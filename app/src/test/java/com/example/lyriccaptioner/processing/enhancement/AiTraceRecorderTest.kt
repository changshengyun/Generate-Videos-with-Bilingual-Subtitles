package com.example.lyriccaptioner.processing.enhancement

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiTraceRecorderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun creatingRecorderClearsPreviousProcessTrace() {
        val traceFile = temporaryFolder.newFile(FileAiTraceRecorder.TRACE_FILE_NAME)
        traceFile.writeText("previous-process-secret")

        FileAiTraceRecorder(traceFile)

        assertFalse(traceFile.exists())
    }

    @Test
    fun newSessionReplacesPreviousWorkflowAndKeepsOrderedJsonLines() {
        val traceFile = File(temporaryFolder.root, FileAiTraceRecorder.TRACE_FILE_NAME)
        var now = 1_000L
        val recorder = FileAiTraceRecorder(traceFile, nowMs = { now })
        recorder.beginSession("job-old")
        recorder.record("old_event", mapOf("value" to "old"))

        now = 2_000L
        recorder.beginSession("job-new")
        now = 2_125L
        recorder.record("new_event", mapOf("value" to "new"))

        val lines = traceFile.readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"job_id\":\"job-new\""))
        assertTrue(lines[0].contains("\"sequence\":0"))
        assertTrue(lines[1].contains("\"sequence\":1"))
        assertTrue(lines[1].contains("\"elapsed_ms\":125"))
        assertFalse(traceFile.readText().contains("job-old"))
    }

    @Test
    fun sensitiveFieldsAndCredentialPatternsAreRedacted() {
        val traceFile = File(temporaryFolder.root, FileAiTraceRecorder.TRACE_FILE_NAME)
        val recorder = FileAiTraceRecorder(traceFile)
        recorder.beginSession("job-safe")
        recorder.record(
            "safe_event",
            mapOf(
                "api_key" to "sk-top-secret",
                "request_body" to "private prompt",
                "nested" to mapOf("reasoning_content" to "hidden reasoning"),
                "message" to "Authorization: Bearer secret-token",
            ),
        )

        val trace = traceFile.readText()
        assertFalse(trace.contains("sk-top-secret"))
        assertFalse(trace.contains("private prompt"))
        assertFalse(trace.contains("hidden reasoning"))
        assertFalse(trace.contains("secret-token"))
        assertTrue(trace.contains("[REDACTED]"))
    }

    @Test
    fun fileStopsGrowingAtConfiguredLimit() {
        val traceFile = File(temporaryFolder.root, FileAiTraceRecorder.TRACE_FILE_NAME)
        val recorder = FileAiTraceRecorder(traceFile, maximumBytes = 700L)
        recorder.beginSession("job-bounded")
        repeat(20) { index -> recorder.record("large_event", mapOf("index" to index, "text" to "x".repeat(200))) }

        assertTrue(traceFile.length() <= 700L)
        assertTrue(traceFile.readLines().last().contains("\"event\":\"trace_truncated\""))
    }

    @Test
    fun ioFailureAndThrowingDelegateNeverEscapeIntoProductFlow() {
        val invalidParent = temporaryFolder.newFile("not-a-directory")
        val recorder = FileAiTraceRecorder(File(invalidParent, FileAiTraceRecorder.TRACE_FILE_NAME))
        recorder.beginSession("job-io-failure")
        recorder.record("ignored")

        val safeDelegate = object : AiTraceSink {
            override fun beginSession(jobId: String) = error("begin failure")
            override fun record(event: String, fields: Map<String, Any?>) = error("write failure")
        }.bestEffort()
        safeDelegate.beginSession("job-delegate-failure")
        safeDelegate.record("ignored")
    }

    @Test
    fun logcatSinkUsesStableTagJobSequenceAndTheSameRedactionBoundary() {
        val written = mutableListOf<Pair<String, String>>()
        var now = 3_000L
        val sink = LogcatAiTraceSink(
            nowMs = { now },
            writer = { tag, message -> written += tag to message },
        )

        sink.beginSession("job-logcat")
        now = 3_250L
        sink.record(
            "ai_call_failed",
            mapOf(
                "stage" to "ai_3_search_score",
                "api_key" to "sk-logcat-secret",
                "response_body" to "private response",
            ),
        )

        assertEquals(2, written.size)
        assertTrue(written.all { it.first == LogcatAiTraceSink.TRACE_LOG_TAG })
        assertTrue(written[0].second.contains("\"job_id\":\"job-logcat\""))
        assertTrue(written[0].second.contains("\"sequence\":0"))
        assertTrue(written[1].second.contains("\"sequence\":1"))
        assertTrue(written[1].second.contains("\"elapsed_ms\":250"))
        assertFalse(written[1].second.contains("sk-logcat-secret"))
        assertFalse(written[1].second.contains("private response"))
        assertTrue(written[1].second.contains("[REDACTED]"))
    }
}
