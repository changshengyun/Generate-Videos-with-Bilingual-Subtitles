package com.example.lyriccaptioner.model

import android.net.Uri

enum class VideoImportMode {
    NEW_VIDEO,
    RELINK,
}

object VideoImportPolicy {
    fun isDurationAllowed(durationMs: Long?, maxDurationMs: Long): Boolean =
        durationMs != null && durationMs > 0L && durationMs <= maxDurationMs

    fun apply(
        current: EditorState,
        uri: Uri,
        durationMs: Long?,
        mediaState: MediaState,
        mode: VideoImportMode,
        status: String,
    ): EditorState {
        val updated = when (mode) {
            VideoImportMode.NEW_VIDEO -> current.copy(
                videoUri = uri,
                videoDurationMs = durationMs,
                mediaState = mediaState,
                captions = emptyList(),
                selectedCaptionId = null,
                status = status,
            )
            VideoImportMode.RELINK -> current.copy(
                videoUri = uri,
                videoDurationMs = durationMs,
                mediaState = mediaState,
                status = status,
            )
        }
        return DerivedOutputPolicy.invalidateDerivedOutputs(updated)
    }
}
