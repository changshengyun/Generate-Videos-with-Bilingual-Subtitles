package com.example.lyriccaptioner.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Media3SubtitleExporterObservabilityTest {
    @Test
    fun sanitizeMedia3DiagnosticMessage_redactsUrisAndAbsolutePaths() {
        val message = "Failed file:///storage/emulated/0/video.mp4 at C:\\Users\\name\\clip.mp4 or /data/user/0/cache/out.mp4"

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains("storage/emulated"))
        assertFalse(sanitized.contains("Users"))
        assertFalse(sanitized.contains("data/user"))
        assertEquals(
            "Failed <redacted-uri> at <redacted-path> or <redacted-path>",
            sanitized,
        )
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_redactsSpaceContainingQuotedAndUnquotedPaths() {
        val message = "failed C:\\Users\\Jane Doe\\clip.mp4 or /storage/emulated/0/My Video/clip.mp4"

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains("Jane Doe"))
        assertFalse(sanitized.contains("My Video"))
        assertFalse(sanitized.contains("clip.mp4"))
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_redactsQuotedAndUncPaths() {
        val message = "failed \"C:\\Users\\Jane Doe\\clip.mp4\" and '\\\\server\\share\\My Clip.mp4'"

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains("Jane Doe"))
        assertFalse(sanitized.contains("server\\share"))
        assertFalse(sanitized.contains("My Clip.mp4"))
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_redactsUnquotedUriAndDirectoryPath() {
        val message = "failed file:///storage/My Video/clip.mp4 or C:\\Users\\Jane Doe\\cache"

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains("My Video"))
        assertFalse(sanitized.contains("Jane Doe"))
        assertFalse(sanitized.contains("cache"))
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_redactsUnquotedUriWithoutExtension() {
        val message = "failed file:///storage/My Video or next diagnostic"

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains("My Video"))
        assert(sanitized.contains("or next diagnostic"))
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_normalizesControlsEscapesQuotesAndBoundsLength() {
        val message = "Video frame processing \\\"error\\\"\n\u0000\u000B" + "x".repeat(300)

        val sanitized = sanitizeMedia3DiagnosticMessage(message)

        assertFalse(sanitized.contains('\n'))
        assertFalse(sanitized.contains('\r'))
        assertEquals(256, sanitized.length)
        assertFalse(sanitized.contains("\"error\""))
        assertFalse(sanitized.contains('\u0000'))
        assertFalse(sanitized.contains('\u000B'))
        assertFalse(sanitized.endsWith("\\"))
    }

    @Test
    fun sanitizeMedia3DiagnosticMessage_handlesMissingMessage() {
        assertEquals("<none>", sanitizeMedia3DiagnosticMessage(null))
        assertFalse(sanitizeMedia3DiagnosticMessage("trailing\\").endsWith("\\"))
    }
}
