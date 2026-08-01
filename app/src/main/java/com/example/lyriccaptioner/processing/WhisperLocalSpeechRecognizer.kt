package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WhisperLocalSpeechRecognizer(
    private val modelPath: String,
    private val bridge: WhisperNativeClient = WhisperNativeBridge,
) : LocalSpeechRecognizer {
    override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> = withContext(Dispatchers.Default) {
        if (!bridge.isAvailable) {
            throw WhisperJniUnavailableException(
                "Whisper native library is not available. Build with enableWhisperNative before using local ASR.",
            )
        }
        val audioPath = requireNotNull(audio.filePath) {
            "Whisper requires extracted audio as a readable local file path, not a content URI."
        }
        val coroutineContext = currentCoroutineContext()
        val cancellationRequested = AtomicBoolean(false)
        suspendCancellableCoroutine<List<WhisperSegment>> { continuation ->
            continuation.invokeOnCancellation { cancellationRequested.set(true) }
            runCatching {
                bridge.transcribe(
                    modelPath = modelPath,
                    audioPath = audioPath,
                    sampleRate = audio.sampleRate,
                    channels = audio.channels,
                    cancellationToken = WhisperCancellationToken {
                        cancellationRequested.get() || !coroutineContext.isActive
                    },
                )
            }.onSuccess { segments ->
                if (continuation.isActive) continuation.resume(segments)
            }.onFailure { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }.let(WhisperSegmentConverter::toCaptions)
    }
}

interface WhisperNativeClient {
    val isAvailable: Boolean

    fun transcribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken = WhisperCancellationToken { false },
    ): List<WhisperSegment>
}

fun interface WhisperCancellationToken {
    fun isCancelled(): Boolean
}

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float,
)

object WhisperNativeBridge : WhisperNativeClient {
    override val isAvailable: Boolean

    init {
        isAvailable = runCatching {
            System.loadLibrary("lyriccaptioner_whisper")
        }.isSuccess
    }

    override fun transcribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken,
    ): List<WhisperSegment> {
        return nativeTranscribe(modelPath, audioPath, sampleRate, channels, cancellationToken).toList()
    }

    private external fun nativeTranscribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken,
    ): Array<WhisperSegment>
}

class WhisperJniUnavailableException(message: String) : IllegalStateException(message)

object WhisperSegmentConverter {
    fun toCaptions(segments: List<WhisperSegment>): List<CaptionCue> {
        return AsrCaptionValidator.validate(
            segments.mapIndexed { index, segment ->
                CaptionCue(
                    id = "whisper-$index-${segment.startMs}",
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    english = segment.text,
                    chinese = "",
                    confidence = segment.confidence,
                    correctionCandidates = emptyList(),
                    confirmed = false,
                )
            },
        )
    }
}

class AudioChunker(
    private val chunkDurationMs: Long = 30_000L,
    private val overlapMs: Long = 1_000L,
) {
    fun planChunks(durationMs: Long): List<AudioChunk> {
        if (durationMs <= 0L) return emptyList()
        val chunks = mutableListOf<AudioChunk>()
        var start = 0L
        while (start < durationMs) {
            val end = minOf(durationMs, start + chunkDurationMs)
            chunks += AudioChunk(startMs = start, endMs = end)
            if (end == durationMs) break
            start = (end - overlapMs).coerceAtLeast(start + 1)
        }
        return chunks
    }
}

data class AudioChunk(
    val startMs: Long,
    val endMs: Long,
)
