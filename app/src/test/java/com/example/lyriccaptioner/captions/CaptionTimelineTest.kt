package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionTimelineTest {
    private val first = cue("first", 0L, 1_000L)
    private val second = cue("second", 1_500L, 2_500L)
    private val timeline = CaptionTimeline(listOf(second, first))

    @Test
    fun findsCueAtStartAndBeforeEnd() {
        assertEquals(first, timeline.cueAt(0L))
        assertEquals(first, timeline.cueAt(999L))
        assertEquals(second, timeline.cueAt(1_500L))
    }

    @Test
    fun returnsNullInGapsAndAtExclusiveEnd() {
        assertNull(timeline.cueAt(1_000L))
        assertNull(timeline.cueAt(1_200L))
        assertNull(timeline.cueAt(2_500L))
    }

    private fun cue(id: String, startMs: Long, endMs: Long) = CaptionCue(
        id = id,
        startMs = startMs,
        endMs = endMs,
        english = id,
        chinese = "",
        confidence = 1f,
    )
}
