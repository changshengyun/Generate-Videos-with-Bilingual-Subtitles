package com.example.lyriccaptioner.processing

import android.net.TestUri
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreExportGatewayTest {
    @Test
    fun api29And36UsePendingRowsAndUniqueMp4Names() {
        listOf(29, 36).forEach { api ->
            val store = FakeStore()
            val gateway = MediaStoreExportGateway(store, MediaStoreWritePolicy(api, false))
            val first = gateway.begin("task-1")
            val second = gateway.begin("task-2")
            assertTrue(store.insertedPolicies.any { it.apiLevel == api && it.usesPendingRows })
            assertTrue(first.destination.displayName.endsWith(".mp4"))
            assertFalse(first.destination.displayName == second.destination.displayName)
        }
    }

    @Test
    fun api26And28RequireLegacyPermissionOnlyOnThoseApis() {
        listOf(26, 28).forEach { api ->
            assertThrows(LegacyStoragePermissionRequired::class.java) {
                MediaStoreExportGateway(FakeStore(), MediaStoreWritePolicy(api, false)).begin("legacy")
            }
            MediaStoreExportGateway(FakeStore(), MediaStoreWritePolicy(api, true)).begin("legacy")
        }
        listOf(29, 36).forEach { api ->
            MediaStoreExportGateway(FakeStore(), MediaStoreWritePolicy(api, false)).begin("modern")
        }
    }

    @Test
    fun completeWriteValidatesBytesBeforePublishing() {
        val store = FakeStore()
        val session = MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false)).begin("ok")
        session.openOutput().use { it.write(byteArrayOf(1, 2, 3)) }
        val result = session.publish()
        assertEquals(MediaStoreExportState.PUBLISHED, session.state)
        assertEquals(3L, result.sizeBytes)
        assertEquals(1, store.publishCount)
        assertTrue(store.pendingRows.isNotEmpty())
    }

    @Test
    fun emptyWriteRollsBackOnlyTaskOwnedRow() {
        val store = FakeStore()
        val existing = MediaStoreDestination(
            uri = TestUri("content://media/existing"),
            ownerToken = "existing-owner",
            displayName = "existing.mp4",
            usesPendingRow = true,
        )
        store.pendingRows[existing.ownerToken] = ByteArrayOutputStream().apply { write(9) }
        val session = MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false)).begin("empty")
        session.openOutput().close()
        assertThrows(IllegalStateException::class.java) { session.publish() }
        assertEquals(MediaStoreExportState.FAILED, session.state)
        assertEquals(1, store.deleteCount)
        assertTrue(store.pendingRows.containsKey(existing.ownerToken))
        assertEquals(1, store.pendingRows.size)
        session.rollback()
        session.cancel()
        assertEquals(1, store.deleteCount)
    }

    @Test
    fun cancellationRollbackIsIdempotentAndCannotDeletePublishedOutput() {
        val store = FakeStore()
        val cancelled = MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false)).begin("cancel")
        cancelled.openOutput().use { it.write(byteArrayOf(7)) }
        cancelled.cancel()
        cancelled.cancel()
        cancelled.rollback()
        assertEquals(MediaStoreExportState.CANCELLED, cancelled.state)
        assertEquals(1, store.deleteCount)

        val published = MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false)).begin("published")
        published.openOutput().use { it.write(byteArrayOf(7)) }
        published.publish()
        published.cancel()
        published.rollback()
        assertEquals(MediaStoreExportState.PUBLISHED, published.state)
        assertEquals(1, store.deleteCount)
    }

    @Test
    fun insertFailureDoesNotCreateSession() {
        val store = FakeStore(shouldFailInsert = true)
        assertThrows(IllegalStateException::class.java) {
            MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false)).begin("insert-fail")
        }
        assertEquals(0, store.deleteCount)
    }

    @Test
    fun unsupportedApiIsRejectedAndExactSourceDestinationIsNeverExported() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaStoreWritePolicy(25, hasLegacyWritePermission = true)
        }
        val store = FakeStore(fixedUri = TestUri("content://same"))
        assertThrows(IllegalArgumentException::class.java) {
            MediaStoreExportGateway(store, MediaStoreWritePolicy(36, false))
                .begin("same", sourceUri = TestUri("content://same"))
        }
        assertEquals(1, store.deleteCount)
    }

    private class FakeStore(
        private val insertResult: MediaStoreDestination? = null,
        private val fixedUri: android.net.Uri? = null,
        private val shouldFailInsert: Boolean = false,
    ) : MediaStoreDestinationStore {
        val insertedPolicies = mutableListOf<MediaStoreWritePolicy>()
        val pendingRows = linkedMapOf<String, ByteArrayOutputStream>()
        var publishCount = 0
        var deleteCount = 0

        override fun insertVideo(displayName: String, policy: MediaStoreWritePolicy): MediaStoreDestination? {
            insertedPolicies += policy
            if (shouldFailInsert) return null
            val destination = insertResult ?: MediaStoreDestination(
                uri = fixedUri ?: TestUri("content://media/${pendingRows.size + 1}"),
                ownerToken = "owner-${pendingRows.size + 1}",
                displayName = displayName,
                usesPendingRow = policy.usesPendingRows,
            )
            pendingRows[destination.ownerToken] = ByteArrayOutputStream()
            return destination
        }

        override fun openOutput(destination: MediaStoreDestination): OutputStream? {
            val bytes = pendingRows[destination.ownerToken] ?: return null
            return bytes
        }

        override fun sizeBytes(destination: MediaStoreDestination): Long? = pendingRows[destination.ownerToken]?.size()?.toLong()

        override fun publish(destination: MediaStoreDestination) {
            check(pendingRows.containsKey(destination.ownerToken))
            publishCount++
        }

        override fun delete(destination: MediaStoreDestination) {
            deleteCount++
            pendingRows.remove(destination.ownerToken)
        }
    }
}
