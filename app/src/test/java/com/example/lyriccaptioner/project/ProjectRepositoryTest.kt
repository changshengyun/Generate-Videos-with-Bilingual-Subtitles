package com.example.lyriccaptioner.project

import android.net.Uri
import android.net.TestUri
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRepositoryTest {
    @Test
    fun repositoryContractCarriesSnapshotDestinationAndMediaStatus() = runBlocking {
        val repository = RecordingProjectRepository()
        val snapshot = ProjectSnapshot(null, null, emptyList(), ExportProfile())
        val destination = TestUri("content://projects/new.lcp")

        val save = repository.save(snapshot, destination)
        val load = repository.load(destination)

        assertTrue(save is ProjectSaveResult.Success)
        assertSame(destination, (save as ProjectSaveResult.Success).destinationUri)
       assertEquals(snapshot, (load as ProjectLoadResult.Success).snapshot)
        assertSame(destination, load.mediaAccess.uri)
        assertEquals(null, load.mediaAccess.durationMs)
        assertEquals(snapshot, repository.lastSavedSnapshot)
        assertSame(destination, repository.lastDestination)
    }

    @Test
    fun repositoryFailureResultIsExplicitAndDoesNotLookLikeSuccess() = runBlocking {
        val expected = ProjectRepositoryError(ProjectErrorKind.PERMISSION, "denied")
        val repository = RecordingProjectRepository(saveResult = ProjectSaveResult.Failure(expected))

        val result = repository.save(ProjectSnapshot(null, null, emptyList(), ExportProfile()), TestUri("content://denied"))

        assertSame(expected, (result as ProjectSaveResult.Failure).error)
    }

    private class RecordingProjectRepository(
        private val saveResult: ProjectSaveResult? = null,
    ) : ProjectRepository {
        var lastSavedSnapshot: ProjectSnapshot? = null
        var lastDestination: Uri? = null

        override suspend fun save(snapshot: ProjectSnapshot, destinationUri: Uri): ProjectSaveResult {
            lastSavedSnapshot = snapshot
            lastDestination = destinationUri
            return saveResult ?: ProjectSaveResult.Success(destinationUri)
        }

       override suspend fun load(sourceUri: Uri): ProjectLoadResult = ProjectLoadResult.Success(
           sourceUri = sourceUri,
           snapshot = lastSavedSnapshot ?: ProjectSnapshot(null, null, emptyList(), ExportProfile()),
            mediaAccess = MediaAccessResult.Persisted(sourceUri, null),
       )

        override fun retainMediaReadAccess(uri: Uri): MediaAccessResult =
            MediaAccessResult.Persisted(uri, null)

        override fun validateMediaAccess(uri: Uri): MediaAccessResult =
            MediaAccessResult.Persisted(uri, null)
    }
}
