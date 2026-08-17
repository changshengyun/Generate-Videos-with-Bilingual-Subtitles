package com.example.lyriccaptioner.model

import android.net.Uri

/**
 * Product media picker contract.  The UI is expected to construct the
 * AndroidX PickVisualMedia launcher from this specification; keeping the
 * choice in a small, platform-independent value makes the import behaviour
 * testable without an Activity or a Photo Picker implementation.
 */
enum class VideoPickerContract {
    PICK_VISUAL_MEDIA,
}

enum class VideoPickerMediaType {
    VIDEO_ONLY,
}

data class VideoPickerRequest(
    val contract: VideoPickerContract = VideoPickerContract.PICK_VISUAL_MEDIA,
    val mediaType: VideoPickerMediaType = VideoPickerMediaType.VIDEO_ONLY,
    val allowMultiple: Boolean = false,
)

/** How the selected URI can be used after the picker returns. */
enum class VideoUriAccessStatus {
    PERSISTED,
    SESSION_ONLY,
    PROVIDER_UNSUPPORTED,
    UNAVAILABLE,
}

/**
 * Facts collected by the URI validator.  No filesystem path is carried here:
 * product media remains a content URI and the policy only consumes safe
 * metadata supplied by the platform repository.
 */
data class VideoUriAccess(
    val status: VideoUriAccessStatus,
    val isReadable: Boolean,
    val hasVideoTrack: Boolean,
    val durationMs: Long?,
)

sealed class VideoPickerImportDecision {
    abstract val state: EditorState

    /** A null picker result is a no-op, including no validation or work. */
    data class Cancelled(override val state: EditorState) : VideoPickerImportDecision()

    /** Invalid or unavailable media leaves the current project untouched. */
    data class Rejected(
        override val state: EditorState,
        val reason: VideoImportRejection,
    ) : VideoPickerImportDecision()

    /** Valid media is ready for the caller to persist and/or apply. */
    data class Accepted(
        override val state: EditorState,
        val uri: Uri,
        val mode: VideoImportMode,
        val access: VideoUriAccessStatus,
    ) : VideoPickerImportDecision()
}

enum class VideoImportRejection {
    EMPTY_URI,
    UNREADABLE,
    NO_VIDEO_TRACK,
    UNKNOWN_DURATION,
    INVALID_DURATION,
    TOO_LONG,
    UNAVAILABLE,
}

/**
 * Pure picker/import decision policy shared by NEW_VIDEO and RELINK flows.
 * It deliberately does not request permissions, inspect a resolver, launch a
 * picker, or start recognition; those side effects remain in the platform
 * integration owned by the orchestrator.
 */
object VideoPickerImportPolicy {
    const val MAX_VIDEO_DURATION_MS: Long = 5 * 60 * 1_000L

    fun request(): VideoPickerRequest = VideoPickerRequest()

    fun decide(
        current: EditorState,
        uri: Uri?,
        mode: VideoImportMode,
        access: VideoUriAccess?,
        status: String = defaultStatus(mode),
    ): VideoPickerImportDecision {
        // ActivityResultContracts return null on cancellation.  This branch
        // must remain before any validation or state mutation.
        if (uri == null) return VideoPickerImportDecision.Cancelled(current)
        // Avoid Uri.equals here: the policy is deliberately JVM-testable and
        // only needs to reject an empty textual content URI.
        if (uri.toString().isBlank()) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.EMPTY_URI)
        }
        if (access == null || access.status == VideoUriAccessStatus.UNAVAILABLE) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.UNAVAILABLE)
        }
        if (!access.isReadable) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.UNREADABLE)
        }
        if (!access.hasVideoTrack) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.NO_VIDEO_TRACK)
        }
        val duration = access.durationMs
            ?: return VideoPickerImportDecision.Rejected(current, VideoImportRejection.UNKNOWN_DURATION)
        if (duration <= 0L) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.INVALID_DURATION)
        }
        if (duration > MAX_VIDEO_DURATION_MS) {
            return VideoPickerImportDecision.Rejected(current, VideoImportRejection.TOO_LONG)
        }

        val mediaState = when (access.status) {
            VideoUriAccessStatus.PERSISTED -> MediaState.PERSISTED
            VideoUriAccessStatus.SESSION_ONLY -> MediaState.SESSION_ONLY
            VideoUriAccessStatus.PROVIDER_UNSUPPORTED -> MediaState.PROVIDER_UNSUPPORTED
            VideoUriAccessStatus.UNAVAILABLE -> MediaState.UNAVAILABLE
        }
        return VideoPickerImportDecision.Accepted(
            state = VideoImportPolicy.apply(
                current = current,
                uri = uri,
                durationMs = duration,
                mediaState = mediaState,
                mode = mode,
                status = status,
            ),
            uri = uri,
            mode = mode,
            access = access.status,
        )
    }

    private fun defaultStatus(mode: VideoImportMode): String = when (mode) {
        VideoImportMode.NEW_VIDEO -> "Video imported."
        VideoImportMode.RELINK -> "Video re-associated."
    }
}
