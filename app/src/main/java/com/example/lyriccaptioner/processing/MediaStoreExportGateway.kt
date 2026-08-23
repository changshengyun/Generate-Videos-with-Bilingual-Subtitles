package com.example.lyriccaptioner.processing

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Platform policy for writing a product video to the system gallery. */
data class MediaStoreWritePolicy(
    val apiLevel: Int,
    val hasLegacyWritePermission: Boolean,
) {
    init {
        require(apiLevel >= 26) { "MediaStore export requires API 26 or newer" }
    }

    val usesPendingRows: Boolean get() = apiLevel >= 29
    val requiresLegacyWritePermission: Boolean get() = apiLevel in 26..28

    fun requireAllowed() {
        if (requiresLegacyWritePermission && !hasLegacyWritePermission) {
            throw LegacyStoragePermissionRequired()
        }
    }

    companion object {
        fun current(hasLegacyWritePermission: Boolean): MediaStoreWritePolicy =
            MediaStoreWritePolicy(Build.VERSION.SDK_INT, hasLegacyWritePermission)
    }
}

class LegacyStoragePermissionRequired : IllegalStateException(
    "Storage permission is required on Android 8.1 and older.",
)

/** Opaque destination row owned by one export task. */
data class MediaStoreDestination(
    val uri: Uri,
    val ownerToken: String,
    val displayName: String,
    val usesPendingRow: Boolean,
)

/** Small platform boundary; tests can provide a fake without a ContentResolver. */
interface MediaStoreDestinationStore {
    fun insertVideo(displayName: String, policy: MediaStoreWritePolicy): MediaStoreDestination?
    fun openOutput(destination: MediaStoreDestination): OutputStream?
    fun sizeBytes(destination: MediaStoreDestination): Long?
    fun publish(destination: MediaStoreDestination)
    fun delete(destination: MediaStoreDestination)
}

/** Android MediaStore implementation. It never exposes a filesystem path. */
class AndroidMediaStoreDestinationStore(
    private val resolver: ContentResolver,
    private val videoCollection: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
) : MediaStoreDestinationStore {
    override fun insertVideo(displayName: String, policy: MediaStoreWritePolicy): MediaStoreDestination? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (policy.usesPendingRows) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LyricCaptioner")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "LyricCaptioner",
                )
                check(directory.exists() || directory.mkdirs()) {
                    "Could not create the legacy Movies/LyricCaptioner directory."
                }
                put(MediaStore.Video.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        val uri = resolver.insert(videoCollection, values) ?: return null
        return MediaStoreDestination(uri, ownerToken = UUID.randomUUID().toString(), displayName, policy.usesPendingRows)
    }

    override fun openOutput(destination: MediaStoreDestination): OutputStream? =
        resolver.openOutputStream(destination.uri, "w")

    override fun sizeBytes(destination: MediaStoreDestination): Long? = runCatching {
        // The MediaStore SIZE column is not reliably refreshed for pending rows on some
        // OEM builds (e.g. MIUI) until IS_PENDING is cleared. Stat the underlying file
        // descriptor instead, which reflects the actual bytes already written.
        resolver.openFileDescriptor(destination.uri, "r")?.use { descriptor ->
            descriptor.statSize
        }
    }.getOrNull()

    override fun publish(destination: MediaStoreDestination) {
        if (!destination.usesPendingRow) return
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        // Some OEM MediaStore returns 0 for a successful publish (the row may already
        // report IS_PENDING=0). Only a negative result indicates a real failure.
        val updated = resolver.update(destination.uri, values, null, null)
        if (updated < 0) {
            throw IllegalStateException("MediaStore publish did not update the task-owned row")
        }
    }

    override fun delete(destination: MediaStoreDestination) {
        resolver.delete(destination.uri, null, null)
    }
}

enum class MediaStoreExportState {
    CREATED,
    WRITING,
    PUBLISHED,
    CANCELLED,
    FAILED,
}

data class MediaStoreExportResult(
    val uri: Uri,
    val sizeBytes: Long,
)

/**
 * Task-owned lifecycle for one gallery export. A row is deleted only when this
 * session created it, and terminal operations are idempotent.
 */
class MediaStoreExportSession internal constructor(
    private val store: MediaStoreDestinationStore,
    val destination: MediaStoreDestination,
) {
    var state: MediaStoreExportState = MediaStoreExportState.CREATED
        private set
    private var publishedResult: MediaStoreExportResult? = null

    /** Marks that an external encoder owns the destination URI for this task. */
    fun beginExternalWrite(): MediaStoreExportSession {
        check(state == MediaStoreExportState.CREATED) { "Export is already terminal: $state" }
        state = MediaStoreExportState.WRITING
        return this
    }

    fun openOutput(): OutputStream {
        check(state == MediaStoreExportState.CREATED || state == MediaStoreExportState.WRITING) {
            "Export is already terminal: $state"
        }
        state = MediaStoreExportState.WRITING
        return store.openOutput(destination) ?: fail("Could not open MediaStore output")
    }

    fun publish(expectedSize: Long? = null): MediaStoreExportResult {
        if (state == MediaStoreExportState.PUBLISHED) {
            return checkNotNull(publishedResult)
        }
        check(state == MediaStoreExportState.WRITING) { "Export is not writable: $state" }
        val size = expectedSize ?: store.sizeBytes(destination)
            ?: return fail("Could not validate MediaStore output")
        if (size <= 0L) return fail("MediaStore output is empty")
        return try {
            store.publish(destination)
            val result = MediaStoreExportResult(destination.uri, size)
            publishedResult = result
            state = MediaStoreExportState.PUBLISHED
            result
        } catch (error: Throwable) {
            fail("Could not publish MediaStore output", error)
        }
    }

    fun cancel() {
        if (state == MediaStoreExportState.PUBLISHED ||
            state == MediaStoreExportState.CANCELLED ||
            state == MediaStoreExportState.FAILED
        ) return
        rollback(MediaStoreExportState.CANCELLED)
    }

    fun rollback() {
        if (state == MediaStoreExportState.PUBLISHED ||
            state == MediaStoreExportState.CANCELLED ||
            state == MediaStoreExportState.FAILED
        ) return
        rollback(MediaStoreExportState.FAILED)
    }

    private fun rollback(terminalState: MediaStoreExportState) {
        runCatching { store.delete(destination) }
        state = terminalState
    }

    private fun <T> fail(message: String, cause: Throwable? = null): T {
        rollback(MediaStoreExportState.FAILED)
        if (cause != null) throw IllegalStateException(message, cause)
        throw IllegalStateException(message)
    }
}

class MediaStoreExportGateway(
    private val store: MediaStoreDestinationStore,
    private val policy: MediaStoreWritePolicy,
    private val nameFactory: (String) -> String = { taskId ->
        "LyricCaptioner-${taskId.filter { it.isLetterOrDigit() }.take(32)}-${UUID.randomUUID()}.mp4"
    },
) {
    fun begin(taskId: String, sourceUri: Uri? = null): MediaStoreExportSession {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        policy.requireAllowed()
        val first = nameFactory(taskId)
        require(first.endsWith(".mp4", ignoreCase = true)) { "MediaStore output must be .mp4" }
        val destination = store.insertVideo(first, policy)
            ?: throw IllegalStateException("Could not create MediaStore output")
        if (sourceUri != null && sourceUri.toString() == destination.uri.toString()) {
            // The row is ours, so it is safe to remove it before exposing a session.
            store.delete(destination)
            throw IllegalArgumentException("Export destination must differ from source")
        }
        return MediaStoreExportSession(store, destination)
    }
}
