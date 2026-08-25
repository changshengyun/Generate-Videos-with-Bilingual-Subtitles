package com.example.lyriccaptioner.model

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEditControlPolicyTest {
    @Test
    fun globalPositionUpdatesDefaultAndClearsOnlyPositionOverrides() {
        val state = editorState(
            captions = listOf(
                cue(
                    "a",
                    layout = CaptionLayoutOverride(xRatio = 0.2f, yRatio = 0.3f, widthRatio = 0.4f),
                    style = CaptionStyleOverride(primaryColorHex = "#123456"),
                ),
                cue("b", start = 2_000, layout = CaptionLayoutOverride(yRatio = 0.5f)),
            ),
        )

        val updated = state.withGlobalDirectPosition(0.1f, 0.2f)

        assertEquals(0.1f, updated.captionLayout.xRatio, 0.000001f)
        assertEquals(0.2f, updated.captionLayout.yRatio, 0.000001f)
        assertEquals(0.4f, updated.captions[0].layoutOverride?.widthRatio ?: -1f, 0.000001f)
        assertNull(updated.captions[0].layoutOverride?.xRatio)
        assertNull(updated.captions[0].layoutOverride?.yRatio)
        assertEquals("#123456", updated.captions[0].styleOverride?.primaryColorHex)
        assertNull(updated.captions[1].layoutOverride)
        assertEquals(ExportState.IDLE, updated.exportState)
        assertNull(updated.exportUri)
    }

    @Test
    fun globalWidthAndFontSizeClearOnlyOwnedOverrides() {
        val style = CaptionStyleOverride(
            fontSizeRatio = 0.03f,
            primaryColorHex = "#123456",
            bold = true,
        )
        val state = editorState(
            captions = listOf(
                cue(
                    "a",
                    layout = CaptionLayoutOverride(xRatio = 0.1f, yRatio = 0.2f, widthRatio = 0.5f),
                    style = style,
                ),
            ),
        )

        val widthUpdated = state.withGlobalDirectWidth(0.7f)
        assertEquals(0.7f, widthUpdated.captionLayout.widthRatio, 0.000001f)
        assertEquals(0.1f, widthUpdated.captions.single().layoutOverride?.xRatio ?: -1f, 0.000001f)
        assertEquals(0.2f, widthUpdated.captions.single().layoutOverride?.yRatio ?: -1f, 0.000001f)
        assertNull(widthUpdated.captions.single().layoutOverride?.widthRatio)

        val fontUpdated = state.withGlobalDirectFontSize(0.04f)
        val remaining = fontUpdated.captions.single().styleOverride
        assertNull(remaining?.fontSizeRatio)
        assertNull(remaining?.fontSizeSp)
        assertEquals("#123456", remaining?.primaryColorHex)
        assertTrue(remaining?.bold == true)
    }

    @Test
    fun globalPositionAndWidthRemainCompatibleWithRetainedOverrides() {
        val state = editorState(
            captions = listOf(
                cue("wide", layout = CaptionLayoutOverride(widthRatio = 0.8f)),
                cue("right", start = 2_000, layout = CaptionLayoutOverride(xRatio = 0.7f)),
            ),
        )

        val moved = state.withGlobalDirectPosition(xRatio = 0.9f, yRatio = 0.4f)
        assertEquals(0.1f, moved.captionLayout.xRatio, 0.000001f)
        moved.captions.forEach { resolveCaptionLayout(moved.captionLayout, it.layoutOverride) }

        val widened = state.withGlobalDirectWidth(0.9f)
        assertEquals(0.3f, widened.captionLayout.widthRatio, 0.000001f)
        widened.captions.forEach { resolveCaptionLayout(widened.captionLayout, it.layoutOverride) }
        assertNull(widened.captions[0].layoutOverride)
        assertEquals(0.7f, widened.captions[1].layoutOverride?.xRatio ?: -1f, 0.000001f)
    }

    @Test
    fun globalStylePropertyDoesNotClearUnrelatedOverrides() {
        val state = editorState(
            captions = listOf(
                cue(
                    "a",
                    style = CaptionStyleOverride(
                        primaryColorHex = "#111111",
                        secondaryColorHex = "#222222",
                        italic = true,
                    ),
                ),
            ),
        )

        val updated = state.withGlobalEnglishColor("#ABCDEF")
        val override = updated.captions.single().styleOverride

        assertEquals("#ABCDEF", updated.defaultCaptionStyle.primaryColorHex)
        assertNull(override?.primaryColorHex)
        assertEquals("#222222", override?.secondaryColorHex)
        assertTrue(override?.italic == true)
    }

    @Test
    fun lockTogglesAreSessionStateOnlyAndDoNotChangeCaptionData() {
        val state = editorState(captions = listOf(cue("a")))
        val updated = state.copy(layoutEditLocked = true, styleEditLocked = true)

        assertEquals(state.captions, updated.captions)
        assertEquals(state.captionLayout, updated.captionLayout)
        assertEquals(state.defaultCaptionStyle, updated.defaultCaptionStyle)
        assertSame(state.exportUri, updated.exportUri)
        assertEquals(state.exportState, updated.exportState)
    }

    @Test
    fun mergeNextCombinesContentTimeConfidenceAndUsesInitiatingVisuals() {
        val selectedStyle = CaptionStyleOverride(bold = true)
        val selectedLayout = CaptionLayoutOverride(yRatio = 0.3f)
        val state = editorState(
            selectedCaptionId = "parent:1",
            captions = listOf(
                cue(
                    "parent:1",
                    start = 1_000,
                    end = 2_000,
                    english = " first ",
                    chinese = " 第一 ",
                    confidence = 0.9f,
                    style = selectedStyle,
                    layout = selectedLayout,
                    confirmed = true,
                    candidates = listOf("candidate"),
                ),
                cue(
                    "parent:2",
                    start = 2_100,
                    end = 3_000,
                    english = " second ",
                    chinese = " 第二 ",
                    confidence = 0.6f,
                    style = CaptionStyleOverride(italic = true),
                ),
            ),
        )

        val updated = state.mergeCaptionCue("parent:1", CaptionMergeDirection.NEXT)
        val merged = updated.captions.single()

        assertEquals("parent", merged.id)
        assertEquals("first second", merged.english)
        assertEquals("第一第二", merged.chinese)
        assertEquals(1_000, merged.startMs)
        assertEquals(3_000, merged.endMs)
        assertEquals(0.6f, merged.confidence, 0f)
        assertFalse(merged.confirmed)
        assertTrue(merged.correctionCandidates.isEmpty())
        assertEquals(selectedStyle, merged.styleOverride)
        assertEquals(selectedLayout, merged.layoutOverride)
        assertEquals("parent", updated.selectedCaptionId)
        assertEquals(ExportState.IDLE, updated.exportState)
        assertNull(updated.exportUri)
    }

    @Test
    fun mergePreviousKeepsEarlierIdButUsesLaterInitiatingVisuals() {
        val initiatingStyle = CaptionStyleOverride(italic = true)
        val state = editorState(
            captions = listOf(
                cue("first", start = 1_000, end = 2_000, english = "One", chinese = "一"),
                cue("second", start = 2_000, end = 3_000, english = "Two", chinese = "二", style = initiatingStyle),
            ),
        )

        val updated = state.mergeCaptionCue("second", CaptionMergeDirection.PREVIOUS)
        val merged = updated.captions.single()

        assertEquals("first", merged.id)
        assertEquals("One Two", merged.english)
        assertEquals("一二", merged.chinese)
        assertEquals(initiatingStyle, merged.styleOverride)
    }

    @Test
    fun siblingMergeFallsBackToEarlierIdWhenParentIdAlreadyExists() {
        val state = editorState(
            captions = listOf(
                cue("parent", start = 0, end = 500),
                cue("parent:1", start = 1_000, end = 2_000),
                cue("parent:2", start = 2_000, end = 3_000),
            ),
        )

        val updated = state.mergeCaptionCue("parent:1", CaptionMergeDirection.NEXT)

        assertEquals(listOf("parent", "parent:1"), updated.captions.map(CaptionCue::id))
        assertEquals(updated.captions.size, updated.captions.map(CaptionCue::id).distinct().size)
    }

    @Test
    fun mergeRejectsMissingAndTimelineEdgesWithoutInvalidating() {
        val state = editorState(captions = listOf(cue("a"), cue("b", start = 2_000)))

        assertSame(state, state.mergeCaptionCue("missing", CaptionMergeDirection.NEXT))
        assertSame(state, state.mergeCaptionCue("a", CaptionMergeDirection.PREVIOUS))
        assertSame(state, state.mergeCaptionCue("b", CaptionMergeDirection.NEXT))
    }

    private fun editorState(
        captions: List<CaptionCue>,
        selectedCaptionId: String? = null,
    ): EditorState = EditorState(
        captions = captions,
        selectedCaptionId = selectedCaptionId,
        captionLayout = CaptionLayout(),
        defaultCaptionStyle = DefaultCaptionStyle(),
        exportUri = TestUri("content://exports/old"),
        exportState = ExportState.SUCCEEDED,
    )

    private fun cue(
        id: String,
        start: Long = 1_000,
        end: Long = start + 900,
        english: String = "English",
        chinese: String = "中文",
        confidence: Float = 0.9f,
        style: CaptionStyleOverride? = null,
        layout: CaptionLayoutOverride? = null,
        confirmed: Boolean = false,
        candidates: List<String> = emptyList(),
    ): CaptionCue = CaptionCue(
        id = id,
        startMs = start,
        endMs = end,
        english = english,
        chinese = chinese,
        confidence = confidence,
        correctionCandidates = candidates,
        confirmed = confirmed,
        styleOverride = style,
        layoutOverride = layout,
    )
}
