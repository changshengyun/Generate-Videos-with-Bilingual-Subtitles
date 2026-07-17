package com.example.lyriccaptioner.model

import android.net.Uri
import com.example.lyriccaptioner.processing.TranslationModelState

data class EditorState(
    val videoUri: Uri? = null,
    val videoDurationMs: Long? = null,
    val mediaState: MediaState = MediaState.NONE,
    val captions: List<CaptionCue> = emptyList(),
    val selectedCaptionId: String? = null,
    val modelState: ModelState = ModelState(),
    val exportProfile: ExportProfile = ExportProfile(),
    val isWorking: Boolean = false,
    val asrRunning: Boolean = false,
    val translationRunning: Boolean = false,
    val status: String = "Import a video up to 5 minutes to start.",
    val exportUri: Uri? = null,
    val pendingSidecarSrt: String? = null,
)

enum class MediaState {
    NONE,
    PERSISTED,
    SESSION_ONLY,
    PROVIDER_UNSUPPORTED,
    UNAVAILABLE,
}

data class ModelState(
    val speechModelReady: Boolean = false,
    val speechModelInstalled: Boolean = false,
    val speechNativeLibraryReady: Boolean = false,
    val speechRuntimeDetail: String = "Checking local speech runtime...",
    val speechMode: SpeechMode = SpeechMode.UNAVAILABLE,
    val translationModelState: TranslationModelState = TranslationModelState.NEEDS_DOWNLOAD,
    val maxVideoDurationMs: Long = 5 * 60 * 1_000L,
)
