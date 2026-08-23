package com.example.lyriccaptioner

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionBasicStylePreset
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.MAX_CAPTION_FONT_SIZE_RATIO
import com.example.lyriccaptioner.model.MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.resolveCaptionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelDirectEditTest {
    @Test
    fun positionTargetsStableCueIdAndChangesOnlyResolvedPosition() {
        val original = state(
            selectedCaptionId = "sibling",
            targetLayout = CaptionLayoutOverride(xRatio = 0.20f, yRatio = 0.30f, widthRatio = 0.40f),
        )

        val updated = original.withCueDirectPosition("target", xRatio = 0.90f, yRatio = -1f)
        val target = updated.captions.first()

        assertEquals(0.60f, target.layoutOverride?.xRatio ?: -1f, 0.000001f)
        assertEquals(0f, target.layoutOverride?.yRatio ?: -1f, 0.000001f)
        assertEquals(0.40f, target.layoutOverride?.widthRatio ?: -1f, 0.000001f)
        assertEquals(original.captions[1], updated.captions[1])
        assertCueContentUnchanged(original.captions[0], target)
        assertEquals(ExportState.IDLE, updated.exportState)
    }

    @Test
    fun widthKeepsResolvedLeftEdgeAndClampsBothBoundaries() {
        val original = state(
            targetLayout = CaptionLayoutOverride(xRatio = 0.70f, yRatio = 0.22f, widthRatio = 0.20f),
        )

        val minimum = original.withCueDirectWidth("target", -5f).captions.first().layoutOverride!!
        assertEquals(0.70f, minimum.xRatio ?: -1f, 0.000001f)
        assertEquals(0.22f, minimum.yRatio ?: -1f, 0.000001f)
        assertEquals(MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO, minimum.widthRatio ?: -1f, 0.000001f)

        val maximum = original.withCueDirectWidth("target", 5f).captions.first().layoutOverride!!
        assertEquals(0.30f, maximum.widthRatio ?: -1f, 0.000001f)
        assertEquals(0.70f, maximum.xRatio ?: -1f, 0.000001f)
    }

    @Test
    fun fontSizeClampsAndPreservesEveryUnownedStyleField() {
        val initialStyle = CaptionStyleOverride(
            primaryColorHex = "#123456",
            secondaryColorHex = "#654321",
            outlineColorHex = "#111111",
            fontFamily = SUBTITLE_FONT_MONO,
            bold = true,
            italic = true,
            alignment = CaptionAlignment.RIGHT,
            backgroundEnabled = true,
            backgroundColorHex = "#222222",
        )
        val original = state(targetStyle = initialStyle)

        val updated = original.withCueDirectFontSize("target", Float.POSITIVE_INFINITY)
        val resolved = resolveCaptionStyle(updated.defaultCaptionStyle, updated.captions.first().styleOverride)

        // L1 contract falls back non-finite input to the canonical default ratio.
        assertEquals(updated.defaultCaptionStyle.fontSizeRatio, resolved.fontSizeRatio, 0.000001f)
        assertEquals("#123456", resolved.primaryColorHex)
        assertEquals("#654321", resolved.secondaryColorHex)
        assertEquals("#111111", resolved.outlineColorHex)
        assertEquals(SUBTITLE_FONT_MONO, resolved.fontFamily)
        assertTrue(resolved.bold)
        assertTrue(resolved.italic)
        assertEquals(CaptionAlignment.RIGHT, resolved.alignment)
        assertTrue(resolved.backgroundEnabled)
        assertEquals("#222222", resolved.backgroundColorHex)

        val clamped = original.withCueDirectFontSize("target", 99f)
        assertEquals(
            MAX_CAPTION_FONT_SIZE_RATIO,
            resolveCaptionStyle(clamped.defaultCaptionStyle, clamped.captions.first().styleOverride).fontSizeRatio,
            0.000001f,
        )
    }

    @Test
    fun unifiedColorWritesBothLinesOnly() {
        val original = state(
            targetStyle = CaptionStyleOverride(
                primaryColorHex = "#111111",
                secondaryColorHex = "#222222",
                outlineColorHex = "#ABCDEF",
                alignment = CaptionAlignment.LEFT,
            ),
        )

        val updated = original.withCueUnifiedTextColor("target", "#12ab34")
        val resolved = resolveCaptionStyle(updated.defaultCaptionStyle, updated.captions.first().styleOverride)

        assertEquals("#12AB34", resolved.primaryColorHex)
        assertEquals("#12AB34", resolved.secondaryColorHex)
        assertEquals("#ABCDEF", resolved.outlineColorHex)
        assertEquals(CaptionAlignment.LEFT, resolved.alignment)
    }

    @Test
    fun everyBasicPresetPreservesFontSizeFamilyAndAlignment() {
        CaptionBasicStylePreset.entries.forEach { preset ->
            val original = state(
                targetStyle = CaptionStyleOverride(
                    fontSizeRatio = 0.03f,
                    fontFamily = SUBTITLE_FONT_MONO,
                    alignment = CaptionAlignment.RIGHT,
                ),
            )
            val updated = original.withCueBasicStyle("target", preset)
            val resolved = resolveCaptionStyle(updated.defaultCaptionStyle, updated.captions.first().styleOverride)

            assertEquals("preset=$preset", 0.03f, resolved.fontSizeRatio, 0.000001f)
            assertEquals("preset=$preset", SUBTITLE_FONT_MONO, resolved.fontFamily)
            assertEquals("preset=$preset", CaptionAlignment.RIGHT, resolved.alignment)
            when (preset) {
                CaptionBasicStylePreset.PLAIN_TEXT,
                CaptionBasicStylePreset.DARK_OUTLINE,
                CaptionBasicStylePreset.LIGHT_OUTLINE -> assertFalse("preset=$preset", resolved.backgroundEnabled)
                CaptionBasicStylePreset.LIGHT_BACKGROUND,
                CaptionBasicStylePreset.DARK_BACKGROUND,
                CaptionBasicStylePreset.GRAY_BACKGROUND -> assertTrue("preset=$preset", resolved.backgroundEnabled)
            }
        }
    }

    @Test
    fun missingCueIsIdentityAndDoesNotInvalidateDerivedOutput() {
        val original = state()
        assertSame(original, original.withCueDirectPosition("missing", 0.1f, 0.1f))
        assertSame(original, original.withCueDirectWidth("missing", 0.5f))
        assertSame(original, original.withCueDirectFontSize("missing", 0.03f))
        assertSame(original, original.withCueBasicStyle("missing", CaptionBasicStylePreset.DARK_OUTLINE))
        assertSame(original, original.withCueUnifiedTextColor("missing", "#123456"))
        assertEquals(ExportState.SUCCEEDED, original.exportState)
    }

    @Test
    fun everyEffectiveApiInvalidatesDerivedOutput() {
        val original = state()
        val results = listOf(
            original.withCueDirectPosition("target", 0.01f, 0.02f),
            original.withCueDirectWidth("target", 0.5f),
            original.withCueDirectFontSize("target", 0.04f),
            original.withCueBasicStyle("target", CaptionBasicStylePreset.DARK_BACKGROUND),
            original.withCueUnifiedTextColor("target", "#123456"),
        )
        results.forEach { assertEquals(ExportState.IDLE, it.exportState) }
    }

    private fun state(
        selectedCaptionId: String? = null,
        targetLayout: CaptionLayoutOverride? = null,
        targetStyle: CaptionStyleOverride? = null,
    ): EditorState = EditorState(
        captions = listOf(
            CaptionCue("target", 10, 20, "English", "中文", 0.9f, styleOverride = targetStyle, layoutOverride = targetLayout),
            CaptionCue("sibling", 20, 30, "Sibling", "相邻", 0.8f),
        ),
        selectedCaptionId = selectedCaptionId,
        captionLayout = CaptionLayout(xRatio = 0.05f, yRatio = 0.88f, widthRatio = 0.90f),
        defaultCaptionStyle = DefaultCaptionStyle(),
        exportState = ExportState.SUCCEEDED,
    )

    private fun assertCueContentUnchanged(before: CaptionCue, after: CaptionCue) {
        assertEquals(before.id, after.id)
        assertEquals(before.startMs, after.startMs)
        assertEquals(before.endMs, after.endMs)
        assertEquals(before.english, after.english)
        assertEquals(before.chinese, after.chinese)
        assertEquals(before.confidence, after.confidence, 0f)
        assertEquals(before.styleOverride, after.styleOverride)
    }
}
