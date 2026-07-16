package com.example.lyriccaptioner.project

import android.content.Context
import android.content.Intent
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
            MediaAccessResult.Persisted(uri, validated.durationMs)
        } catch (_: UnsupportedOperationException) {
            MediaAccessResult.ProviderUnsupported(uri, validated.durationMs, "Provider does not support persistable permissions.")
        } catch (_: IllegalArgumentException) {
            MediaAccessResult.ProviderUnsupported(uri, validated.durationMs, "Provider rejected persistable permissions.")
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
            val durationMs = readDuration(uri)
            if (hasPersistedReadPermission(uri)) {
                MediaAccessResult.Persisted(uri, durationMs)
            } else {
                MediaAccessResult.SessionOnly(uri, durationMs, "Media is readable without persisted permission.")
            }
        }.getOrElse { error ->
            MediaAccessResult.Unavailable(uri, error.message ?: "The video cannot be read.")
        }
    }

    private fun readDuration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
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
}
