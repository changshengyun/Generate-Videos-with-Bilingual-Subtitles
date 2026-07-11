package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionTimingEditorTest {
    private val editor = CaptionTimingEditor(minimumDurationMs = 100L)
    private val cue = CaptionCue(
        id = "cue",
        startMs = 1_000L,
        endMs = 2_000L,
        english = "Line",
        chinese = "",
        confidence = 0.9f,
    )

    @Test
    fun startCannotBecomeNegativeOrReachEnd() {
        assertEquals(0L, editor.shiftStart(cue, -2_000L).startMs)
        assertEquals(1_900L, editor.shiftStart(cue, 2_000L).startMs)
    }

    @Test
    fun endKeepsMinimumDurationAndDoesNotExceedVideo() {
        assertEquals(1_100L, editor.shiftEnd(cue, -2_000L, 3_000L).endMs)
        assertEquals(3_000L, editor.shiftEnd(cue, 2_000L, 3_000L).endMs)
    }

    @Test
    fun endCanGrowWhenVideoDurationIsUnknown() {
        assertEquals(4_000L, editor.shiftEnd(cue, 2_000L, null).endMs)
    }

    @Test
    fun timingCannotOverlapNeighboringCues() {
        assertEquals(900L, editor.shiftStart(cue, -500L, earliestStartMs = 900L).startMs)
        assertEquals(
            2_100L,
            editor.shiftEnd(cue, 500L, videoDurationMs = 3_000L, latestEndMs = 2_100L).endMs,
        )
    }
}
