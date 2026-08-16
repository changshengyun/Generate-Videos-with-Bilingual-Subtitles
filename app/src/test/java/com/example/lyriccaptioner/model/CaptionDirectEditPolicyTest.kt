package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionDirectEditPolicyTest {
    @Test
    fun movingClampsToFrameAndPreservesWidth() {
        val original = CaptionLayout(xRatio = 0.2f, yRatio = 0.4f, widthRatio = 0.5f)

        assertEquals(
            CaptionLayout(xRatio = 0.5f, yRatio = 0f, widthRatio = 0.5f),
            original.movedToDirectEditPosition(xRatio = 2f, yRatio = -1f),
        )
        assertEquals(
            CaptionLayout(xRatio = 0.2f, yRatio = 0.4f, widthRatio = 0.5f),
            original.movedToDirectEditPosition(Float.NaN, Float.POSITIVE_INFINITY),
        )
    }

    @Test
    fun rightEdgeResizeKeepsLeftEdgeAndUsesStableMinimumAndMaximum() {
        val original = CaptionLayout(xRatio = 0.25f, yRatio = 0.4f, widthRatio = 0.5f)

        assertEquals(
            CaptionLayout(xRatio = 0.25f, yRatio = 0.4f, widthRatio = 0.15f),
            original.withDirectEditWidth(0.01f),
        )
        assertEquals(
            CaptionLayout(xRatio = 0.25f, yRatio = 0.4f, widthRatio = 0.75f),
            original.withDirectEditWidth(2f),
        )
        assertEquals(original, original.withDirectEditWidth(Float.NaN))
        assertEquals(0.15f, MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO)
    }

    @Test
    fun legacyBoxNearRightEdgeUsesAvailableWidthWithoutMovingLeftEdge() {
        val legacy = CaptionLayout(xRatio = 0.92f, yRatio = 0.4f, widthRatio = 0.08f)

        val narrowed = legacy.withDirectEditWidth(0.01f)
        assertEquals(legacy.xRatio, narrowed.xRatio)
        assertEquals(legacy.yRatio, narrowed.yRatio)
        assertEquals(legacy.widthRatio, narrowed.widthRatio, 0.000_001f)
        assertEquals(0.92f, legacy.withDirectEditWidth(0.9f).xRatio)
    }

    @Test
    fun directFontResizeChangesOnlyCanonicalFontProjection() {
        val original = CaptionStyleOverride(
            primaryColorHex = "#123456",
            secondaryColorHex = "#654321",
            alignment = CaptionAlignment.RIGHT,
            fontSizeRatio = DEFAULT_CAPTION_FONT_SIZE_RATIO,
            backgroundEnabled = true,
            backgroundColorHex = "#222222",
        )

        val resized = original.withDirectEditFontSize(Float.POSITIVE_INFINITY)

        assertEquals(DEFAULT_CAPTION_FONT_SIZE_RATIO, resized.fontSizeRatio)
        assertEquals(ratioToLegacyFontSize(DEFAULT_CAPTION_FONT_SIZE_RATIO), resized.fontSizeSp)
        assertEquals(original.primaryColorHex, resized.primaryColorHex)
        assertEquals(original.secondaryColorHex, resized.secondaryColorHex)
        assertEquals(original.alignment, resized.alignment)
        assertEquals(original.backgroundEnabled, resized.backgroundEnabled)
        assertEquals(original.backgroundColorHex, resized.backgroundColorHex)
    }

    @Test
    fun unifiedTextColorUpdatesBothLanguagesAndFallsBackSafely() {
        val cue = CaptionStyleOverride(outlineColorHex = "#ABCDEF")
        val project = DefaultCaptionStyle(outlineColorHex = "#ABCDEF")

        assertEquals("#12ABEF", cue.withUnifiedTextColor("#12abef").primaryColorHex)
        assertEquals("#12ABEF", cue.withUnifiedTextColor("#12abef").secondaryColorHex)
        assertEquals("#FFFFFF", cue.withUnifiedTextColor("not-a-color").primaryColorHex)
        assertEquals("#FFFFFF", project.withUnifiedTextColor("not-a-color").secondaryColorHex)
        assertEquals("#ABCDEF", cue.withUnifiedTextColor("#12abef").outlineColorHex)
    }

    @Test
    fun everyBasicPresetHasDeterministicVisualFields() {
        val base = CaptionStyleOverride(
            fontSizeRatio = 0.03f,
            fontFamily = SUBTITLE_FONT_SERIF,
            alignment = CaptionAlignment.RIGHT,
            backgroundColorHex = "#123456",
        )

        val plain = base.withBasicStylePreset(CaptionBasicStylePreset.PLAIN_TEXT)
        assertEquals(0f, plain.outlineWidthRatio)
        assertEquals(false, plain.backgroundEnabled)

        val darkOutline = base.withBasicStylePreset(CaptionBasicStylePreset.DARK_OUTLINE)
        assertEquals("#FFFFFF", darkOutline.primaryColorHex)
        assertEquals("#FFFFFF", darkOutline.secondaryColorHex)
        assertEquals("#000000", darkOutline.outlineColorHex)
        assertEquals(DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO, darkOutline.outlineWidthRatio)
        assertEquals(false, darkOutline.backgroundEnabled)

        val lightOutline = base.withBasicStylePreset(CaptionBasicStylePreset.LIGHT_OUTLINE)
        assertEquals("#000000", lightOutline.primaryColorHex)
        assertEquals("#FFFFFF", lightOutline.outlineColorHex)
        assertEquals(false, lightOutline.backgroundEnabled)

        val lightBackground = base.withBasicStylePreset(CaptionBasicStylePreset.LIGHT_BACKGROUND)
        assertEquals("#000000", lightBackground.primaryColorHex)
        assertEquals("#FFFFFF", lightBackground.backgroundColorHex)
        assertEquals(true, lightBackground.backgroundEnabled)
        assertEquals(0f, lightBackground.outlineWidthRatio)

        val darkBackground = base.withBasicStylePreset(CaptionBasicStylePreset.DARK_BACKGROUND)
        assertEquals("#FFFFFF", darkBackground.primaryColorHex)
        assertEquals("#000000", darkBackground.backgroundColorHex)
        assertEquals(true, darkBackground.backgroundEnabled)

        val grayBackground = base.withBasicStylePreset(CaptionBasicStylePreset.GRAY_BACKGROUND)
        assertEquals("#FFFFFF", grayBackground.primaryColorHex)
        assertEquals("#4A4A4A", grayBackground.backgroundColorHex)
        assertEquals(true, grayBackground.backgroundEnabled)

        CaptionBasicStylePreset.entries.forEach { preset ->
            val styled = base.withBasicStylePreset(preset)
            assertEquals(base.fontFamily, styled.fontFamily)
            assertEquals(base.fontSizeRatio, styled.fontSizeRatio)
            assertEquals(base.alignment, styled.alignment)
        }
    }

    @Test
    fun backgroundFieldsParticipateInValidationResolutionAndEmptyState() {
        val oldDefault = DefaultCaptionStyle()
        val oldResolved = resolveCaptionStyle(oldDefault, null)
        assertFalse(oldResolved.backgroundEnabled)
        assertEquals("#000000", oldResolved.backgroundColorHex)
        assertEquals(
            "#000000",
            DefaultCaptionStyle(backgroundColorHex = "invalid").validated().backgroundColorHex,
        )
        assertTrue(CaptionStyleOverride().isEmpty)

        val override = CaptionStyleOverride(
            backgroundEnabled = true,
            backgroundColorHex = "invalid",
        )
        assertFalse(override.isEmpty)

        val validated = override.validated()
        assertEquals(true, validated.backgroundEnabled)
        assertEquals("#000000", validated.backgroundColorHex)

        val resolved = resolveCaptionStyle(
            DefaultCaptionStyle(backgroundColorHex = "also-invalid"),
            override,
        )
        assertTrue(resolved.backgroundEnabled)
        assertEquals("#000000", resolved.backgroundColorHex)
    }
}
