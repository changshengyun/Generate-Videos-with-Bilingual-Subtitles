package com.example.lyriccaptioner

import android.net.TestUri
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.processing.MediaStoreDestination
import com.example.lyriccaptioner.processing.MediaStoreDestinationStore
import com.example.lyriccaptioner.processing.MediaStoreExportGateway
import com.example.lyriccaptioner.processing.MediaStoreExportSession
import com.example.lyriccaptioner.processing.MediaStoreExportState
import com.example.lyriccaptioner.processing.MediaStoreWritePolicy
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTaskCancellationTest {
    @Test
    fun cancellationAfterSessionCreationBeforePipelineRollsBackAndSettlesState() = runBlocking {
        val store = RecordingStore()
        val sessionRef = AtomicReference<MediaStoreExportSession>()
        val beforePipeline = CompletableDeferred<Unit>()
        var pipelineCalls = 0
        val state = runningState()

        val job = launch {
            executeExportTask(
                beginSession = { gateway(store).begin("before-pipeline").also(sessionRef::set) },
                beforeRender = {
                    beforePipeline.complete(Unit)
                    awaitCancellation()
                },
                render = { pipelineCalls += 1; 0L },
                onRunning = {},
                onSucceeded = { result -> state.set(succeededState(state.get(), result.uri)) },
                onCancelled = { state.set(cancelledState(state.get())) },
                onFailed = { state.set(failedState(state.get())) },
            )
        }
        beforePipeline.await()

        job.cancelAndJoin()

        assertEquals(0, pipelineCalls)
        assertCancelled(store, sessionRef.get(), state.get())
    }

    @Test
    fun cancellationDuringPipelineRollsBackAndSettlesState() = runBlocking {
        val store = RecordingStore()
        val sessionRef = AtomicReference<MediaStoreExportSession>()
        val pipelineStarted = CompletableDeferred<Unit>()
        val state = runningState()

        val job = launch {
            executeExportTask(
                beginSession = { gateway(store).begin("during-pipeline").also(sessionRef::set) },
                beforeRender = {},
                render = {
                    pipelineStarted.complete(Unit)
                    awaitCancellation()
                },
                onRunning = {},
                onSucceeded = { result -> state.set(succeededState(state.get(), result.uri)) },
                onCancelled = { state.set(cancelledState(state.get())) },
                onFailed = { state.set(failedState(state.get())) },
            )
        }
        pipelineStarted.await()

        job.cancelAndJoin()

        assertCancelled(store, sessionRef.get(), state.get())
    }

    @Test
    fun cancellationRacingWithPublishedOutputPreservesSuccessAndDoesNotDelete() = runBlocking {
        val state = runningState()
        lateinit var job: Job
        val store = RecordingStore(onPublish = { job.cancel() })
        val sessionRef = AtomicReference<MediaStoreExportSession>()
        job = launch(start = CoroutineStart.LAZY) {
            executeExportTask(
                beginSession = { gateway(store).begin("publish-race").also(sessionRef::set) },
                beforeRender = {},
                render = { session -> session.openOutput().use { it.write(byteArrayOf(1, 2, 3)) }; 3L },
                onRunning = {},
                onSucceeded = { result -> state.set(succeededState(state.get(), result.uri)) },
                onCancelled = { state.set(cancelledState(state.get())) },
                onFailed = { state.set(failedState(state.get())) },
            )
        }
        job.start()
        job.join()

        assertEquals(MediaStoreExportState.PUBLISHED, sessionRef.get().state)
        assertEquals(0, store.deleteCount)
        assertEquals(ExportState.SUCCEEDED, state.get().exportState)
        assertEquals(sessionRef.get().destination.uri.toString(), state.get().exportUri?.toString())
        assertTrue(!state.get().isWorking)
    }

    @Test
    fun pipelineFailureRollsBackOnceAndSettlesFailedState() = runBlocking {
        val store = RecordingStore()
        val sessionRef = AtomicReference<MediaStoreExportSession>()
        val state = runningState()

        executeExportTask(
            beginSession = { gateway(store).begin("pipeline-failure").also(sessionRef::set) },
            beforeRender = {},
            render = { throw IllegalStateException("render failed") },
            onRunning = {},
            onSucceeded = { result -> state.set(succeededState(state.get(), result.uri)) },
            onCancelled = { state.set(cancelledState(state.get())) },
            onFailed = { state.set(failedState(state.get())) },
        )

        assertEquals(MediaStoreExportState.FAILED, sessionRef.get().state)
        assertEquals(1, store.deleteCount)
        assertTrue(store.pendingRows.isEmpty())
        assertEquals(ExportState.FAILED, state.get().exportState)
        assertTrue(!state.get().isWorking)
        assertNull(state.get().exportUri)
    }

    @Test
    fun cancellationStillSettlesStateWhenPendingRowDeletionFails() = runBlocking {
        val store = RecordingStore(throwOnDelete = true)
        val sessionRef = AtomicReference<MediaStoreExportSession>()
        val pipelineStarted = CompletableDeferred<Unit>()
        val state = runningState()

        val job = launch {
            executeExportTask(
                beginSession = { gateway(store).begin("delete-failure").also(sessionRef::set) },
                beforeRender = {},
                render = {
                    pipelineStarted.complete(Unit)
                    awaitCancellation()
                },
                onRunning = {},
                onSucceeded = { result -> state.set(succeededState(state.get(), result.uri)) },
                onCancelled = { state.set(cancelledState(state.get())) },
                onFailed = { state.set(failedState(state.get())) },
            )
        }
        pipelineStarted.await()

        job.cancelAndJoin()

        assertEquals(MediaStoreExportState.CANCELLED, sessionRef.get().state)
        assertEquals(1, store.deleteCount)
        assertTrue(store.pendingRows.isNotEmpty())
        assertEquals(ExportState.CANCELLED, state.get().exportState)
        assertTrue(!state.get().isWorking)
        assertNull(state.get().exportUri)
    }

    private fun runningState() = AtomicReference(
        EditorState(
            isWorking = true,
            exportUri = TestUri("content://media/stale"),
            exportState = ExportState.RUNNING,
        ),
    )

    private fun cancelledState(state: EditorState) = state.copy(
        isWorking = false,
        exportUri = null,
        exportState = ExportState.CANCELLED,
    )

    private fun failedState(state: EditorState) = state.copy(
        isWorking = false,
        exportUri = null,
        exportState = ExportState.FAILED,
    )

    private fun succeededState(state: EditorState, uri: android.net.Uri) = state.copy(
        isWorking = false,
        exportUri = uri,
        exportState = ExportState.SUCCEEDED,
    )

    private fun assertCancelled(
        store: RecordingStore,
        session: MediaStoreExportSession,
        state: EditorState,
    ) {
        assertEquals(MediaStoreExportState.CANCELLED, session.state)
        assertEquals(1, store.deleteCount)
        assertTrue(store.pendingRows.isEmpty())
        assertEquals(ExportState.CANCELLED, state.exportState)
        assertTrue(!state.isWorking)
        assertNull(state.exportUri)
    }

    private fun gateway(store: RecordingStore) =
        MediaStoreExportGateway(store, MediaStoreWritePolicy(apiLevel = 36, hasLegacyWritePermission = false))

    private class RecordingStore(
        private val onPublish: () -> Unit = {},
        private val throwOnDelete: Boolean = false,
    ) : MediaStoreDestinationStore {
        val pendingRows = linkedMapOf<String, ByteArrayOutputStream>()
        var deleteCount = 0

        override fun insertVideo(displayName: String, policy: MediaStoreWritePolicy): MediaStoreDestination {
            val owner = "owner-${pendingRows.size + 1}"
            return MediaStoreDestination(
                uri = TestUri("content://media/$owner"),
                ownerToken = owner,
                displayName = displayName,
                usesPendingRow = policy.usesPendingRows,
            ).also { pendingRows[owner] = ByteArrayOutputStream() }
        }

        override fun openOutput(destination: MediaStoreDestination): OutputStream? =
            pendingRows[destination.ownerToken]

        override fun sizeBytes(destination: MediaStoreDestination): Long? =
            pendingRows[destination.ownerToken]?.size()?.toLong()

        override fun publish(destination: MediaStoreDestination) {
            check(pendingRows.containsKey(destination.ownerToken))
            onPublish()
        }

        override fun delete(destination: MediaStoreDestination) {
            deleteCount += 1
            if (throwOnDelete) error("delete failed")
            pendingRows.remove(destination.ownerToken)
        }
    }
}
