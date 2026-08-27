package com.example.lyriccaptioner

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionMergeDirection
import com.example.lyriccaptioner.model.CaptionSplitLine
import com.example.lyriccaptioner.model.CueEditingPolicy
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.clearAllCaptionOverrides
import com.example.lyriccaptioner.model.mergeCaptionCue
import com.example.lyriccaptioner.model.splitCaptionCue
import com.example.lyriccaptioner.model.splitCaptionCueDraft
import com.example.lyriccaptioner.ui.orderedCaptionEditorItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V4.1 feature-parity requirements suite.
 *
 * Every assertion below is derived from the V4.1 product requirements (split/merge/lock
 * editing suite and bilingual text editing semantics), not from the current implementation,
 * so the suite proves whether V4.3 behaves like V4.1 for these features.
 */
class V41FeatureParityTest {

    // ---- R1: manual split produces two independent timed segments ----

    @Test
    fun requirementManualSplitProducesTwoTimedChildrenAndSelectsFirst() {
        val state = editorState(
            cue("cue-a", 1_000L, 5_000L, "First line second line", "第一句。第二句。"),
        )

        val split = state.splitCaptionCue(
            "cue-a",
            listOf(
                CaptionSplitLine("First line", "第一句。"),
                CaptionSplitLine("second line", "第二句。"),
            ),
        )

        assertEquals(2, split.captions.size)
        val first = split.captions.first { it.id == "cue-a:1" }
        val second = split.captions.first { it.id == "cue-a:2" }
        assertEquals(1_000L, first.startMs)
        assertEquals(5_000L, second.endMs)
        assertTrue("children must not overlap", first.endMs <= second.startMs)
        assertEquals("First line", first.english)
        assertEquals("第一句。", first.chinese)
        assertEquals("second line", second.english)
        assertEquals("第二句。", second.chinese)
        assertEquals("cue-a:1", split.selectedCaptionId)
        assertEquals(ExportState.IDLE, split.exportState)
    }

    // ---- R2: one-tap draft split finds safe boundaries, refuses when none exist ----

    @Test
    fun requirementDraftSplitSplitsAtSafeBoundary() {
        val state = editorState(
            cue("cue-b", 0L, 4_000L, "Hello world. Goodbye moon.", "你好世界。再见月亮。"),
        )

        val split = state.splitCaptionCueDraft("cue-b")

        assertEquals(2, split.captions.size)
        assertEquals("cue-b:1", split.captions[0].id)
        assertEquals("cue-b:2", split.captions[1].id)
        assertEquals(0L, split.captions[0].startMs)
        assertEquals(4_000L, split.captions[1].endMs)
        assertTrue(split.captions.all { it.english.isNotBlank() && it.chinese.isNotBlank() })
    }

    @Test
    fun requirementDraftSplitWithoutSafeBoundaryKeepsCueAndReports() {
        val state = editorState(cue("cue-c", 0L, 2_000L, "Wordless", "无标点"))

        val unchanged = state.splitCaptionCueDraft("cue-c")

        assertEquals(1, unchanged.captions.size)
        assertEquals("cue-c", unchanged.captions.single().id)
        assertTrue(unchanged.status.contains("找不到安全的分句边界"))
    }

    // ---- R3: merge combines the two adjacent cues deterministically ----

    @Test
    fun requirementMergeCombinesTimeContentAndConfidence() {
        val state = editorState(
            cue("cue-1", 0L, 1_000L, "Hello world", "你好世界", confidence = 0.9f),
            cue("cue-2", 1_000L, 2_500L, "Goodbye moon", "再见月亮", confidence = 0.7f),
        )

        val merged = state.mergeCaptionCue("cue-1", CaptionMergeDirection.NEXT)

        assertEquals(1, merged.captions.size)
        val result = merged.captions.single()
        assertEquals(0L, result.startMs)
        assertEquals(2_500L, result.endMs)
        assertEquals("Hello world Goodbye moon", result.english)
        assertEquals("你好世界再见月亮", result.chinese)
        assertEquals(0.7f, result.confidence, 0.000001f)
        assertFalse(result.confirmed)
        assertEquals(result.id, merged.selectedCaptionId)
        assertEquals(ExportState.IDLE, merged.exportState)
    }

    @Test
    fun requirementMergeOfSplitChildrenRestoresParentId() {
        val parent = cue("cue-p", 0L, 4_000L, "Whole line", "整句")
        val state = editorState(
            parent.copy(id = "cue-p:1", endMs = 2_000L, english = "Whole", chinese = "整"),
            parent.copy(id = "cue-p:2", startMs = 2_000L, english = "line", chinese = "句"),
        )

        val merged = state.mergeCaptionCue("cue-p:1", CaptionMergeDirection.NEXT)

        assertEquals("cue-p", merged.captions.single().id)
    }

    // ---- R4: layout lock redirects on-screen layout edits to the global default ----

    @Test
    fun requirementLayoutLockRoutesDirectPositionEditToGlobalDefault() {
        val locked = editorState(
            cue("cue-d", 0L, 1_000L, "Text", "文字"),
            layoutLocked = true,
        )

        val updated = locked.withScopedDirectPosition("cue-d", 0.05f, 0.22f)

        assertEquals(0.05f, updated.captionLayout.xRatio, 0.000001f)
        assertEquals(0.22f, updated.captionLayout.yRatio, 0.000001f)
        assertNull("locked layout edit must not write a per-cue override", updated.captions.single().layoutOverride?.xRatio)
    }

    @Test
    fun requirementUnlockedDirectPositionEditTargetsOnlyTheCue() {
        val unlocked = editorState(cue("cue-d", 0L, 1_000L, "Text", "文字"))

        val updated = unlocked.withScopedDirectPosition("cue-d", 0.04f, 0.22f)

        assertEquals(0.05f, updated.captionLayout.xRatio, 0.000001f)
        assertEquals(0.04f, updated.captions.single().layoutOverride?.xRatio ?: -1f, 0.000001f)
        assertEquals(0.22f, updated.captions.single().layoutOverride?.yRatio ?: -1f, 0.000001f)
    }

    @Test
    fun requirementLayoutLockAlsoRoutesWidthAndFontSize() {
        val locked = editorState(
            cue("cue-e", 0L, 1_000L, "Text", "文字"),
            layoutLocked = true,
        )

        val widthUpdated = locked.withScopedDirectWidth("cue-e", 0.42f)
        assertEquals(0.42f, widthUpdated.captionLayout.widthRatio, 0.000001f)
        assertNull(widthUpdated.captions.single().layoutOverride?.widthRatio)

        val fontUpdated = locked.withScopedDirectFontSize("cue-e", 0.03f)
        assertEquals(0.03f, fontUpdated.defaultCaptionStyle.fontSizeRatio, 0.000001f)
        assertNull(fontUpdated.captions.single().styleOverride?.fontSizeRatio)
    }

    // ---- R5: style lock wipes per-cue style drift and edits the global style ----

    @Test
    fun requirementStyleLockClearsEveryPerCueOverride() {
        val state = editorState(
            cue("cue-f", 0L, 1_000L, "Text", "文字", style = CaptionStyleOverride(bold = true)),
            cue("cue-g", 1_000L, 2_000L, "More", "更多", style = CaptionStyleOverride(italic = true)),
        )

        val cleared = state.clearAllCaptionOverrides()

        assertTrue(cleared.captions.all { it.styleOverride == null && it.layoutOverride == null })
    }

    // ---- R6: editing English must preserve the existing Chinese translation (V4.1) ----

    @Test
    fun requirementEditingEnglishPreservesExistingChinese() {
        val cue = cue("cue-h", 0L, 1_000L, "Old English", "保留的中文")

        val updated = CueEditingPolicy.updateEnglish(cue, "New English")

        assertEquals("New English", updated.english)
        assertEquals("V4.1 two-stage repair: editing English must not wipe the Chinese line", "保留的中文", updated.chinese)
        assertTrue(updated.correctionCandidates.isEmpty())
        assertFalse(updated.confirmed)
    }

    @Test
    fun requirementApplyingEnglishCorrectionPreservesChinese() {
        val cue = CaptionCue(
            id = "cue-i",
            startMs = 0L,
            endMs = 1_000L,
            english = "Orig",
            chinese = "原中文",
            confidence = 0.9f,
            correctionCandidates = listOf("Better"),
        )

        val updated = CueEditingPolicy.applyEnglishCorrection(cue, "Better")

        assertEquals("Better", updated.english)
        assertEquals("原中文", updated.chinese)
    }

    // ---- R7: caption list order is the stable timeline order ----

    @Test
    fun requirementCaptionListFollowsTimelineOrder() {
        val captions = listOf(
            cue("late", 5_000L, 6_000L, "Late", "晚"),
            cue("early", 0L, 1_000L, "Early", "早"),
            cue("mid", 2_000L, 3_000L, "Mid", "中"),
        )

        val ordered = orderedCaptionEditorItems(captions)

        assertEquals(listOf("early", "mid", "late"), ordered.map { it.id })
    }

    private fun cue(
        id: String,
        startMs: Long,
        endMs: Long,
        english: String,
        chinese: String,
        confidence: Float = 0.9f,
        style: CaptionStyleOverride? = null,
    ) = CaptionCue(
        id = id,
        startMs = startMs,
        endMs = endMs,
        english = english,
        chinese = chinese,
        confidence = confidence,
        styleOverride = style,
    )

    private fun editorState(vararg cues: CaptionCue, layoutLocked: Boolean = false): EditorState =
        EditorState(
            captions = cues.toList(),
            captionLayout = CaptionLayout(xRatio = 0.05f, yRatio = 0.88f, widthRatio = 0.90f),
            defaultCaptionStyle = DefaultCaptionStyle(),
            exportState = ExportState.SUCCEEDED,
            layoutEditLocked = layoutLocked,
        )
}
