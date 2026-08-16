package com.example.lyriccaptioner.processing

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os

enum class ExportDestinationState {
    NEW,
    EXISTING,
    UNKNOWN,
}

data class OpenFileIdentity(
    val device: Long,
    val inode: Long,
)

object ExportDestinationPolicy {
    fun isSameDocument(
        source: Uri,
        destination: Uri,
        resolver: ContentResolver? = null,
    ): Boolean {
        if (source.toString() == destination.toString()) return true
        if (source.scheme == destination.scheme && source.authority == destination.authority) {
            val sourceId = documentId(source) ?: return false
            val destinationId = documentId(destination) ?: return false
            return sourceId == destinationId
        }
        // URI authority is not a file identity. When both providers expose the same
        // underlying inode, reject the destination even though their URI strings differ.
        // A missing/unsupported descriptor is treated as "not proven same" here; the
        // destination preflight separately fails closed when it cannot prove ownership.
        if (resolver == null) return false
        return sameOpenFile(resolver, source, destination)
    }

    fun inspectDestination(resolver: ContentResolver, destination: Uri): ExportDestinationState {
        if (destination.scheme == "file") {
            return if (java.io.File(destination.path.orEmpty()).exists()) {
                ExportDestinationState.EXISTING
            } else {
                ExportDestinationState.NEW
            }
        }
        if (destination.scheme == "content" && destination.authority == MediaStore.AUTHORITY) {
            return inspectMediaStoreRow(resolver, destination)
        }
        return try {
            resolver.query(
                destination,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        ExportDestinationState.NEW
                    } else {
                        val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        if (sizeColumn < 0) return@use ExportDestinationState.UNKNOWN
                        val sizeBytes = sizeColumn.takeIf { !cursor.isNull(it) }
                            ?.let { index -> cursor.getLong(index) }
                        classifyDocumentQuery(true, sizeBytes)
                    }
                }
                ?: ExportDestinationState.UNKNOWN
        } catch (_: SecurityException) {
            ExportDestinationState.UNKNOWN
        } catch (_: UnsupportedOperationException) {
            ExportDestinationState.UNKNOWN
        } catch (_: IllegalArgumentException) {
            ExportDestinationState.UNKNOWN
        }
    }

    private fun inspectMediaStoreRow(
        resolver: ContentResolver,
        destination: Uri,
    ): ExportDestinationState = runCatching {
        resolver.query(
            destination,
            arrayOf(
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.IS_PENDING,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) {
                ExportDestinationState.NEW
            } else {
                val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                if (sizeColumn < 0) return@use ExportDestinationState.UNKNOWN
                val sizeBytes = sizeColumn.takeIf { !cursor.isNull(it) }
                    ?.let { index -> cursor.getLong(index) }
                // IS_PENDING is not reliably queryable on every OEM MediaStore. Treat a
                // missing/null column as "unknown pending" and fall back to the size signal,
                // which is the only field that proves pre-existing user content.
                val pendingColumn = cursor.getColumnIndex(MediaStore.Video.Media.IS_PENDING)
                val isPending = pendingColumn.takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { index -> cursor.getInt(index) }
                classifyMediaStoreQuery(true, sizeBytes, isPending)
            }
        } ?: ExportDestinationState.UNKNOWN
    }.getOrDefault(ExportDestinationState.UNKNOWN)

    internal fun classifyDocumentQuery(hasDocumentRow: Boolean?): ExportDestinationState = when (hasDocumentRow) {
        true -> ExportDestinationState.EXISTING
        false -> ExportDestinationState.NEW
        null -> ExportDestinationState.UNKNOWN
    }

    internal fun classifyDocumentQuery(
        hasDocumentRow: Boolean,
        sizeBytes: Long?,
    ): ExportDestinationState = when {
        !hasDocumentRow -> ExportDestinationState.NEW
        sizeBytes == 0L -> ExportDestinationState.NEW
        else -> ExportDestinationState.EXISTING
    }

    internal fun classifyMediaStoreQuery(
        hasRow: Boolean?,
        sizeBytes: Long?,
        isPending: Int?,
    ): ExportDestinationState = when {
        hasRow == null -> ExportDestinationState.UNKNOWN
        !hasRow -> ExportDestinationState.NEW
        isPending == 1 -> ExportDestinationState.NEW
        sizeBytes == null || sizeBytes == 0L -> ExportDestinationState.NEW
        else -> ExportDestinationState.EXISTING
    }

    internal fun requireNewDestination(state: ExportDestinationState) {
        check(state == ExportDestinationState.NEW) {
            when (state) {
                ExportDestinationState.EXISTING -> "The export destination already exists."
                ExportDestinationState.UNKNOWN -> "The export destination ownership is unknown."
                ExportDestinationState.NEW -> ""
            }
        }
    }

    internal fun sameOpenFile(
        resolver: ContentResolver,
        source: Uri,
        destination: Uri,
    ): Boolean {
        val sourceIdentity = openFileIdentity(resolver, source) ?: return false
        val destinationIdentity = openFileIdentity(resolver, destination) ?: return false
        return sourceIdentity == destinationIdentity
    }

    internal fun sameOpenFileIdentity(
        source: OpenFileIdentity?,
        destination: OpenFileIdentity?,
    ): Boolean = source != null && source == destination

    private fun openFileIdentity(resolver: ContentResolver, uri: Uri): OpenFileIdentity? {
        return runCatching {
            if (uri.scheme == "file") {
                ParcelFileDescriptor.open(
                    java.io.File(uri.path.orEmpty()),
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { descriptor -> statIdentity(descriptor) }
            } else {
                resolver.openFileDescriptor(uri, "r")?.use { descriptor -> statIdentity(descriptor) }
            }
        }.getOrNull()
    }

    private fun statIdentity(descriptor: ParcelFileDescriptor): OpenFileIdentity {
        val stat = Os.fstat(descriptor.fileDescriptor)
        return OpenFileIdentity(device = stat.st_dev, inode = stat.st_ino)
    }

    private fun documentId(uri: Uri): String? = runCatching {
        DocumentsContract.getDocumentId(uri)
    }.getOrNull()
}
