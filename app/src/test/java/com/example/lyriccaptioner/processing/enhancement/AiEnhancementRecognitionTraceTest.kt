package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.LocalTranslator
import com.example.lyriccaptioner.processing.TranslationModule
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline test sample codified from a captured live recognition run
 * (see LiveAiEnhancementRecognitionTest and test-artifacts/ai-enhancement).
 *
 * Captured trace (2026-08-27, key ****34bc):
 *   [26ms] STAGE CANDIDATE_REQUEST
 *   DIAG call #1 status=401
 *   DIAG call #1 body={"error":{"message":"Authentication Fails, ...","type":"authentication_error"}}
 *
 * Case A replays that exact captured 401 rejection; case B replays the same recognition input
 * through the complete four-flow success chain so the whole pipeline stays covered offline.
 */
class AiEnhancementRecognitionTraceTest {

    @Test
    fun capturedAuthenticationFailureStopsAtFlowOneWithoutFallback() = runBlocking {
        val captions = recognitionCaptions()
        val capturedErrorBody =
            """{"error":{"message":"Authentication Fails, Your api key: ****34bc is invalid","type":"authentication_error","param":null,"code":"invalid_request_error"}}"""
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = SentinelByokManager(),
            connectionFactory = { ArrayDeque(listOf(TraceConnection(401, capturedErrorBody))).removeFirst() },
            onDiagnosticStage = stages::add,
        )

        // Provider level: the captured 401 maps to AUTHENTICATION and halts at flow 1.
        val error = assertThrows(CaptionEnhancementProviderException::class.java) {
            runBlocking { provider.enhance(recognitionRequest()) }
        }
        assertEquals(CaptionEnhancementErrorKind.AUTHENTICATION, error.kind)
        assertEquals(listOf(DeepSeekEnhancementStage.CANDIDATE_REQUEST), stages)

        // Coordinator level: authentication failures never downgrade to the local fallback.
        val translator = RecordingLocalTranslator()
        val states = mutableListOf<CaptionEnhancementState>()
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )
        val coordinatorError = assertThrows(CaptionEnhancementException::class.java) {
            runBlocking { coordinator.enhance("live-recognition-1", captions, states::add, 24_000L) }
        }
        assertEquals(CaptionEnhancementErrorKind.AUTHENTICATION, coordinatorError.kind)
        assertEquals(0, translator.translateCalls)
        assertTrue(states.last() != CaptionEnhancementState.LOCAL_FALLBACK_APPLIED)
    }

    @Test
    fun sameRecognitionInputRunsTheCompleteFourFlowChainOffline() = runBlocking {
        val request = recognitionRequest()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val connections = ArrayDeque<TraceConnection>().apply {
            add(TraceConnection(200, envelope("""{"candidates":[{"title":"Let It Be","artist":"The Beatles"}]}""")))
            add(TraceConnection(200, enhancementEnvelope(request)))
        }
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = SentinelByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> =
                    listOf(SongLyricsCandidate("lrclib:let-it-be", identity.title, identity.artist, lyrics()))
            },
            connectionFactory = { connections.removeFirst() },
            onDiagnosticStage = stages::add,
        )

        val response = provider.enhance(request)

        // Flows 1-3 stage trace of the captured recognition.
        assertEquals(
            listOf(
                DeepSeekEnhancementStage.CANDIDATE_REQUEST,
                DeepSeekEnhancementStage.CANDIDATE_PARSE,
                DeepSeekEnhancementStage.LYRICS_SEARCH,
                DeepSeekEnhancementStage.LYRICS_VERIFY,
                DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED,
                DeepSeekEnhancementStage.WHOLE_SONG_REQUEST,
                DeepSeekEnhancementStage.WHOLE_SONG_PARSE,
            ),
            stages,
        )
        assertEquals(SongMatchStatus.CONFIRMED, response.songMatch?.status)

        // Flow 4: the complete batch passes local validation and applies atomically.
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, recognitionCaptions())
        assertEquals(CaptionEnhancementState.CLOUD_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.CLOUD_AI, outcome.source)
        assertEquals(request.cues.map { it.id }, outcome.captions.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
    }

    private fun recognitionRequest(): CaptionEnhancementRequest {
        val lines = recognitionLines()
        return CaptionEnhancementRequest(
            jobId = "live-recognition-1",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = lines.mapIndexed { index, line ->
                CaptionEnhancementRequestCue(
                    "cue-${index + 1}",
                    index * 4_000L,
                    (index + 1) * 4_000L,
                    line,
                    0.9f,
                )
            },
            mediaDurationMs = 24_000L,
        )
    }

    private fun recognitionCaptions(): List<CaptionCue> = recognitionRequest().cues.map { cue ->
        CaptionCue(
            id = cue.id,
            startMs = cue.startMs,
            endMs = cue.endMs,
            english = cue.rawEnglish,
            chinese = "",
            confidence = cue.confidence ?: 0f,
        )
    }

    private fun recognitionLines() = listOf(
        "When I find myself in times of trouble",
        "Mother Mary comes to me",
        "Speaking words of wisdom, let it be",
        "And in my hour of darkness",
        "She is standing right in front of me",
        "Speaking words of wisdom, let it be",
    )

    private fun lyrics() = recognitionLines().joinToString("\n")

    private fun enhancementEnvelope(request: CaptionEnhancementRequest): String = envelope(
        encodeJson(
            mapOf(
                "schema_version" to request.schemaVersion,
                "job_id" to request.jobId,
                "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
                "cues" to request.cues.map { cue ->
                    mapOf(
                        "id" to cue.id,
                        "start_ms" to cue.startMs,
                        "end_ms" to cue.endMs,
                        "corrected_english" to cue.rawEnglish,
                        "chinese" to "中文歌词 ${cue.id}",
                    )
                },
            ),
        ),
    )

    private fun envelope(content: String): String = encodeJson(
        mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
    )

    private class TraceConnection(
        private val status: Int,
        private val response: String,
    ) : HttpURLConnection(URL(DeepSeekCaptionEnhancementProvider.ENDPOINT)) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = ByteArrayInputStream(response.toByteArray())
    }

    private class RecordingLocalTranslator : LocalTranslator {
        var translateCalls = 0

        override suspend fun prepareBatch() = Unit

        override suspend fun translateEnglishToChinese(text: String): String {
            translateCalls += 1
            return "本地译文"
        }
    }

    /** Fixed sentinel key so the offline trace replay never depends on the local .env. */
    private class SentinelByokManager : DeepSeekByokManager {
        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "sk-sen***")
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-trace-sentinel")
    }
}
