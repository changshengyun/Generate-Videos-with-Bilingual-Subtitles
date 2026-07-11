package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperLocalSpeechRecognizer(
    private val modelPath: String,
    private val bridge: WhisperNativeBridge = WhisperNativeBridge,
) : LocalSpeechRecognizer {
    override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> = withContext(Dispatchers.Default) {
        check(bridge.isAvailable) {
            "Whisper native library is not available. Install packaged JNI libraries before using local ASR."
        }
        val audioPath = requireNotNull(audio.filePath) {
            "Whisper requires extracted audio as a readable local file path, not a content URI."
        }
        bridge.transcribe(
            modelPath = modelPath,
            audioPath = audioPath,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
        ).mapIndexed { index, segment ->
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
        }
    }
}

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val confidence: Float,
)

object WhisperNativeBridge {
    val isAvailable: Boolean

    init {
        isAvailable = runCatching {
            System.loadLibrary("lyriccaptioner_whisper")
        }.isSuccess
    }

    fun transcribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
    ): List<WhisperSegment> {
        return nativeTranscribe(modelPath, audioPath, sampleRate, channels).toList()
    }

    private external fun nativeTranscribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
    ): Array<WhisperSegment>
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
