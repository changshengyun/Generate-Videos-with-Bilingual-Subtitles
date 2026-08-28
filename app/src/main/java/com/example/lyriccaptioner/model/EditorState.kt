package com.example.lyriccaptioner.model

import android.net.Uri
import com.example.lyriccaptioner.processing.TranslationModelState

data class EditorState(
    val videoUri: Uri? = null,
    val videoDurationMs: Long? = null,
    val mediaState: MediaState = MediaState.NONE,
    val mediaRevision: Long = 0L,
    val requiresVideoAssociation: Boolean = false,
    val captions: List<CaptionCue> = emptyList(),
    /** Editor-session scope only; deliberately excluded from project archives. */
    val lyricLines: List<String> = emptyList(),
    val captionProcessing: CaptionProcessingSnapshot = CaptionProcessingSnapshot(),
    val selectedCaptionId: String? = null,
    /** Editor-session scope only; deliberately excluded from project archives. */
    val layoutEditLocked: Boolean = false,
    /** Editor-session scope only; deliberately excluded from project archives. */
    val styleEditLocked: Boolean = false,
    val captionLayout: CaptionLayout = CaptionLayout(),
    val defaultCaptionStyle: DefaultCaptionStyle = DefaultCaptionStyle(),
    val modelState: ModelState = ModelState(),
    val exportProfile: ExportProfile = ExportProfile(),
    val isWorking: Boolean = false,
    val asrRunning: Boolean = false,
    val enhancementRunning: Boolean = false,
    val captionWorkflowStage: CaptionWorkflowStage = CaptionWorkflowStage.IDLE,
    val status: String = "Import a video up to 5 minutes to start.",
    val exportUri: Uri? = null,
    val exportState: ExportState = ExportState.IDLE,
)

enum class CaptionWorkflowStage {
    IDLE,
    LOCAL_RECOGNIZING,
    AI_ENHANCING,
    READY_FOR_EDIT,
    FAILED,
    CANCELLED,
}

enum class ExportState {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

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
    val translationModelState: TranslationModelState = TranslationModelState.NEEDS_INSTALL,
    val maxVideoDurationMs: Long = 5 * 60 * 1_000L,
)
