package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionInsertionPolicyTest {
    @Test
    fun insertsAtBeginningUsingTheWholeLeadingGap() {
        val updated = state().insertCaptionAt(500L, "manual-start")

        assertInserted(updated, "manual-start", 0L, 1_000L)
    }

    @Test
    fun insertsBetweenCuesUsingTheWholeMiddleGap() {
        val updated = state().insertCaptionAt(2_500L, "manual-middle")

        assertInserted(updated, "manual-middle", 2_000L, 3_000L)
    }

    @Test
    fun insertsAtEndUsingTheGapToVideoDuration() {
        val updated = state().insertCaptionAt(4_500L, "manual-end")

        assertInserted(updated, "manual-end", 4_000L, 5_000L)
    }

    @Test
    fun exactCueStartIsInsideButExactCueEndCanUseTheFollowingGap() {
        val inside = state().insertCaptionAt(1_000L, "rejected")
        val boundaryGap = state().insertCaptionAt(2_000L, "allowed")

        assertEquals(2, inside.captions.size)
        assertTrue(inside.status.contains("当前位置已有字幕"))
        assertInserted(boundaryGap, "allowed", 2_000L, 3_000L)
    }

    @Test
    fun rejectsPositionInsideCueAndKeepsDerivedOutputState() {
        val original = state().copy(exportState = ExportState.SUCCEEDED)

        val updated = original.insertCaptionAt(1_500L, "rejected")

        assertEquals(original.captions, updated.captions)
        assertEquals(ExportState.SUCCEEDED, updated.exportState)
        assertTrue(updated.status.contains("当前位置已有字幕"))
    }

    @Test
    fun rejectsAnyAdjacentOverlap() {
        val overlapping = state().copy(
            captions = listOf(
                cue("first", 1_000L, 2_500L),
                cue("second", 2_000L, 4_000L),
            ),
        )

        val updated = overlapping.insertCaptionAt(500L, "rejected")

        assertEquals(overlapping.captions, updated.captions)
        assertTrue(updated.status.contains("存在重叠"))
    }

    @Test
    fun rejectsGapShorterThanOneHundredMilliseconds() {
        val shortLeadingGap = state().copy(
            captions = listOf(cue("first", 99L, 1_000L)),
        )

        val updated = shortLeadingGap.insertCaptionAt(0L, "rejected")

        assertEquals(1, updated.captions.size)
        assertTrue(updated.status.contains("不足 100ms"))
    }

    @Test
    fun rejectsUnknownDurationAndEmptyCaptionList() {
        val unknownDuration = state().copy(videoDurationMs = null)
            .insertCaptionAt(500L, "rejected-duration")
        val empty = state().copy(captions = emptyList())
            .insertCaptionAt(500L, "rejected-empty")

        assertTrue(unknownDuration.status.contains("视频总时长未知"))
        assertTrue(empty.status.contains("请先生成字幕"))
    }

    @Test
    fun successfulInsertionSelectsIndependentCueSortsAndInvalidatesExport() {
        val original = state().copy(
            captions = state().captions.reversed(),
            exportState = ExportState.SUCCEEDED,
        )

        val updated = original.insertCaptionAt(2_500L, "manual-independent")
        val inserted = updated.captions.single { it.id == "manual-independent" }

        assertEquals("manual-independent", updated.selectedCaptionId)
        assertNotEquals(original.captions.first().id, inserted.id)
        assertEquals(listOf("first", "manual-independent", "second"), updated.captions.map { it.id })
        assertEquals("", inserted.english)
        assertEquals("", inserted.chinese)
        assertEquals(ExportState.IDLE, updated.exportState)
    }

    private fun assertInserted(state: EditorState, id: String, startMs: Long, endMs: Long) {
        val cue = state.captions.single { it.id == id }
        assertEquals(startMs, cue.startMs)
        assertEquals(endMs, cue.endMs)
        assertEquals(id, state.selectedCaptionId)
    }

    private fun state() = EditorState(
        videoDurationMs = 5_000L,
        captions = listOf(
            cue("first", 1_000L, 2_000L),
            cue("second", 3_000L, 4_000L),
        ),
    )

    private fun cue(id: String, startMs: Long, endMs: Long) = CaptionCue(
        id = id,
        startMs = startMs,
        endMs = endMs,
        english = "line-$id",
        chinese = "字幕-$id",
        confidence = 0.9f,
    )
}
