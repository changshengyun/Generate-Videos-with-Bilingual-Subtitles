package com.example.lyriccaptioner.processing

/** Stable Kotlin/native boundary owned by the V3-ASR-SESSION-001 orchestrator. */
interface WhisperSessionNativeClient {
    val isAvailable: Boolean

    fun createContext(modelPath: String): Long

    fun transcribe(
        contextHandle: Long,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken = WhisperCancellationToken { false },
    ): List<WhisperSegment>

    fun requestAbort(contextHandle: Long)

    fun freeContext(contextHandle: Long)
}

data class WhisperModelIdentity(
    val canonicalPath: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class WhisperSessionRunMetrics(
    val contextHandle: Long,
    val reusedContext: Boolean,
    val contextLoadMs: Long,
    val inferenceMs: Long,
    val totalMs: Long,
    val segmentCount: Int,
    val cancelled: Boolean,
)

fun interface WhisperSessionObserver {
    fun onRunCompleted(metrics: WhisperSessionRunMetrics)
}
