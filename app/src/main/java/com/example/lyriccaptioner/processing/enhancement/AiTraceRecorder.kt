package com.example.lyriccaptioner.processing.enhancement

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.StandardCopyOption

interface AiTraceSink {
    fun beginSession(jobId: String)

    fun record(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    )

    companion object {
        val NONE: AiTraceSink = object : AiTraceSink {
            override fun beginSession(jobId: String) = Unit

            override fun record(event: String, fields: Map<String, Any?>) = Unit
        }
    }
}

internal fun AiTraceSink.bestEffort(): AiTraceSink = object : AiTraceSink {
    override fun beginSession(jobId: String) {
        runCatching { this@bestEffort.beginSession(jobId) }
    }

    override fun record(event: String, fields: Map<String, Any?>) {
        runCatching { this@bestEffort.record(event, fields) }
    }
}

internal class CompositeAiTraceSink(
    private val delegates: List<AiTraceSink>,
) : AiTraceSink {
    override fun beginSession(jobId: String) {
        delegates.forEach { it.beginSession(jobId) }
    }

    override fun record(event: String, fields: Map<String, Any?>) {
        delegates.forEach { it.record(event, fields) }
    }
}

internal class LogcatAiTraceSink(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val writer: (String, String) -> Unit = { tag, message -> Log.i(tag, message) },
) : AiTraceSink {
    private var activeJobId: String? = null
    private var startedAtMs = 0L
    private var sequence = 0L

    override fun beginSession(jobId: String) {
        if (jobId.isBlank()) return
        activeJobId = jobId
        startedAtMs = nowMs()
        sequence = 0L
        write("workflow_started", emptyMap())
    }

    override fun record(event: String, fields: Map<String, Any?>) {
        if (event.isBlank()) return
        write(event, fields)
    }

    private fun write(event: String, fields: Map<String, Any?>) {
        val jobId = activeJobId ?: return
        val timestampMs = nowMs()
        writer(
            TRACE_LOG_TAG,
            encodeJson(
                linkedMapOf(
                    "schema_version" to FileAiTraceRecorder.TRACE_SCHEMA_VERSION,
                    "job_id" to jobId,
                    "sequence" to sequence++,
                    "timestamp_ms" to timestampMs,
                    "elapsed_ms" to (timestampMs - startedAtMs).coerceAtLeast(0L),
                    "event" to event,
                    "fields" to AiTraceSanitizer.sanitize(fields),
                ),
            ),
        )
    }

    companion object {
        const val TRACE_LOG_TAG = "LyricCaptionerTrace"
    }
}

private object AiTraceSanitizer {
    private const val REDACTED = "[REDACTED]"
    private val forbiddenFieldNames = setOf(
        "api_key",
        "authorization",
        "request_body",
        "complete_english_lyrics",
        "prompt",
        "response_body",
        "reasoning_content",
    )
    private val authorizationPattern = Regex("(?i)authorization\\s*:\\s*bearer\\s+\\S+")
    private val apiKeyPattern = Regex("sk-[A-Za-z0-9_-]+")

    fun sanitize(fields: Map<String, Any?>): Map<String, Any?> = fields.mapValues { (key, value) ->
        if (key.lowercase() in forbiddenFieldNames) REDACTED else sanitizeValue(value)
    }

    private fun sanitizeValue(value: Any?): Any? = when (value) {
        is String -> value
            .replace(authorizationPattern, "Authorization: $REDACTED")
            .replace(apiKeyPattern, REDACTED)
        is Map<*, *> -> value.entries.associate { (key, item) ->
            val stringKey = key.toString()
            stringKey to if (stringKey.lowercase() in forbiddenFieldNames) REDACTED else sanitizeValue(item)
        }
        is Iterable<*> -> value.map(::sanitizeValue)
        else -> value
    }
}

/**
 * One-session JSONL recorder. Creating the recorder clears a trace left by a previous process,
 * while [beginSession] replaces the previous workflow in the current process.
 */
internal class FileAiTraceRecorder(
    private val traceFile: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maximumBytes: Long = MAX_TRACE_BYTES,
) : AiTraceSink {
    private val lock = Any()
    private var activeJobId: String? = null
    private var startedAtMs: Long = 0L
    private var sequence: Long = 0L
    private var truncated = false
    private var disabled = false

    init {
        runCatching {
            traceFile.parentFile?.mkdirs()
            if (traceFile.exists() && !traceFile.delete()) {
                disabled = true
            }
        }.onFailure {
            disabled = true
        }
    }

    override fun beginSession(jobId: String) {
        runCatching {
            synchronized(lock) {
                if (disabled || jobId.isBlank()) return@synchronized
                activeJobId = jobId
                startedAtMs = nowMs()
                sequence = 0L
                truncated = false
                replaceWithLine(buildLine(event = "workflow_started", fields = emptyMap()))
            }
        }.onFailure {
            disabled = true
        }
    }

    override fun record(event: String, fields: Map<String, Any?>) {
        runCatching {
            synchronized(lock) {
                if (disabled || activeJobId == null || truncated || event.isBlank()) return@synchronized
                val line = buildLine(event, fields)
                val encoded = (line + "\n").toByteArray(StandardCharsets.UTF_8)
                val currentSize = if (traceFile.exists()) traceFile.length() else 0L
                if (currentSize + encoded.size > payloadLimitBytes()) {
                    appendTruncationEvent(currentSize)
                    truncated = true
                    return@synchronized
                }
                traceFile.appendText(line + "\n", Charsets.UTF_8)
            }
        }.onFailure {
            disabled = true
        }
    }

    private fun replaceWithLine(line: String) {
        val encoded = (line + "\n").toByteArray(StandardCharsets.UTF_8)
        if (encoded.size > payloadLimitBytes()) {
            disabled = true
            return
        }
        val parent = traceFile.parentFile ?: run {
            disabled = true
            return
        }
        parent.mkdirs()
        val temporaryFile = File(parent, "${traceFile.name}.tmp")
        temporaryFile.writeBytes(encoded)
        try {
            java.nio.file.Files.move(
                temporaryFile.toPath(),
                traceFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun appendTruncationEvent(currentSize: Long) {
        val line = buildLine(
            event = "trace_truncated",
            fields = mapOf(
                "maximum_bytes" to maximumBytes,
                "bytes_before_truncation" to currentSize,
            ),
        )
        val encoded = (line + "\n").toByteArray(StandardCharsets.UTF_8)
        if (currentSize + encoded.size <= maximumBytes) {
            traceFile.appendText(line + "\n", Charsets.UTF_8)
        } else {
            disabled = true
        }
    }

    private fun payloadLimitBytes(): Long = (maximumBytes - terminalReserveBytes()).coerceAtLeast(0L)

    private fun terminalReserveBytes(): Long = minOf(MAX_TERMINAL_RESERVE_BYTES, maximumBytes / 2L)

    private fun buildLine(event: String, fields: Map<String, Any?>): String {
        val currentSequence = sequence++
        val timestampMs = nowMs()
        return encodeJson(
            linkedMapOf(
                "schema_version" to TRACE_SCHEMA_VERSION,
                "job_id" to checkNotNull(activeJobId),
                "sequence" to currentSequence,
                "timestamp_ms" to timestampMs,
                "elapsed_ms" to (timestampMs - startedAtMs).coerceAtLeast(0L),
                "event" to event,
                "fields" to AiTraceSanitizer.sanitize(fields),
            ),
        )
    }

    companion object {
        const val TRACE_FILE_NAME = "ai-trace.jsonl"
        const val TRACE_SCHEMA_VERSION = "ai-trace.v1"
        const val MAX_TRACE_BYTES = 1_048_576L
        private const val MAX_TERMINAL_RESERVE_BYTES = 8_192L
    }
}

internal object AndroidAiTraceRecorder {
    @Volatile
    private var processRecorder: AiTraceSink? = null

    fun get(context: Context): AiTraceSink = processRecorder ?: synchronized(this) {
        processRecorder ?: runCatching {
            CompositeAiTraceSink(
                listOf(
                    FileAiTraceRecorder(
                        traceFile = File(context.applicationContext.cacheDir, FileAiTraceRecorder.TRACE_FILE_NAME),
                    ).bestEffort(),
                    LogcatAiTraceSink().bestEffort(),
                ),
            ).bestEffort()
        }.getOrDefault(AiTraceSink.NONE).also { processRecorder = it }
    }
}
