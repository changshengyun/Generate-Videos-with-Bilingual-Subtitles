package com.example.lyriccaptioner.processing

import android.net.Uri
import com.example.lyriccaptioner.captions.SrtWriter
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportProfile

class CaptionPipeline(
    private val exportEngine: ExportEngine,
    private val srtWriter: SrtWriter = SrtWriter(),
) {
    suspend fun export(
        videoUri: Uri,
        destinationUri: Uri,
        captions: List<CaptionCue>,
        exportProfile: ExportProfile,
        captionLayout: CaptionLayout = CaptionLayout(),
        defaultCaptionStyle: DefaultCaptionStyle = DefaultCaptionStyle(),
        onStatus: (String) -> Unit,
    ): Uri {
        onStatus("Preparing subtitle overlay...")
        return exportEngine.export(
            CaptionProject(
                videoUri = videoUri,
                captions = captions,
                exportProfile = exportProfile,
                captionLayout = captionLayout,
                defaultCaptionStyle = defaultCaptionStyle,
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
    suspend fun isModelReady(): Boolean = false
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
