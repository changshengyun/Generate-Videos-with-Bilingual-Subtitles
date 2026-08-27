package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live recognition scenario runs derived from LiveAiEnhancementRecognitionTest. Every scenario
 * starts from the captured recognition input (the six recognized "Let It Be" lines) and mutates
 * it into one plausible Whisper misrecognition shape, then runs it through the real DeepSeek
 * endpoint while recording the complete stage trace plus every request/response body under
 * test-artifacts/ai-enhancement for review.
 *
 * Covered recognition error situations:
 *  - Scenario A (wrong words 错字): homophone-style typos plus low confidence on most cues.
 *    The pipeline must still complete with a 1:1 bilingual batch. (DeepSeek calls: 2.)
 *  - Scenario B (wrong segmentation 错句): one cue swallows the next lyric line and another
 *    keeps only the tail of its line. The contract requires the two-line cue to come back as
 *    two English lines and two aligned Chinese lines. (DeepSeek calls: 2.)
 *  - Scenario C (wrong order 顺序错乱): the six lines heard in the wrong order against
 *    chronological timelines. The local DP verifier must never mark such a batch CONFIRMED,
 *    yet the pipeline still completes. (DeepSeek calls: up to 2.)
 *
 * Skips automatically when the key is not configured or the configured key is rejected (401).
 */
class LiveAiEnhancementRecognitionScenariosTest {

    @Test
    fun liveWrongWordsInputStillProducesBilingualBatch() = runBlocking {
        val cues = listOf(
            ScenarioCue("cue-1", 0L, 4_000L, "when i fine my self in time a troble", 0.42f),
            ScenarioCue("cue-2", 4_000L, 8_000L, "mother marry cums to mi", 0.38f),
            ScenarioCue("cue-3", 8_000L, 12_000L, "speeking words of wisdom let it be", 0.55f),
            ScenarioCue("cue-4", 12_000L, 16_000L, "and in my hour a darkness", 0.45f),
            ScenarioCue("cue-5", 16_000L, 20_000L, "she is stending right in fraunt of me", 0.47f),
            ScenarioCue("cue-6", 20_000L, 24_000L, "speaking words a wisdom let it bee", 0.52f),
        )
        val result = runScenario("wrong-words", cues, 24_000L)
        assertTrue(
            "wrong-word recognition still runs the song identification flow",
            result.stages.contains(DeepSeekEnhancementStage.CANDIDATE_REQUEST),
        )
        assertTrue(
            "wrong-word recognition completes the whole-song enhancement",
            result.stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_PARSE),
        )
    }

    @Test
    fun liveWrongSegmentationKeepsTheOneToOneContract() = runBlocking {
        val cues = listOf(
            ScenarioCue("cue-1", 0L, 4_000L, "When I find myself in times of trouble", 0.85f),
            ScenarioCue("cue-2", 4_000L, 8_000L, "Mother Mary comes to me", 0.85f),
            ScenarioCue(
                "cue-3",
                8_000L,
                12_000L,
                "Speaking words of wisdom, let it be and in my hour of darkness",
                0.55f,
            ),
            ScenarioCue("cue-4", 12_000L, 16_000L, "standing right in front of me", 0.5f),
            ScenarioCue("cue-5", 16_000L, 20_000L, "Speaking words of wisdom, let it be", 0.85f),
        )
        val result = runScenario("wrong-segmentation", cues, 20_000L)
        assertEquals(
            "the merged cue must come back as a single cue, never split",
            result.response.cues.map { it.id },
            cues.map { it.id },
        )
        assertTrue(
            "wrong segmentation still completes the whole-song enhancement",
            result.stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_PARSE),
        )
    }

    @Test
    fun liveMisorderedLinesAreNeverMarkedConfirmed() = runBlocking {
        val lines = listOf(
            "And in my hour of darkness",
            "When I find myself in times of trouble",
            "Speaking words of wisdom, let it be",
            "Mother Mary comes to me",
            "She is standing right in front of me",
            "Speaking words of wisdom, let it be",
        )
        val cues = lines.mapIndexed { index, line ->
            ScenarioCue("cue-${index + 1}", index * 4_000L, (index + 1) * 4_000L, line, 0.6f)
        }
        val result = runScenario("misordered-lines", cues, 24_000L)
        assertNotEquals(
            "only the local DP verifier may confirm a song; misordered lines must stay unconfirmed",
            SongMatchStatus.CONFIRMED,
            result.response.songMatch?.status,
        )
        assertTrue(result.outcome.captions.all { it.chinese.isNotBlank() })
    }

    private data class ScenarioCue(
        val id: String,
        val startMs: Long,
        val endMs: Long,
        val english: String,
        val confidence: Float,
    )

    private data class ScenarioResult(
        val response: CaptionEnhancementResponse,
        val outcome: CaptionEnhancementOutcome,
        val stages: List<DeepSeekEnhancementStage>,
    )

    private fun runScenario(
        scenarioId: String,
        cues: List<ScenarioCue>,
        mediaDurationMs: Long,
    ): ScenarioResult = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping live scenario", EnhancementTestEnv.isConfigured)

        val request = CaptionEnhancementRequest(
            jobId = "live-scenario-$scenarioId",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = cues.map { cue ->
                CaptionEnhancementRequestCue(cue.id, cue.startMs, cue.endMs, cue.english, cue.confidence)
            },
            mediaDurationMs = mediaDurationMs,
        )
        val rawCaptions = cues.map { cue ->
            CaptionCue(
                id = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                english = cue.english,
                chinese = "",
                confidence = cue.confidence,
            )
        }

        val trace = StringBuilder()
        val startedAt = System.currentTimeMillis()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val connections = mutableListOf<RecordingHttpConnection>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = EnvFileDeepSeekByokManager(),
            connectionFactory = { url ->
                RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
            },
            onDiagnosticStage = { marker ->
                stages.add(marker)
                val line = "[${System.currentTimeMillis() - startedAt}ms] STAGE $marker"
                trace.appendLine(line)
                println(line)
            },
        )

        trace.appendLine("== Live scenario '$scenarioId' trace ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        trace.appendLine("== endpoint=${DeepSeekCaptionEnhancementProvider.ENDPOINT} model=${DeepSeekCaptionEnhancementProvider.MODEL}")
        trace.appendLine("== input cues: ${request.cues.size}, mediaDurationMs=$mediaDurationMs")
        request.cues.forEach { cue ->
            trace.appendLine("   ${cue.id} [${cue.startMs}..${cue.endMs}] conf=${cue.confidence} :: ${cue.rawEnglish}")
        }

        val response = try {
            provider.enhance(request)
        } catch (error: CaptionEnhancementProviderException) {
            connections.forEachIndexed { index, connection ->
                trace.appendLine("== DeepSeek call #${index + 1} FAILED status=${connection.statusCode}")
                trace.appendLine(connection.rawResponse())
            }
            writeTraceArtifact(scenarioId, trace.toString())
            if (error.kind == CaptionEnhancementErrorKind.AUTHENTICATION) {
                println("DEEPSEEK_API_KEY in .env was rejected (401). Update the key and rerun.")
                assumeTrue("DeepSeek rejected the configured API key", false)
            }
            throw error
        }

        connections.forEachIndexed { index, connection ->
            trace.appendLine("== DeepSeek call #${index + 1} request body (${connection.writtenBody().length} chars) ==")
            trace.appendLine(connection.writtenBody())
            trace.appendLine("== DeepSeek call #${index + 1} raw response (${connection.rawResponse().length} chars) ==")
            trace.appendLine(connection.rawResponse())
        }

        trace.appendLine("== parsed response: songMatch=${response.songMatch}")
        response.cues.forEach { cue ->
            trace.appendLine("   ${cue.id} [${cue.startMs}..${cue.endMs}] :: EN=${cue.correctedEnglish} :: ZH=${cue.chinese}")
        }

        // Flow 4: local batch validation of the provider response.
        trace.appendLine("[${System.currentTimeMillis() - startedAt}ms] FLOW4 ${CaptionEnhancementState.CLOUD_VALIDATING}")
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        trace.appendLine("[${System.currentTimeMillis() - startedAt}ms] FLOW4 ${CaptionEnhancementState.CLOUD_APPLIED}")
        trace.appendLine("== validation: state=${outcome.state} source=${outcome.source} applied=${outcome.captions.size} cues")
        outcome.captions.forEach { cue ->
            trace.appendLine("   ${cue.id} :: EN=${cue.english} :: ZH=${cue.chinese}")
        }

        val artifact = writeTraceArtifact(scenarioId, trace.toString())
        println(trace.toString())
        println("trace saved to ${artifact.absolutePath}")

        // Scenario-independent hard contract: every run completes as an atomic 1:1 batch.
        assertEquals(request.cues.map { it.id }, response.cues.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertTrue(
            "stage trace must reach the whole-song flow",
            stages.contains(DeepSeekEnhancementStage.LYRICS_SEARCH) &&
                stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_REQUEST),
        )

        ScenarioResult(response, outcome, stages.toList())
    }

    private fun writeTraceArtifact(scenarioId: String, content: String): File {
        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "live-scenario-$scenarioId-trace.txt")
        artifact.writeText(content, Charsets.UTF_8)
        return artifact
    }

    /** Wraps the real connection, capturing the written request body and raw response. */
    private class RecordingHttpConnection(private val delegate: HttpURLConnection) : HttpURLConnection(delegate.url) {
        private val captured = ByteArrayOutputStream()
        private var responseBody: ByteArray? = null
        private var flushed = false
        var statusCode: Int = -1
            private set

        fun writtenBody(): String = captured.toString(Charsets.UTF_8.name())

        fun rawResponse(): String = responseBody?.toString(Charsets.UTF_8) ?: ""

        private fun flushBody() {
            if (!flushed) {
                flushed = true
                delegate.outputStream.use { it.write(captured.toByteArray()) }
            }
        }

        override fun setRequestMethod(method: String) = delegate.setRequestMethod(method)
        override fun setConnectTimeout(timeout: Int) { delegate.connectTimeout = timeout }
        override fun setReadTimeout(timeout: Int) { delegate.readTimeout = timeout }
        override fun setDoOutput(dooutput: Boolean) = delegate.setDoOutput(dooutput)
        override fun setInstanceFollowRedirects(followRedirects: Boolean) {
            delegate.instanceFollowRedirects = followRedirects
        }
        override fun setUseCaches(usecaches: Boolean) { delegate.useCaches = usecaches }
        override fun setRequestProperty(key: String, value: String) = delegate.setRequestProperty(key, value)
        override fun connect() = delegate.connect()
        override fun disconnect() = delegate.disconnect()
        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = captured

        override fun getResponseCode(): Int {
            flushBody()
            return delegate.responseCode.also { statusCode = it }.also { status ->
                if (status !in 200..299) {
                    responseBody = try {
                        delegate.errorStream?.use { stream -> stream.readBytes() }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        override fun getInputStream(): InputStream {
            flushBody()
            val bytes = delegate.inputStream.use { it.readBytes() }
            responseBody = bytes
            return ByteArrayInputStream(bytes)
        }

        override fun getErrorStream(): InputStream? {
            flushBody()
            val stream = delegate.errorStream ?: return null
            val bytes = stream.use { it.readBytes() }
            responseBody = bytes
            return ByteArrayInputStream(bytes)
        }
    }
}
