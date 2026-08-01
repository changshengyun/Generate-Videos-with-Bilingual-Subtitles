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
}
