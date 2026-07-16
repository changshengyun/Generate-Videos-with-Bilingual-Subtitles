package com.example.lyriccaptioner.processing

import android.net.Uri
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.SpeechMode
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

interface AsrModule {
    val runtimeStatus: WhisperRuntimeStatus

    suspend fun recognize(
        videoUri: Uri,
        onStatus: (String) -> Unit = {},
    ): List<CaptionCue>
}

class WhisperAsrModule(
    override val runtimeStatus: WhisperRuntimeStatus,
    private val audioExtractor: AudioExtractor,
    private val speechRecognizer: LocalSpeechRecognizer,
) : AsrModule {
    override suspend fun recognize(
        videoUri: Uri,
        onStatus: (String) -> Unit,
    ): List<CaptionCue> {
        if (runtimeStatus.mode == SpeechMode.UNAVAILABLE) {
            throw AsrUnavailableException(runtimeStatus.detail)
        }

        val routeName = if (runtimeStatus.mode == SpeechMode.LOCAL) "Local Whisper JNI" else "Demo ASR"
        onStatus("Extracting audio for $routeName...")
        val audio = audioExtractor.extract(videoUri)
        try {
            currentCoroutineContext().ensureActive()
            onStatus("Running $routeName...")
            val captions = speechRecognizer.recognize(audio)
            currentCoroutineContext().ensureActive()
            return AsrCaptionValidator.validate(captions)
        } finally {
            cleanup(audio)
        }
    }

    private fun cleanup(audio: ExtractedAudio) {
        if (audio.deleteFileAfterUse) {
            audio.filePath?.let { path -> runCatching { File(path).delete() } }
        }
    }
}

class UnavailableAsrModule(
    override val runtimeStatus: WhisperRuntimeStatus,
) : AsrModule {
    override suspend fun recognize(
        videoUri: Uri,
        onStatus: (String) -> Unit,
    ): List<CaptionCue> = throw AsrUnavailableException(runtimeStatus.detail)
}

object AsrCaptionValidator {
    fun validate(captions: List<CaptionCue>): List<CaptionCue> {
        val nonEmpty = captions.mapNotNull { cue ->
            val text = cue.english.trim()
            if (text.isEmpty()) null else cue.copy(english = text, chinese = "")
        }
        if (nonEmpty.isEmpty()) {
            throw AsrOutputFormatException("Whisper returned no non-empty English captions.")
        }

        val ids = mutableSetOf<String>()
        var previousStart = -1L
        var previousEnd = -1L
        nonEmpty.forEach { cue ->
            if (cue.id.isBlank() || !ids.add(cue.id)) {
                throw AsrOutputFormatException("Whisper returned duplicate or empty caption IDs.")
            }
            if (cue.startMs < 0L || cue.endMs <= cue.startMs || cue.startMs < previousStart || cue.endMs < previousEnd) {
                throw AsrOutputFormatException("Whisper returned invalid or unordered caption timestamps.")
            }
            if (!cue.confidence.isFinite() || cue.confidence !in 0f..1f) {
                throw AsrOutputFormatException("Whisper returned an invalid caption confidence.")
            }
            previousStart = cue.startMs
            previousEnd = cue.endMs
        }
        return nonEmpty
    }
}

class AsrUnavailableException(message: String) : IllegalStateException(message)

class AsrOutputFormatException(message: String) : IllegalStateException(message)
