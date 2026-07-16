package com.example.lyriccaptioner.project

import android.net.Uri

enum class ProjectErrorKind {
    INVALID_INPUT,
    READ,
    WRITE,
    FORMAT,
    PERMISSION,
    MEDIA,
    UNKNOWN,
}

data class ProjectRepositoryError(
    val kind: ProjectErrorKind,
    val message: String,
    val cause: Throwable? = null,
)

sealed class ProjectSaveResult {
    data class Success(val destinationUri: Uri) : ProjectSaveResult()
    data class Failure(val error: ProjectRepositoryError) : ProjectSaveResult()
}

sealed class ProjectLoadResult {
    data class Success(
        val sourceUri: Uri,
        val snapshot: com.example.lyriccaptioner.model.ProjectSnapshot,
        val mediaAccess: MediaAccessResult,
    ) : ProjectLoadResult()
    data class Failure(val error: ProjectRepositoryError) : ProjectLoadResult()
}

sealed class MediaAccessResult(open val uri: Uri, open val durationMs: Long?) {
    data class Persisted(override val uri: Uri, override val durationMs: Long?) : MediaAccessResult(uri, durationMs)
    data class SessionOnly(
        override val uri: Uri,
        override val durationMs: Long?,
        val reason: String,
    ) : MediaAccessResult(uri, durationMs)
    data class ProviderUnsupported(
        override val uri: Uri,
        override val durationMs: Long?,
        val reason: String,
    ) : MediaAccessResult(uri, durationMs)
    data class Unavailable(
        override val uri: Uri,
        val reason: String,
    ) : MediaAccessResult(uri, null)

    val isReadable: Boolean
        get() = this !is Unavailable
}

interface ProjectRepository {
    suspend fun save(
        snapshot: com.example.lyriccaptioner.model.ProjectSnapshot,
        destinationUri: Uri,
    ): ProjectSaveResult

    suspend fun load(sourceUri: Uri): ProjectLoadResult

    fun retainMediaReadAccess(uri: Uri): MediaAccessResult

    fun validateMediaAccess(uri: Uri): MediaAccessResult
}
