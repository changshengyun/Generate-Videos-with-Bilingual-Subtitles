package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue

class WhisperLocalSpeechRecognizer(
    private val modelPath: String,
    private val sessionRuntime: WhisperSessionRuntime,
) : LocalSpeechRecognizer {
    override suspend fun recognize(audio: ExtractedAudio): List<CaptionCue> {
        val audioPath = requireNotNull(audio.filePath) {
            "Whisper requires extracted audio as a readable local file path, not a content URI."
        }
        return sessionRuntime.transcribe(
            modelPath = modelPath,
            audioPath = audioPath,
            sampleRate = audio.sampleRate,
            channels = audio.channels,
        ).let(WhisperSegmentConverter::toCaptions)
    }
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
