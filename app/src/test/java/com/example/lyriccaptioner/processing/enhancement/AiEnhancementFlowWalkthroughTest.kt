package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.WhisperSegment
import com.example.lyriccaptioner.processing.WhisperSegmentConverter
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end walkthrough of the four AI-enhancement flows using Whisper output as input.
 * Captures every model call's full request body (system prompt + user payload), the parsed
 * intermediate outputs and the final validated batch, then persists the walkthrough to
 * test-artifacts/ai-enhancement/flow-walkthrough.txt.
 *
 * DeepSeek HTTP calls are replayed deterministically (offline) so the walkthrough runs
 * regardless of the .env key; the captured request bodies are byte-identical to what the
 * provider would send to the real endpoint.
 */
class AiEnhancementFlowWalkthroughTest {

    @Test
    fun walkthroughCapturesEveryModelPromptInputAndOutput() = runBlocking {
        val report = StringBuilder()
        val section = { title: String ->
            report.appendLine()
            report.appendLine("================================================================")
            report.appendLine(title)
            report.appendLine("================================================================")
        }

        // ---- Whisper output as pipeline input (simulated ASR with recognition noise) ----
        val whisperSegments = listOf(
            WhisperSegment(0L, 4_000L, "When I find myself in times of trouble", 0.88f),
            WhisperSegment(4_000L, 8_000L, "Mother Mary comes for me", 0.74f),
            WhisperSegment(8_000L, 12_000L, "Speaking words of wisdom let it be", 0.81f),
            WhisperSegment(12_000L, 16_000L, "And in my hour of darkness", 0.90f),
            WhisperSegment(16_000L, 20_000L, "She is standing right in front of me", 0.85f),
            WhisperSegment(20_000L, 24_000L, "Speaking words of wisdom let it be", 0.80f),
        )
        val rawCaptions: List<CaptionCue> = WhisperSegmentConverter.toCaptions(whisperSegments)

        section("STEP 0  Whisper output (pipeline input)")
        report.appendLine("whisper -> WhisperSegmentConverter.toCaptions -> ${rawCaptions.size} cues")
        rawCaptions.forEach { cue ->
            report.appendLine("   ${cue.id} [${cue.startMs}..${cue.endMs}] conf=${cue.confidence} :: ${cue.english}")
        }

        val request = CaptionEnhancementRequestMapper().map(
            jobId = "walkthrough-1",
            captions = rawCaptions,
            mediaDurationMs = 24_000L,
        )

        val connections = ArrayDeque<WalkthroughConnection>().apply {
            add(WalkthroughConnection(200, envelope("""{"candidates":[{"title":"Let It Be","artist":"The Beatles"}]}""")))
            add(WalkthroughConnection(200, enhancementEnvelope(request)))
        }
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val usedConnections = mutableListOf<WalkthroughConnection>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = WalkthroughByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> =
                    listOf(SongLyricsCandidate("lrclib:let-it-be", identity.title, identity.artist, verifiedLyrics()))
            },
            connectionFactory = { connections.removeFirst().also(usedConnections::add) },
            onDiagnosticStage = stages::add,
        )

        val response = provider.enhance(request)

        // ---- Flow 1 captured call ----
        section("FLOW 1  song identification (DeepSeek call #1)")
        report.appendLine("stage markers: ${stages.take(2)}")
        report.appendLine("--- request body (prompt + input) ---")
        report.appendLine(usedConnections[0].writtenBody())
        report.appendLine("--- raw response ---")
        report.appendLine(usedConnections[0].rawResponse())

        // ---- Flow 2 search + verification ----
        section("FLOW 2  lyrics search + local DP verification")
        val verified = requireNotNull(
            SongLyricsCandidateVerifier().verify(
                request.cues,
                SongLyricsCandidate("lrclib:let-it-be", "Let It Be", "The Beatles", verifiedLyrics()),
            ),
        )
        report.appendLine("search tool result: SongLyricsCandidate(lrclib:let-it-be, Let It Be, The Beatles, ${verifiedLyrics().length} chars)")
        report.appendLine("verifier metrics: $verified")
        report.appendLine("cue -> canonical english alignment:")
        verified.cueCanonicalEnglish.forEach { (cueId, canonical) ->
            report.appendLine("   $cueId :: $canonical")
        }

        // ---- Flow 3 captured call ----
        section("FLOW 3  whole-song bilingual generation (DeepSeek call #2)")
        report.appendLine("stage markers: ${stages.drop(2)}")
        report.appendLine("--- request body (prompt + input incl. verified lyrics) ---")
        report.appendLine(usedConnections[1].writtenBody())
        report.appendLine("--- raw response ---")
        report.appendLine(usedConnections[1].rawResponse())
        report.appendLine("parsed response songMatch=${response.songMatch}")

        // ---- Flow 4 validation + apply ----
        section("FLOW 4  local batch validation + atomic apply")
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        report.appendLine("validation: state=${outcome.state} source=${outcome.source} processingVersion=${outcome.processingVersion}")
        report.appendLine("final captions:")
        outcome.captions.forEach { cue ->
            report.appendLine("   ${cue.id} :: EN=${cue.english} :: ZH=${cue.chinese}")
        }

        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "flow-walkthrough.txt")
        artifact.writeText(report.toString(), Charsets.UTF_8)
        println("walkthrough saved to ${artifact.absolutePath}")

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

        // Codified captured outputs of this walkthrough run (saved test case).
        assertTrue(usedConnections[0].writtenBody().contains("When I find myself in times of trouble"))
        assertTrue(usedConnections[1].writtenBody().contains("verified_complete_lyrics"))
        assertEquals("Let It Be", verified.candidate.title)
        assertEquals(6, verified.metrics.matchedCueCount)
        assertTrue(verified.metrics.confidence > 0.82f)
        assertEquals("mother mary comes to me", verified.cueCanonicalEnglish["whisper-1-4000"])
        assertEquals(SongMatchStatus.CONFIRMED, response.songMatch?.status)
        assertEquals("lrclib:let-it-be", response.songMatch?.source)

        val expectedFinal = listOf(
            "When I find myself in times of trouble" to "当我深陷困境之时",
            "Mother Mary comes to me" to "圣母玛利亚来到我身旁",
            "Speaking words of wisdom, let it be" to "轻声说着智慧的话语，顺其自然",
            "And in my hour of darkness" to "在我黑暗的时刻",
            "She is standing right in front of me" to "她就伫立在我的正前方",
            "Speaking words of wisdom, let it be" to "轻声说着智慧的话语，顺其自然",
        )
        outcome.captions.forEachIndexed { index, cue ->
            assertEquals(expectedFinal[index].first, cue.english)
            assertEquals(expectedFinal[index].second, cue.chinese)
        }

        // Duplicate chorus lines must produce identical Chinese (captured behavior).
        assertEquals(outcome.captions[2].chinese, outcome.captions[5].chinese)
    }

    private fun verifiedLyrics() = listOf(
        "When I find myself in times of trouble",
        "Mother Mary comes to me",
        "Speaking words of wisdom, let it be",
        "And in my hour of darkness",
        "She is standing right in front of me",
        "Speaking words of wisdom, let it be",
    ).joinToString("\n")

    private fun enhancementEnvelope(request: CaptionEnhancementRequest): String {
        val corrected = listOf(
            "When I find myself in times of trouble" to "当我深陷困境之时",
            "Mother Mary comes to me" to "圣母玛利亚来到我身旁",
            "Speaking words of wisdom, let it be" to "轻声说着智慧的话语，顺其自然",
            "And in my hour of darkness" to "在我黑暗的时刻",
            "She is standing right in front of me" to "她就伫立在我的正前方",
            "Speaking words of wisdom, let it be" to "轻声说着智慧的话语，顺其自然",
        )
        return envelope(
            encodeJson(
                mapOf(
                    "schema_version" to request.schemaVersion,
                    "job_id" to request.jobId,
                    "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
                    "cues" to request.cues.mapIndexed { index, cue ->
                        mapOf(
                            "id" to cue.id,
                            "start_ms" to cue.startMs,
                            "end_ms" to cue.endMs,
                            "corrected_english" to corrected[index].first,
                            "chinese" to corrected[index].second,
                        )
                    },
                ),
            ),
        )
    }

    private fun envelope(content: String): String = encodeJson(
        mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
    )

    private class WalkthroughConnection(
        private val status: Int,
        private val response: String,
    ) : HttpURLConnection(URL(DeepSeekCaptionEnhancementProvider.ENDPOINT)) {
        private val output = ByteArrayOutputStream()
        private var cachedResponse: String? = null

        fun writtenBody(): String = output.toString(Charsets.UTF_8.name())

        fun rawResponse(): String = cachedResponse ?: response

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream {
            cachedResponse = response
            return ByteArrayInputStream(response.toByteArray())
        }
    }

    private class WalkthroughByokManager : DeepSeekByokManager {
        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "sk-wal***")
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-walkthrough-sentinel")
    }
}
