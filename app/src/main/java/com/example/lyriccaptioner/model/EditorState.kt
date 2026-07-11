package com.example.lyriccaptioner.model

import android.net.Uri

data class EditorState(
    val videoUri: Uri? = null,
    val videoDurationMs: Long? = null,
    val captions: List<CaptionCue> = emptyList(),
    val selectedCaptionId: String? = null,
    val modelState: ModelState = ModelState(),
    val exportProfile: ExportProfile = ExportProfile(),
    val isWorking: Boolean = false,
    val status: String = "Import a video up to 5 minutes to start.",
    val exportUri: Uri? = null,
    val pendingSidecarSrt: String? = null,
    val pendingProjectArchive: String? = null,
)

data class ModelState(
    val speechModelReady: Boolean = false,
    val speechModelInstalled: Boolean = false,
    val speechNativeLibraryReady: Boolean = false,
    val speechRuntimeDetail: String = "Checking local speech runtime...",
    val translationModelReady: Boolean = false,
    val maxVideoDurationMs: Long = 5 * 60 * 1_000L,
)
