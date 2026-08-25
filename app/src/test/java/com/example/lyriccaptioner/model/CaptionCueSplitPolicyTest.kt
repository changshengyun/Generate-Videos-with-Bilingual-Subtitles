package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionCueSplitPolicyTest {
    @Test
    fun twoLinesCreateStableChildrenWithPreferredGapAndMinimumDurations() {
        val parent = cue(startMs = 1_000L, endMs = 4_000L)

        val result = CaptionCueSplitPolicy.apply(
            parent,
            listOf(
                CaptionSplitLine("short line", "短句"),
                CaptionSplitLine("a considerably longer line", "较长的句子"),
            ),
        )

        assertEquals(listOf("source:1", "source:2"), result.map { it.id })
        assertEquals(parent.startMs, result.first().startMs)
        assertEquals(parent.endMs, result.last().endMs)
        assertEquals(CaptionCueSplitPolicy.PREFERRED_GAP_MS, result[1].startMs - result[0].endMs)
        assertTrue(result.all { it.endMs - it.startMs >= CaptionCueSplitPolicy.PREFERRED_MIN_DURATION_MS })
        assertEquals(parent.styleOverride, result[0].styleOverride)
        assertEquals(parent.layoutOverride, result[1].layoutOverride)
    }

    @Test
    fun shortParentStillSplitsProportionallyWithoutExpandingItsBoundary() {
        val parent = cue(startMs = 100L, endMs = 700L)

        val result = CaptionCueSplitPolicy.apply(
            parent,
            listOf(CaptionSplitLine("one", "一"), CaptionSplitLine("three three three", "二")),
        )

        assertEquals(100L, result.first().startMs)
        assertEquals(700L, result.last().endMs)
        assertEquals(result.first().endMs, result.last().startMs)
        assertTrue(result.first().endMs - result.first().startMs < result.last().endMs - result.last().startMs)
        assertTrue(CaptionReadabilityIssue.DURATION_TOO_SHORT in CaptionReadability.issues(result.first()))
    }

    @Test
    fun invalidLineCountsAndBlankTextAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CaptionCueSplitPolicy.apply(cue(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            CaptionCueSplitPolicy.apply(cue(), listOf(CaptionSplitLine("", "中文")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CaptionCueSplitPolicy.apply(
                cue(),
                listOf(
                    CaptionSplitLine("a", "甲"),
                    CaptionSplitLine("b", "乙"),
                    CaptionSplitLine("c", "丙"),
                ),
            )
        }
    }

    @Test
    fun readabilityReportsLengthSpeedAndDurationIndependently() {
        val issues = CaptionReadability.issues(
            cue(startMs = 0L, endMs = 500L).copy(
                english = "x".repeat(43),
                chinese = "中".repeat(17),
            ),
        )

        assertTrue(CaptionReadabilityIssue.ENGLISH_LINE_TOO_LONG in issues)
        assertTrue(CaptionReadabilityIssue.CHINESE_LINE_TOO_LONG in issues)
        assertTrue(CaptionReadabilityIssue.ENGLISH_READING_TOO_FAST in issues)
        assertTrue(CaptionReadabilityIssue.CHINESE_READING_TOO_FAST in issues)
        assertTrue(CaptionReadabilityIssue.DURATION_TOO_SHORT in issues)
        assertFalse(CaptionReadabilityIssue.DURATION_TOO_LONG in issues)
    }

    @Test
    fun manualSplitReplacesParentSortsChildrenAndInvalidatesOldExport() {
        val parent = cue(startMs = 1_000L, endMs = 4_000L)
        val state = EditorState(
            captions = listOf(
                cue(startMs = 0L, endMs = 500L).copy(id = "before"),
                parent,
            ),
            selectedCaptionId = parent.id,
            exportUri = android.net.TestUri("content://media/old"),
            exportState = ExportState.SUCCEEDED,
        )

        val updated = state.splitCaptionCue(
            parent.id,
            listOf(CaptionSplitLine("first", "第一"), CaptionSplitLine("second", "第二")),
        )

        assertEquals(listOf("before", "source:1", "source:2"), updated.captions.map { it.id })
        assertEquals("source:1", updated.selectedCaptionId)
        assertNull(updated.exportUri)
        assertEquals(ExportState.IDLE, updated.exportState)
    }

    private fun cue(startMs: Long = 0L, endMs: Long = 2_000L) = CaptionCue(
        id = "source",
        startMs = startMs,
        endMs = endMs,
        english = "raw text",
        chinese = "原文",
        confidence = 0.9f,
        styleOverride = CaptionStyleOverride(bold = true),
        layoutOverride = CaptionLayoutOverride(widthRatio = 0.7f),
    )
}
