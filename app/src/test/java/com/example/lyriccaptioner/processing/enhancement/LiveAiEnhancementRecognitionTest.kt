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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live recognition run for the four isolated AI-enhancement flows. Reads DEEPSEEK_API_KEY
 * from the project .env (via EnhancementTestEnv), executes the complete pipeline against the
 * real DeepSeek endpoint and LRCLIB, records every stage marker plus each stage's generated
 * request/response, prints the trace and persists it under test-artifacts/ai-enhancement so
 * it can be codified as an offline test sample.
 *
 * Skips automatically when the key is not configured.
 */
class LiveAiEnhancementRecognitionTest {

    @Test
    fun liveRecognitionRunsAllFourFlowsAndRecordsStageTrace() = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping live recognition", EnhancementTestEnv.isConfigured)

        val request = recognitionRequest()
        val rawCaptions = request.cues.map { cue ->
            CaptionCue(
                id = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                english = cue.rawEnglish,
                chinese = "",
                confidence = cue.confidence ?: 0f,
            )
        }

        val trace = StringBuilder()
        val startedAt = System.currentTimeMillis()
        val stage = { marker: DeepSeekEnhancementStage ->
            val line = "[${System.currentTimeMillis() - startedAt}ms] STAGE $marker"
            trace.appendLine(line)
            println(line)
        }
        val connections = mutableListOf<RecordingHttpConnection>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = EnvFileDeepSeekByokManager(),
            connectionFactory = { url ->
                RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
            },
            onDiagnosticStage = stage,
        )

        trace.appendLine("== Live AI-enhancement recognition trace ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        trace.appendLine("== endpoint=${DeepSeekCaptionEnhancementProvider.ENDPOINT} model=${DeepSeekCaptionEnhancementProvider.MODEL}")
        trace.appendLine("== input cues: ${request.cues.size}, mediaDurationMs=${request.mediaDurationMs}")
        request.cues.forEach { cue ->
            trace.appendLine("   ${cue.id} [${cue.startMs}..${cue.endMs}] conf=${cue.confidence} :: ${cue.rawEnglish}")
        }

        val response = try {
            provider.enhance(request)
        } catch (error: CaptionEnhancementProviderException) {
            connections.forEachIndexed { index, connection ->
                println("DIAG call #${index + 1} status=${connection.statusCode}")
                println("DIAG call #${index + 1} body=${connection.rawResponse()}")
                trace.appendLine("== DeepSeek call #${index + 1} FAILED status=${connection.statusCode}")
                trace.appendLine(connection.rawResponse())
            }
            writeTraceArtifact(trace.toString())
            if (error.kind == CaptionEnhancementErrorKind.AUTHENTICATION) {
                // Reached the endpoint but the key was rejected: report clearly and skip
                // instead of failing the suite until a valid key is placed in .env.
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
        val flow4 = { marker: CaptionEnhancementState ->
            val line = "[${System.currentTimeMillis() - startedAt}ms] FLOW4 $marker"
            trace.appendLine(line)
            println(line)
        }
        flow4(CaptionEnhancementState.CLOUD_VALIDATING)
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        flow4(CaptionEnhancementState.CLOUD_APPLIED)
        trace.appendLine("== validation: state=${outcome.state} source=${outcome.source} applied=${outcome.captions.size} cues")
        outcome.captions.forEach { cue ->
            trace.appendLine("   ${cue.id} :: EN=${cue.english} :: ZH=${cue.chinese}")
        }

        val artifact = writeTraceArtifact(trace.toString())

        println(trace.toString())
        println("trace saved to ${artifact.absolutePath}")

        // The run is only meaningful when the whole pipeline completed.
        assertEquals(request.cues.map { it.id }, response.cues.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertTrue(
            "stage trace must contain the four flows",
            trace.contains("STAGE CANDIDATE_REQUEST") &&
                trace.contains("STAGE LYRICS_SEARCH") &&
                trace.contains("STAGE WHOLE_SONG_REQUEST") &&
                trace.contains("FLOW4 CLOUD_APPLIED"),
        )
    }

    private fun writeTraceArtifact(content: String): File {
        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "live-recognition-trace.txt")
        artifact.writeText(content, Charsets.UTF_8)
        return artifact
    }

    private fun recognitionRequest(): CaptionEnhancementRequest {
        val lines = listOf(
            "When I find myself in times of trouble",
            "Mother Mary comes to me",
            "Speaking words of wisdom, let it be",
            "And in my hour of darkness",
            "She is standing right in front of me",
            "Speaking words of wisdom, let it be",
        )
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
