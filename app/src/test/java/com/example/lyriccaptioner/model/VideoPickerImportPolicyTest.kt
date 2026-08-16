package com.example.lyriccaptioner.model

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPickerImportPolicyTest {
    private val oldUri = TestUri("content://media/old")
    private val newUri = TestUri("content://media/new")
    private val cue = CaptionCue(
        id = "cue-1",
        startMs = 100L,
        endMs = 900L,
        english = "hello",
        chinese = "你好",
        confidence = 1f,
        confirmed = true,
    )

    private fun state() = EditorState(
        videoUri = oldUri,
        videoDurationMs = 2_000L,
        mediaState = MediaState.PERSISTED,
        captions = listOf(cue),
        selectedCaptionId = cue.id,
        exportUri = TestUri("content://media/export"),
    )

    @Test
    fun requestIsSingleVisualMediaVideoSelection() {
        assertEquals(VideoPickerContract.PICK_VISUAL_MEDIA, VideoPickerImportPolicy.request().contract)
        assertEquals(VideoPickerMediaType.VIDEO_ONLY, VideoPickerImportPolicy.request().mediaType)
        assertTrue(!VideoPickerImportPolicy.request().allowMultiple)
    }

    @Test
    fun nullPickerResultIsNoOpAndPreservesEveryProjectField() {
        val current = state()
        val result = VideoPickerImportPolicy.decide(
            current = current,
            uri = null,
            mode = VideoImportMode.NEW_VIDEO,
            access = null,
        )

        assertTrue(result is VideoPickerImportDecision.Cancelled)
        assertSame(current, result.state)
    }

    @Test
    fun persistedSessionAndProviderUnsupportedAreDistinctAcceptedStates() {
        val current = state()
        val persisted = VideoPickerImportPolicy.decide(
            current, newUri, VideoImportMode.RELINK,
            VideoUriAccess(VideoUriAccessStatus.PERSISTED, true, true, 3_000L),
        ) as VideoPickerImportDecision.Accepted
        val session = VideoPickerImportPolicy.decide(
            current, newUri, VideoImportMode.RELINK,
            VideoUriAccess(VideoUriAccessStatus.SESSION_ONLY, true, true, 3_000L),
        ) as VideoPickerImportDecision.Accepted
        val unsupported = VideoPickerImportPolicy.decide(
            current, newUri, VideoImportMode.RELINK,
            VideoUriAccess(VideoUriAccessStatus.PROVIDER_UNSUPPORTED, true, true, 3_000L),
        ) as VideoPickerImportDecision.Accepted

        assertEquals(VideoUriAccessStatus.PERSISTED, persisted.access)
        assertEquals(MediaState.PERSISTED, persisted.state.mediaState)
        assertEquals(VideoUriAccessStatus.SESSION_ONLY, session.access)
        assertEquals(MediaState.SESSION_ONLY, session.state.mediaState)
        assertEquals(VideoUriAccessStatus.PROVIDER_UNSUPPORTED, unsupported.access)
        assertEquals(MediaState.PROVIDER_UNSUPPORTED, unsupported.state.mediaState)
    }

    @Test
    fun unavailableUnreadableAndNonVideoMediaAreRejectedWithoutMutation() {
        val current = state()
        val cases = listOf(
            VideoUriAccess(VideoUriAccessStatus.UNAVAILABLE, false, false, null) to VideoImportRejection.UNAVAILABLE,
            VideoUriAccess(VideoUriAccessStatus.SESSION_ONLY, false, true, 3_000L) to VideoImportRejection.UNREADABLE,
            VideoUriAccess(VideoUriAccessStatus.SESSION_ONLY, true, false, 3_000L) to VideoImportRejection.NO_VIDEO_TRACK,
        )

        cases.forEach { (access, expected) ->
            val result = VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.NEW_VIDEO, access)
            assertTrue(result is VideoPickerImportDecision.Rejected)
            result as VideoPickerImportDecision.Rejected
            assertSame(current, result.state)
            assertEquals(expected, result.reason)
        }
    }

    @Test
    fun unknownInvalidAndOverLimitDurationsAreRejected() {
        val current = state()
        val common = { duration: Long? ->
            VideoUriAccess(VideoUriAccessStatus.SESSION_ONLY, true, true, duration)
        }
        assertEquals(
            VideoImportRejection.UNKNOWN_DURATION,
            (VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.NEW_VIDEO, common(null)) as VideoPickerImportDecision.Rejected).reason,
        )
        assertEquals(
            VideoImportRejection.INVALID_DURATION,
            (VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.NEW_VIDEO, common(0L)) as VideoPickerImportDecision.Rejected).reason,
        )
        assertEquals(
            VideoImportRejection.TOO_LONG,
            (VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.NEW_VIDEO, common(VideoPickerImportPolicy.MAX_VIDEO_DURATION_MS + 1L)) as VideoPickerImportDecision.Rejected).reason,
        )
    }

    @Test
    fun newVideoClearsCaptionsButRelinkKeepsProjectEditingState() {
        val current = state()
        val access = VideoUriAccess(VideoUriAccessStatus.PERSISTED, true, true, 4_000L)
        val newResult = VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.NEW_VIDEO, access)
            as VideoPickerImportDecision.Accepted
        val relinkResult = VideoPickerImportPolicy.decide(current, newUri, VideoImportMode.RELINK, access)
            as VideoPickerImportDecision.Accepted

        assertTrue(newResult.state.captions.isEmpty())
        assertEquals(listOf(cue), relinkResult.state.captions)
        assertEquals(cue.id, relinkResult.state.selectedCaptionId)
        assertEquals(null, newResult.state.exportUri)
        assertEquals(null, relinkResult.state.exportUri)
    }
}
