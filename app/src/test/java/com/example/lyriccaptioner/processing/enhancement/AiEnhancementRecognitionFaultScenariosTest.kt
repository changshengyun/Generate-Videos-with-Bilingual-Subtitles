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
import java.net.SocketTimeoutException
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline recognition fault scenarios derived from LiveAiEnhancementRecognitionTest and
 * AiEnhancementRecognitionTraceTest. Every case replays a deterministic transport or model
 * failure through the real provider/coordinator pipeline with injected connections, so none
 * of them touches the network or the local .env.
 *
 * Covered recognition error situations:
 *  - Scenario 1: DeepSeek answers 429 (rate limited) -> RETRYABLE_SERVER -> local translation
 *    fallback; authentication-style hard failures never happen here.
 *  - Scenario 2: the socket read times out -> TIMEOUT -> local translation fallback.
 *  - Scenario 3: HTTP 200 but the body is not JSON -> INVALID_RESPONSE -> local translation
 *    fallback.
 *  - Scenario 4: well-formed JSON that drops one cue -> batch validation rejects the whole
 *    response -> INVALID_RESPONSE -> local translation fallback.
 *  - Scenario 5: the song cannot be identified or verified -> conservative enhancement mode
 *    still completes the batch with songMatch=NOT_FOUND.
 */
class AiEnhancementRecognitionFaultScenariosTest {

    @Test
    fun scenario1RateLimitedServerDowngradesToLocalFallback() = runBlocking {
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val provider = providerWith(ArrayDeque(listOf(scenarioConnection(429, RATE_LIMIT_ERROR_BODY))), stages)
        val translator = RecordingLocalTranslator()
        val states = mutableListOf<CaptionEnhancementState>()
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )

        val outcome = coordinator.enhance("fault-scenario-rate-limit", recognitionCaptions(), states::add, 24_000L)

        assertEquals(listOf(DeepSeekEnhancementStage.CANDIDATE_REQUEST), stages)
        assertEquals(CaptionEnhancementErrorKind.RETRYABLE_SERVER, outcome.errorKind)
        assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
        assertEquals(6, translator.translateCalls)
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertEquals(
            listOf(
                CaptionEnhancementState.RAW_ASR_READY,
                CaptionEnhancementState.CLOUD_PENDING,
                CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
            ),
            states,
        )
    }

    @Test
    fun scenario2ReadTimeoutDowngradesToLocalFallback() = runBlocking {
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = SentinelByokManager(),
            connectionFactory = { TimeoutConnection() },
            onDiagnosticStage = stages::add,
        )
        val translator = RecordingLocalTranslator()
        val states = mutableListOf<CaptionEnhancementState>()
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )

        val outcome = coordinator.enhance("fault-scenario-timeout", recognitionCaptions(), states::add, 24_000L)

        assertEquals(listOf(DeepSeekEnhancementStage.CANDIDATE_REQUEST), stages)
        assertEquals(CaptionEnhancementErrorKind.TIMEOUT, outcome.errorKind)
        assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
        assertEquals(6, translator.translateCalls)
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, states.last())
    }

    @Test
    fun scenario3MalformedResponseBodyDowngradesToLocalFallback() = runBlocking {
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val provider = providerWith(ArrayDeque(listOf(scenarioConnection(200, MALFORMED_BODY))), stages)
        val translator = RecordingLocalTranslator()
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )

        val outcome = coordinator.enhance("fault-scenario-malformed", recognitionCaptions(), {}, 24_000L)

        // The malformed body is discovered while parsing the identification response, so the
        // trace reaches CANDIDATE_PARSE and stops there.
        assertEquals(
            listOf(DeepSeekEnhancementStage.CANDIDATE_REQUEST, DeepSeekEnhancementStage.CANDIDATE_PARSE),
            stages,
        )
        assertEquals(CaptionEnhancementErrorKind.INVALID_RESPONSE, outcome.errorKind)
        assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
        assertEquals(6, translator.translateCalls)
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
    }

    @Test
    fun scenario4ResponseMissingOneCueFailsValidationAndDowngrades() = runBlocking {
        val request = recognitionRequest()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val connections = ArrayDeque<HttpURLConnection>().apply {
            add(scenarioConnection(200, envelope(encodeJson(mapOf("candidates" to listOf<Nothing>())))))
            // The provider parses this envelope successfully; the whole-batch validator must
            // then reject it because one cue is missing.
            add(scenarioConnection(200, enhancementEnvelope(request, request.cues.drop(1))))
        }
        val provider = providerWith(connections, stages)
        val translator = RecordingLocalTranslator()
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )

        val outcome = coordinator.enhance("fault-scenario-missing-cue", recognitionCaptions(), {}, 24_000L)

        assertTrue(stages.contains(DeepSeekEnhancementStage.CANDIDATE_REQUEST))
        assertTrue(stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_PARSE))
        assertFalse(stages.contains(DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED))
        assertEquals(CaptionEnhancementErrorKind.INVALID_RESPONSE, outcome.errorKind)
        assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
        assertEquals(6, translator.translateCalls)
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
    }

    @Test
    fun scenario5UnidentifiedSongStillCompletesThroughConservativeMode() = runBlocking {
        val request = recognitionRequest()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val connections = ArrayDeque<HttpURLConnection>().apply {
            add(
                scenarioConnection(
                    200,
                    envelope(
                        encodeJson(
                            mapOf(
                                "candidates" to listOf(
                                    mapOf("title" to "Let It Be", "artist" to "The Beatles"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            add(scenarioConnection(200, enhancementEnvelope(request, request.cues)))
        }
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = SentinelByokManager(),
            // Metadata lookup finds nothing and the lyric-text fallback has no results either,
            // so no lyrics can be verified for this recognition batch.
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> = emptyList()
            },
            connectionFactory = { connections.removeFirst() },
            onDiagnosticStage = stages::add,
        )

        val response = provider.enhance(request)
        assertEquals(SongMatchStatus.NOT_FOUND, response.songMatch?.status)

        val outcome = CaptionEnhancementResponseValidator().validate(request, response, recognitionCaptions())
        assertEquals(CaptionEnhancementState.CLOUD_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.CLOUD_AI, outcome.source)
        assertEquals(SongMatchStatus.NOT_FOUND, outcome.songMatch?.status)
        assertEquals(request.cues.map { it.id }, outcome.captions.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertTrue(stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_REQUEST))
        assertFalse(stages.contains(DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED))
    }

    private fun providerWith(
        connections: ArrayDeque<HttpURLConnection>,
        stages: MutableList<DeepSeekEnhancementStage>,
    ): DeepSeekCaptionEnhancementProvider = DeepSeekCaptionEnhancementProvider(
        byokManager = SentinelByokManager(),
        connectionFactory = { connections.removeFirst() },
        onDiagnosticStage = stages::add,
    )

    private fun scenarioConnection(status: Int, response: String): HttpURLConnection =
        ScenarioConnection(status, response)

    private fun recognitionRequest(): CaptionEnhancementRequest {
        val lines = recognitionLines()
        return CaptionEnhancementRequest(
            jobId = "fault-scenarios-1",
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

    private fun enhancementEnvelope(
        request: CaptionEnhancementRequest,
        cues: List<CaptionEnhancementRequestCue>,
    ): String = envelope(
        encodeJson(
            mapOf(
                "schema_version" to request.schemaVersion,
                "job_id" to request.jobId,
                "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
                "cues" to cues.map { cue ->
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

    private class ScenarioConnection(
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

    /** The socket dies while waiting for the DeepSeek response body. */
    private class TimeoutConnection : HttpURLConnection(URL(DeepSeekCaptionEnhancementProvider.ENDPOINT)) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = throw SocketTimeoutException("Read timed out")
        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = throw SocketTimeoutException("Read timed out")
    }

    private class RecordingLocalTranslator : LocalTranslator {
        var translateCalls = 0

        override suspend fun prepareBatch() = Unit

        override suspend fun translateEnglishToChinese(text: String): String {
            translateCalls += 1
            return "本地译文"
        }
    }

    /** Fixed sentinel key so the fault scenarios never depend on the local .env. */
    private class SentinelByokManager : DeepSeekByokManager {
        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "sk-sen***")
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-fault-sentinel")
    }

    companion object {
        private const val RATE_LIMIT_ERROR_BODY =
            """{"error":{"message":"Rate limit reached for requests","type":"rate_limit_error","param":null,"code":"rate_limit_exceeded"}}"""

        private const val MALFORMED_BODY = "<html><body>502 Bad Gateway</body></html>"
    }
}
