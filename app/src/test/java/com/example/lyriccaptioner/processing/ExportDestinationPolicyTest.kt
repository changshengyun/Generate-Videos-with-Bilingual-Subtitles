package com.example.lyriccaptioner.processing

import android.net.TestUri
import org.junit.Assert.assertFalse
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
}
