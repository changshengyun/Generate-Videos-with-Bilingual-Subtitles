package com.example.lyriccaptioner.processing

import android.net.TestUri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDestinationPolicyTest {
    @Test
    fun exactSourceUriIsRejectedAsExportDestination() {
        val source = TestUri("content://provider/document/source")
        assertTrue(ExportDestinationPolicy.isSameDocument(source, source))
    }

    @Test
    fun differentUriIsNotRejectedAsSameDocument() {
        assertFalse(
            ExportDestinationPolicy.isSameDocument(
                TestUri("content://provider/document/source"),
                TestUri("content://provider/document/output"),
            ),
        )
    }

    @Test
    fun sameUnderlyingCrossProviderFileIdentityIsRejected() {
        assertTrue(
            ExportDestinationPolicy.sameOpenFileIdentity(
                OpenFileIdentity(device = 8L, inode = 42L),
                OpenFileIdentity(device = 8L, inode = 42L),
            ),
        )
    }

    @Test
    fun differentCrossProviderFileIdentityIsAllowed() {
        assertFalse(
            ExportDestinationPolicy.sameOpenFileIdentity(
                OpenFileIdentity(device = 8L, inode = 42L),
                OpenFileIdentity(device = 8L, inode = 43L),
            ),
        )
    }

    @Test
    fun destinationQueryWithExistingRowIsNeverWritableByThisTask() {
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyDocumentQuery(true),
        )
        val error = runCatching {
            ExportDestinationPolicy.requireNewDestination(ExportDestinationState.EXISTING)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun destinationQueryWithoutRowIsNewButUnknownIsRejected() {
        assertEquals(
            ExportDestinationState.NEW,
            ExportDestinationPolicy.classifyDocumentQuery(false),
        )
        assertEquals(
            ExportDestinationState.UNKNOWN,
            ExportDestinationPolicy.classifyDocumentQuery(null),
        )
    }

    @Test
    fun zeroByteCreateDocumentResultIsTaskOwnedButNonEmptySentinelIsNot() {
        assertEquals(
            ExportDestinationState.NEW,
            ExportDestinationPolicy.classifyDocumentQuery(true, 0L),
        )
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyDocumentQuery(true, 17L),
        )
    }

    @Test
    fun pendingEmptyMediaStoreRowIsTaskOwned() {
        assertEquals(
            ExportDestinationState.NEW,
            ExportDestinationPolicy.classifyMediaStoreQuery(true, null, 1),
        )
        assertEquals(
            ExportDestinationState.NEW,
            ExportDestinationPolicy.classifyMediaStoreQuery(true, 0L, 1),
        )
    }

    @Test
    fun pendingPopulatedOrPublishedMediaStoreRowIsExisting() {
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyMediaStoreQuery(true, 17L, 1),
        )
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyMediaStoreQuery(true, null, 0),
        )
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyMediaStoreQuery(true, 17L, 0),
        )
    }

    @Test
    fun missingOrUnknownMediaStoreRowStaysDistinct() {
        assertEquals(
            ExportDestinationState.NEW,
            ExportDestinationPolicy.classifyMediaStoreQuery(false, null, null),
        )
        assertEquals(
            ExportDestinationState.UNKNOWN,
            ExportDestinationPolicy.classifyMediaStoreQuery(null, null, null),
        )
    }

    @Test
    fun ordinaryDocumentWithNullSizeRemainsFailClosed() {
        assertEquals(
            ExportDestinationState.EXISTING,
            ExportDestinationPolicy.classifyDocumentQuery(true, null),
        )
    }
}
