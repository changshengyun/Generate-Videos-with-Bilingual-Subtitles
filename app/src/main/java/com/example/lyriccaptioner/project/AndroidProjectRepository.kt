package com.example.lyriccaptioner.project

import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidProjectRepository(
    context: Context,
    private val archive: ProjectArchive = ProjectArchive(),
) : ProjectRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun save(
        snapshot: com.example.lyriccaptioner.model.ProjectSnapshot,
        destinationUri: Uri,
    ): ProjectSaveResult = withContext(Dispatchers.IO) {
        if (destinationUri == Uri.EMPTY || destinationUri.toString().isBlank()) {
            return@withContext ProjectSaveResult.Failure(
                ProjectRepositoryError(ProjectErrorKind.INVALID_INPUT, "The project destination is empty."),
            )
        }
        runCatching {
            resolver.openOutputStream(destinationUri, "wt")?.use { output ->
                output.write(archive.write(snapshot).toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: error("Could not open the project destination.")
        }.fold(
            onSuccess = { ProjectSaveResult.Success(destinationUri) },
            onFailure = { error -> ProjectSaveResult.Failure(mapError(error, ProjectErrorKind.WRITE)) },
        )
    }

    override suspend fun load(sourceUri: Uri): ProjectLoadResult = withContext(Dispatchers.IO) {
        if (sourceUri == Uri.EMPTY || sourceUri.toString().isBlank()) {
            return@withContext ProjectLoadResult.Failure(
                ProjectRepositoryError(ProjectErrorKind.INVALID_INPUT, "The project source is empty."),
            )
        }
        runCatching {
            val raw = resolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Could not open the project source.")
            val snapshot = archive.read(raw)
            val mediaAccess = snapshot.videoUri?.let { validateMediaAccess(Uri.parse(it)) }
                ?: MediaAccessResult.Unavailable(Uri.EMPTY, "The project has no video URI.")
            ProjectLoadResult.Success(sourceUri, snapshot, mediaAccess)
        }.fold(
            onSuccess = { it },
            onFailure = { error -> ProjectLoadResult.Failure(mapError(error, ProjectErrorKind.READ)) },
        )
    }

    override fun retainMediaReadAccess(uri: Uri): MediaAccessResult {
        val validated = validateMediaAccess(uri)
        if (!validated.isReadable) return validated
        if (hasPersistedReadPermission(uri)) return validated.asPersisted()
        return try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (hasPersistedReadPermission(uri)) {
                MediaAccessResult.Persisted(uri, validated.durationMs)
            } else {
                MediaAccessResult.ProviderUnsupported(
                    uri,
                    validated.durationMs,
                    "Provider did not grant persistable permissions; video is session-only.",
                )
            }
        } catch (_: UnsupportedOperationException) {
            MediaAccessResult.ProviderUnsupported(
                uri,
                validated.durationMs,
                "Provider does not support persistable permissions; video is session-only.",
            )
        } catch (_: IllegalArgumentException) {
            MediaAccessResult.ProviderUnsupported(
                uri,
                validated.durationMs,
                "Provider rejected persistable permissions; video is session-only.",
            )
        } catch (_: SecurityException) {
            MediaAccessResult.SessionOnly(uri, validated.durationMs, "Media is readable for this session only.")
        }
    }

    override fun validateMediaAccess(uri: Uri): MediaAccessResult {
        if (uri == Uri.EMPTY || uri.toString().isBlank()) {
            return MediaAccessResult.Unavailable(uri, "The video URI is empty.")
        }
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                if (input.read() < 0) error("The selected video is empty.")
            } ?: error("The video cannot be read.")
            val durationMs = readVideoDuration(uri)
            if (hasPersistedReadPermission(uri)) {
                MediaAccessResult.Persisted(uri, durationMs)
            } else {
                MediaAccessResult.SessionOnly(uri, durationMs, "Media is readable without persisted permission.")
            }
        }.getOrElse { error ->
            MediaAccessResult.Unavailable(uri, error.message ?: "The video cannot be read.")
        }
    }

    private fun readVideoDuration(uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
        if (durationMs == null || durationMs <= 0L) {
            error("The video duration is unavailable or invalid.")
        }
        if (durationMs > MAX_VIDEO_DURATION_MS) {
            error("The video is longer than 5 minutes.")
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, uri, null)
            val hasVideoTrack = (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
            if (!hasVideoTrack) error("The selected file has no video track.")
        } finally {
            extractor.release()
        }
        return durationMs
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean = runCatching {
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }.getOrDefault(false)

    private fun MediaAccessResult.asPersisted(): MediaAccessResult =
        MediaAccessResult.Persisted(uri, durationMs)

    private fun mapError(error: Throwable, fallback: ProjectErrorKind): ProjectRepositoryError {
        val kind = when (error) {
            is ProjectArchiveException -> ProjectErrorKind.FORMAT
            is SecurityException -> ProjectErrorKind.PERMISSION
            else -> fallback
        }
        return ProjectRepositoryError(kind, error.message ?: "Project operation failed.", error)
    }

    private companion object {
        const val MAX_VIDEO_DURATION_MS = 5 * 60 * 1_000L
    }
}
