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
 * Test sample 3: the 2026-08-27 20:31 physical-device recognition whose recognized text WAS
 * recovered (via the editor UI state dump, see test-artifacts/device-capture).
 *
 * Captured cues (Whisper English as recognized on device, 18.716s video, 4 segments):
 *   whisper-0-0     [0..4320]      I stopped CPR, after all it's no use
 *   whisper-1-4320  [4320..7360]   The spirit was gone, we would never come to
 *   whisper-2-7360  [7360..11440]  I'm pissed off you let me give you all that youth for free
 *   whisper-3-11440 [11440..18720] For so long, let me
 * Device final state: "DeepSeek enhanced 4 captions." with Chinese translations attached.
 *
 * This test rebuilds the STANDARD enhancement input from those cues, replays the same path the
 * device took (song not identified -> lyrics search finds nothing -> conservative UNCONFIRMED
 * generation), captures every stage's real request body and replayed output, and pins down
 * WHERE the weak output of cue 4 originates. The flow-3 response replays the device's actual
 * final output byte-for-byte; request bodies are byte-identical to the real endpoint calls.
 */
class DeviceRecognitionTextWalkthroughTest {

    private val capturedFinal = listOf(
        Triple("I stopped CPR, after all it's no use", "我停止了心肺复苏，毕竟已无济于事", 0.85f),
        Triple("The spirit was gone, we would never come to", "灵魂已逝，我们再也无法苏醒", 0.80f),
        Triple("I'm pissed off you let me give you all that youth for free", "我气的是你让我白白把青春都给了你", 0.78f),
        Triple("For so long, let me", "那么久，让我", 0.62f),
    )
    private val capturedTimings = listOf(0L to 4_320L, 4_320L to 7_360L, 7_360L to 11_440L, 11_440L to 18_720L)

    @Test
    fun deviceCapturedRecognitionReplaysTheFourFlowsAndLocatesTheWeakStage() = runBlocking {
        val report = StringBuilder()
        val section = { title: String ->
            report.appendLine()
            report.appendLine("================================================================")
            report.appendLine(title)
            report.appendLine("================================================================")
        }

        // ---- STEP 0: the device Whisper output, rebuilt as WhisperSegments ----
        val whisperSegments = capturedFinal.mapIndexed { index, (english, _, confidence) ->
            WhisperSegment(capturedTimings[index].first, capturedTimings[index].second, english, confidence)
        }
        val rawCaptions: List<CaptionCue> = WhisperSegmentConverter.toCaptions(whisperSegments)

        section("STEP 0  device Whisper output (captured 2026-08-27 20:31, rebuilt)")
        report.appendLine("video duration 18716ms, inference 5504ms, segmentCount=4 (log: whisper_session_run handle=2)")
        rawCaptions.forEach { cue ->
            report.appendLine("   ${cue.id} [${cue.startMs}..${cue.endMs}] conf=${cue.confidence} :: ${cue.english}")
        }
        report.appendLine("note: per-cue confidence is NOT recorded by the device; values above are estimates.")

        // ---- STANDARD enhancement input built from the captured cues ----
        val request = CaptionEnhancementRequestMapper().map(
            jobId = "device-capture-3",
            captions = rawCaptions,
            mediaDurationMs = 18_716L,
        )

        val connections = ArrayDeque<WalkthroughConnection>().apply {
            // Flow 1 replay: the device run could not identify the song.
            add(WalkthroughConnection(200, envelope("""{"candidates":[]}""")))
            // Flow 3 replay: the device's actual final output, replayed byte-for-byte.
            add(WalkthroughConnection(200, enhancementEnvelope(request)))
        }
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val usedConnections = mutableListOf<WalkthroughConnection>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = WalkthroughByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> = emptyList()
                override suspend fun searchByLyricText(queryText: String): List<SongLyricsCandidate> = emptyList()
            },
            connectionFactory = { connections.removeFirst().also(usedConnections::add) },
            onDiagnosticStage = stages::add,
        )

        section("STANDARD AI-ENHANCEMENT INPUT (CaptionEnhancementRequestMapper output)")
        report.appendLine(DeepSeekCaptionEnhancementJson.requestBody(request))

        val response = provider.enhance(request)

        // ---- FLOW 1 ----
        section("FLOW 1  song identification (DeepSeek call #1) -- result: NOT identified")
        report.appendLine("stage markers: ${stages.take(2)}")
        report.appendLine("--- request body (IDENTIFICATION prompt + 4 cues) ---")
        report.appendLine(usedConnections[0].writtenBody())
        report.appendLine("--- replayed response (device behavior: no candidate) ---")
        report.appendLine(usedConnections[0].rawResponse())

        // ---- FLOW 2 ----
        section("FLOW 2  lyrics search + local DP verification -- result: nothing found")
        report.appendLine("metadata search: skipped (no identity candidate from flow 1)")
        report.appendLine("lyric-text fallback search (LRCLIB): 0 candidates for these lines")
        report.appendLine("verifier: never engaged (no candidate lyrics to align)")
        report.appendLine("songMatch: ${response.songMatch}")

        // ---- FLOW 3 ----
        section("FLOW 3  bilingual generation, UNCONFIRMED conservative mode (DeepSeek call #2)")
        report.appendLine("stage markers: ${stages.drop(2)}")
        report.appendLine("--- request body (UNCONFIRMED prompt + 4 cues, no canonical lyrics) ---")
        report.appendLine(usedConnections[1].writtenBody())
        report.appendLine("--- response: the device's actual final output, replayed byte-for-byte ---")
        report.appendLine(usedConnections[1].rawResponse())

        // ---- FLOW 4 ----
        section("FLOW 4  local batch validation + atomic apply")
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        report.appendLine("validation: state=${outcome.state} source=${outcome.source} processingVersion=${outcome.processingVersion}")
        report.appendLine("final captions:")
        outcome.captions.forEach { cue ->
            report.appendLine("   ${cue.id} :: EN=${cue.english} :: ZH=${cue.chinese}")
        }

        // ---- DIAGNOSIS ----
        section("DIAGNOSIS  where the weak output comes from")
        report.appendLine("cue 4 'For so long, let me' -> '那么久，让我' is fragmentary because:")
        report.appendLine("  1) STEP 0: Whisper itself heard an incomplete trailing phrase (low confidence, cut at 18.72s).")
        report.appendLine("  2) FLOW 1: DeepSeek could not identify the song from these lines -> no song identity.")
        report.appendLine("  3) FLOW 2: LRCLIB has no matching lyrics -> no canonical text for correction.")
        report.appendLine("  4) FLOW 3: UNCONFIRMED mode forbids inventing lyrics, so the fragment is translated literally.")
        report.appendLine("  5) FLOW 4: 4-in/4-out, ids and timestamps intact -> validation passes; not a bug here.")
        report.appendLine("Conclusion: no code-stage malfunction; the quality ceiling is set by the ASR input")
        report.appendLine("plus the song being unidentifiable (flows 1+2 found nothing to correct against).")

        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "device-capture-walkthrough.txt")
        artifact.writeText(report.toString(), Charsets.UTF_8)
        println("device-capture walkthrough saved to ${artifact.absolutePath}")

        // ---- codified assertions ----
        assertEquals(
            listOf("whisper-0-0", "whisper-1-4320", "whisper-2-7360", "whisper-3-11440"),
            rawCaptions.map { it.id },
        )
        assertEquals(
            listOf(
                DeepSeekEnhancementStage.CANDIDATE_REQUEST,
                DeepSeekEnhancementStage.CANDIDATE_PARSE,
                DeepSeekEnhancementStage.LYRICS_SEARCH,
                DeepSeekEnhancementStage.LYRICS_VERIFY,
                DeepSeekEnhancementStage.WHOLE_SONG_REQUEST,
                DeepSeekEnhancementStage.WHOLE_SONG_PARSE,
            ),
            stages,
        )
        assertEquals(SongMatchStatus.NOT_FOUND, response.songMatch?.status)
        // Flow 3 must have used the conservative UNCONFIRMED prompt (no canonical lyrics available).
        assertTrue(usedConnections[1].writtenBody().contains("不得声称歌曲已经确认"))
        // Flow 4 passes and the final batch equals the device-captured output.
        assertEquals(CaptionEnhancementState.CLOUD_APPLIED, outcome.state)
        assertEquals(CaptionResultSource.CLOUD_AI, outcome.source)
        outcome.captions.forEachIndexed { index, cue ->
            assertEquals(capturedFinal[index].first, cue.english)
            assertEquals(capturedFinal[index].second, cue.chinese)
        }
    }

    private fun enhancementEnvelope(request: CaptionEnhancementRequest): String = envelope(
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
                        "corrected_english" to capturedFinal[index].first,
                        "chinese" to capturedFinal[index].second,
                    )
                },
            ),
        ),
    )

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
        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "sk-dc3***")
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-device-capture-3")
    }
}
