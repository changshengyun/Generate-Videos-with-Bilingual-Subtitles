package com.example.lyriccaptioner.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportLifecycleTest {
    @Test
    fun cancellationStopsBeforePublishAndCleansTaskOwnedRow() {
        val task = ExportLifecycle.begin("task-1", "content://source/video", "content://output/1")
        task.markRunning()

        assertEquals(ExportLifecycleState.CANCELLING, task.requestCancel().state)
        assertEquals(ExportLifecycleState.CANCELLED, task.completeCancel().state)
        assertTrue(task.snapshot().cleanupRequired)
        assertEquals(ExportLifecycleState.CANCELLED, task.publish().state)
    }

    @Test
    fun everyFailureStageIsTerminalAndRequestsCleanup() {
        ExportFailureStage.values().forEach { stage ->
            val task = ExportLifecycle.begin("task-$stage", "content://source/video", "content://output/$stage")
            assertEquals(ExportLifecycleState.FAILED, task.fail(stage).state)
            assertEquals(stage, task.snapshot().failureStage)
            assertTrue(task.snapshot().cleanupRequired)
            assertEquals(ExportLifecycleState.FAILED, task.fail(ExportFailureStage.PUBLISH).state)
        }
    }

    @Test
    fun duplicateTerminalTransitionsDoNotResurrectOrDoubleCommit() {
        val task = ExportLifecycle.begin("task-2", "content://source/video", "content://output/2")
        task.markRunning()
        task.publish()

        val published = task.snapshot()
        assertEquals(published, task.publish())
        assertEquals(published, task.requestCancel())
        assertFalse(task.snapshot().cleanupRequired)
    }

    @Test
    fun oneActiveSlotRejectsConcurrentTaskAndReleasesAfterTerminalState() {
        val slot = SingleExportSlot()
        val first = slot.start("task-3", "content://source/one", "content://output/3")
        first.markRunning()

        val rejected = runCatching {
            slot.start("task-4", "content://source/two", "content://output/4")
        }.exceptionOrNull()
        assertTrue(rejected is IllegalStateException)

        first.fail(ExportFailureStage.ENCODE)
        val second = slot.start("task-4", "content://source/two", "content://output/4")
        assertEquals("task-4", second.snapshot().taskId)
    }

    @Test
    fun sameSourceDestinationIsRejectedBeforeAnyTaskRowExists() {
        val rejected = runCatching {
            ExportLifecycle.begin("task-5", "content://source/same", "content://source/same")
        }.exceptionOrNull()
        assertTrue(rejected is IllegalArgumentException)
    }

    @Test
    fun relinkChangesOnlySourceAndPreservesProjectCaptionAndStyleRevisions() {
        val before = ExportRelinkSnapshot(
            sourceUri = "content://source/old",
            captionRevision = 7L,
            styleRevision = 9L,
            projectRevision = 11L,
        )
        val after = ExportLifecycle.relink(before, "content://source/new")

        assertEquals("content://source/new", after.sourceUri)
        assertEquals(before.captionRevision, after.captionRevision)
        assertEquals(before.styleRevision, after.styleRevision)
        assertEquals(before.projectRevision, after.projectRevision)
    }

    @Test
    fun privacyLogExcludesSourceDestinationAndMediaDetails() {
        val source = "content://private.provider/document/secret-video"
        val destination = "content://media/external/video/media/42"
        val task = ExportLifecycle.begin("task-private", source, destination)
        val log = task.snapshot().privacyLogLine()

        assertFalse(log.contains(source))
        assertFalse(log.contains(destination))
        assertFalse(log.contains("secret-video"))
        assertTrue(log.contains("task-private"))
        assertTrue(log.contains("PENDING"))
    }

    @Test
    fun invalidTaskIdentityIsRejectedWithoutCreatingLifecycle() {
        assertNotEquals(
            null,
            runCatching { ExportLifecycle.begin("task-6", "content://source", "content://output") }.getOrNull(),
        )
        assertTrue(runCatching { ExportLifecycle.begin("", "content://source", "content://output") }.isFailure)
        assertTrue(runCatching { ExportLifecycle.begin("task-7", "", "content://output") }.isFailure)
        assertTrue(runCatching { ExportLifecycle.begin("task-8", "content://source", "") }.isFailure)
    }
}
