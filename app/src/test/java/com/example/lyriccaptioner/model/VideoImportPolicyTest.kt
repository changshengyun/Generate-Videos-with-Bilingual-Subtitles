package com.example.lyriccaptioner.model

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class VideoImportPolicyTest {
    private val existingCue = CaptionCue(
        id = "cue-1",
        startMs = 100L,
        endMs = 1_000L,
        english = "Hello",
        chinese = "你好",
        confidence = 1f,
        confirmed = true,
    )

    private fun existingState() = EditorState(
        videoUri = TestUri("old-video"),
        videoDurationMs = 2_000L,
        mediaState = MediaState.PERSISTED,
        captions = listOf(existingCue),
        selectedCaptionId = existingCue.id,
        exportProfile = ExportProfile(subtitleStyle = SubtitleStyle(fontSizeSp = 30)),
        exportUri = TestUri("old-export"),
        pendingSidecarSrt = "old-sidecar",
    )

    @Test
    fun durationMustBeKnownPositiveAndWithinFiveMinutes() {
        assertTrue(VideoImportPolicy.isDurationAllowed(1L, 5 * 60 * 1_000L))
        assertTrue(!VideoImportPolicy.isDurationAllowed(null, 5 * 60 * 1_000L))
        assertTrue(!VideoImportPolicy.isDurationAllowed(0L, 5 * 60 * 1_000L))
        assertTrue(!VideoImportPolicy.isDurationAllowed(5 * 60 * 1_000L + 1L, 5 * 60 * 1_000L))
    }

    @Test
    fun newVideoClearsPreviousCaptionsAndDerivedOutputs() {
        val result = VideoImportPolicy.apply(
            current = existingState(),
            uri = TestUri("new-video"),
            durationMs = 3_000L,
            mediaState = MediaState.PERSISTED,
            mode = VideoImportMode.NEW_VIDEO,
            status = "imported",
        )

        assertEquals("new-video", result.videoUri?.toString())
        assertTrue(result.captions.isEmpty())
        assertNull(result.selectedCaptionId)
        assertNull(result.exportUri)
        assertNull(result.pendingSidecarSrt)
    }

    @Test
    fun relinkPreservesCaptionsStyleAndConfirmationButInvalidatesOutputs() {
        val result = VideoImportPolicy.apply(
            current = existingState(),
            uri = TestUri("replacement-video"),
            durationMs = 2_000L,
            mediaState = MediaState.PERSISTED,
            mode = VideoImportMode.RELINK,
            status = "relinked",
        )

        assertEquals(listOf(existingCue), result.captions)
        assertEquals(existingCue.id, result.selectedCaptionId)
        assertEquals(30, result.exportProfile.subtitleStyle.fontSizeSp)
        assertTrue(result.captions.single().confirmed)
        assertNull(result.exportUri)
        assertNull(result.pendingSidecarSrt)
        assertTrue(!result.requiresVideoAssociation)
    }

    @Test
    fun successfulAssociationClearsExplicitPendingVideoAssociation() {
        val pending = existingState().copy(
            videoUri = null,
            mediaState = MediaState.NONE,
            requiresVideoAssociation = true,
        )

        val result = VideoImportPolicy.apply(
            current = pending,
            uri = TestUri("associated-video"),
            durationMs = 2_000L,
            mediaState = MediaState.PERSISTED,
            mode = VideoImportMode.RELINK,
            status = "relinked",
        )

        assertTrue(!result.requiresVideoAssociation)
        assertEquals(listOf(existingCue), result.captions)
    }
}
