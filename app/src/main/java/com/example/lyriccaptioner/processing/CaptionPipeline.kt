package com.example.lyriccaptioner.processing

import android.net.Uri
import com.example.lyriccaptioner.captions.SrtWriter
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.ExportProfile
import java.io.File

class CaptionPipeline(
    private val audioExtractor: AudioExtractor,
    private val speechRecognizer: LocalSpeechRecognizer,
    private val corrector: CaptionCorrector,
    private val translator: LocalTranslator,
    private val exportEngine: ExportEngine,
    private val srtWriter: SrtWriter = SrtWriter(),
) {
    suspend fun generateDraft(
        videoUri: Uri,
        onStatus: (String) -> Unit,
    ): List<CaptionCue> {
        val audio = audioExtractor.extract(videoUri)
        try {
            onStatus("Running local English recognition...")
            val recognized = speechRecognizer.recognize(audio)
            onStatus("Correcting low-confidence English text...")
            val corrected = corrector.correct(recognized)
            onStatus("Generating offline Chinese translation...")
            translator.prepareBatch()
            return corrected.map { cue ->
                cue.copy(chinese = translator.translateEnglishToChinese(cue.english))
            }
        } finally {
            if (audio.deleteFileAfterUse) {
                audio.filePath?.let { path -> runCatching { File(path).delete() } }
            }
        }
    }

    suspend fun export(
        videoUri: Uri,
        destinationUri: Uri,
        captions: List<CaptionCue>,
        exportProfile: ExportProfile,
        onStatus: (String) -> Unit,
    ): Uri {
        onStatus("Preparing subtitle overlay...")
        return exportEngine.export(
            CaptionProject(
                videoUri = videoUri,
                captions = captions,
                exportProfile = exportProfile,
            ),
            destinationUri,
        ).outputUri
    }

    fun exportSidecarSrt(captions: List<CaptionCue>): String {
        return srtWriter.write(captions)
    }
}

data class ExtractedAudio(
    val uri: Uri,
    val sampleRate: Int,
    val channels: Int,
    val filePath: String? = null,
    val deleteFileAfterUse: Boolean = false,
)

interface AudioExtractor {
    suspend fun extract(videoUri: Uri): ExtractedAudio
}

interface LocalSpeechRecognizer {
    suspend fun recognize(audio: ExtractedAudio): List<CaptionCue>
}

interface CaptionCorrector {
    suspend fun correct(captions: List<CaptionCue>): List<CaptionCue>
}

interface LocalTranslator {
    suspend fun prepareBatch() = Unit
    suspend fun translateEnglishToChinese(text: String): String
}

data class LocalModelStatus(
    val name: String,
    val ready: Boolean,
    val detail: String,
)

interface LocalModelManager {
    suspend fun status(): List<LocalModelStatus>
}
