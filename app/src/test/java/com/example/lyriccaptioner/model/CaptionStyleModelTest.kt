package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionStyleModelTest {
    @Test
    fun layoutUsesNormalizedSourceCoordinatesAndFitsInsideFrame() {
        assertEquals(CaptionLayout(0.1f, 0.8f, 0.7f), CaptionLayout(0.1f, 0.8f, 0.7f))
        assertThrows(IllegalArgumentException::class.java) { CaptionLayout(-0.1f, 0.8f, 0.7f) }
        assertThrows(IllegalArgumentException::class.java) { CaptionLayout(0.5f, 0.8f, 0.6f) }
        assertThrows(IllegalArgumentException::class.java) { CaptionLayout(0.1f, Float.NaN, 0.7f) }
        assertThrows(IllegalArgumentException::class.java) { CaptionLayout(0.1f, 0.8f, 0f) }
    }

    @Test
    fun cueLayoutOverrideInheritsUnsetCoordinatesAndRejectsOutOfBoundsResolution() {
        val project = CaptionLayout(0.1f, 0.8f, 0.7f)
        val override = CaptionLayoutOverride(xRatio = 0.2f, widthRatio = 0.5f)

        assertEquals(CaptionLayout(0.2f, 0.8f, 0.5f), resolveCaptionLayout(project, override))
        assertThrows(IllegalArgumentException::class.java) {
            resolveCaptionLayout(project, CaptionLayoutOverride(xRatio = 0.8f, widthRatio = 0.5f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CaptionLayoutOverride(yRatio = Float.NaN)
        }
    }

    @Test
    fun sharedVerticalAnchorMappingCoversTopMiddleAndBottom() {
        assertEquals(CaptionVerticalAnchor.TOP, CaptionLayout(yRatio = 0.2f).verticalAnchor())
        assertEquals(0.2f, CaptionLayout(yRatio = 0.2f).verticalAnchorOffsetRatio())
        assertEquals(CaptionVerticalAnchor.MIDDLE, CaptionLayout(yRatio = 0.5f).verticalAnchor())
        assertEquals(0f, CaptionLayout(yRatio = 0.5f).verticalAnchorOffsetRatio())
        assertEquals(CaptionVerticalAnchor.BOTTOM, CaptionLayout(yRatio = 0.8f).verticalAnchor())
        assertEquals(-0.2f, CaptionLayout(yRatio = 0.8f).verticalAnchorOffsetRatio(), 0.0001f)
    }

    @Test
    fun defaultStyleAppliesWhenCueHasNoOverride() {
        val default = DefaultCaptionStyle(fontSizeSp = 30, bold = true, alignment = CaptionAlignment.LEFT)

        val resolved = resolveCaptionStyle(default, null)

        assertEquals(30, resolved.fontSizeSp)
        assertTrue(resolved.bold)
        assertEquals(CaptionAlignment.LEFT, resolved.alignment)
    }

    @Test
    fun oneCueOverrideDoesNotMutateDefaultOrAnotherCue() {
        val default = DefaultCaptionStyle(fontSizeSp = 24, primaryColorHex = "#FFFFFF")
        val first = CaptionCue("a", 0, 100, "a", "甲", 0.9f, styleOverride = CaptionStyleOverride(fontSizeSp = 32))
        val second = CaptionCue("b", 100, 200, "b", "乙", 0.9f)

        assertEquals(32, resolveCaptionStyle(default, first.styleOverride).fontSizeSp)
        assertEquals(24, resolveCaptionStyle(default, second.styleOverride).fontSizeSp)
        assertEquals(24, default.fontSizeSp)
    }

    @Test
    fun clearingOverrideImmediatelyFallsBackToDefault() {
        val default = DefaultCaptionStyle(fontSizeSp = 27)
        val overridden = CaptionCue("a", 0, 100, "a", "甲", 0.9f, styleOverride = CaptionStyleOverride(fontSizeSp = 40))
        val cleared = overridden.copy(styleOverride = null)

        assertEquals(40, resolveCaptionStyle(default, overridden.styleOverride).fontSizeSp)
        assertEquals(27, resolveCaptionStyle(default, cleared.styleOverride).fontSizeSp)
        assertNull(cleared.styleOverride)
    }

    @Test
    fun clearingOverridesByCueIdRemovesStyleAndLayoutOnlyFromTargetCue() {
        val target = CaptionCue(
            "target",
            0,
            100,
            "a",
            "甲",
            0.9f,
            styleOverride = CaptionStyleOverride(fontSizeSp = 32),
            layoutOverride = CaptionLayoutOverride(yRatio = 0.2f),
        )
        val sibling = CaptionCue(
            "sibling",
            100,
            200,
            "b",
            "乙",
            0.9f,
            styleOverride = CaptionStyleOverride(italic = true),
            layoutOverride = CaptionLayoutOverride(xRatio = 0.1f),
        )

        val cleared = listOf(target, sibling).clearOverridesForCue("target")

        assertNull(cleared[0].styleOverride)
        assertNull(cleared[0].layoutOverride)
        assertEquals(CaptionLayout(0.05f, 0.88f, 0.9f), resolveCaptionLayout(CaptionLayout(), cleared[0].layoutOverride))
        assertEquals(sibling.styleOverride, cleared[1].styleOverride)
        assertEquals(sibling.layoutOverride, cleared[1].layoutOverride)
    }

    @Test
    fun changingDefaultPreservesExplicitFieldsAndUpdatesInheritedFields() {
        val override = CaptionStyleOverride(primaryColorHex = "#123456", italic = true)
        val before = resolveCaptionStyle(DefaultCaptionStyle(fontSizeSp = 24), override)
        val after = resolveCaptionStyle(DefaultCaptionStyle(fontSizeSp = 35, primaryColorHex = "#ABCDEF"), override)

        assertEquals(24, before.fontSizeSp)
        assertEquals(35, after.fontSizeSp)
        assertEquals("#123456", after.primaryColorHex)
        assertTrue(after.italic)
    }

    @Test
    fun textTimingConfirmationAndStyleAreIndependentCopyFields() {
        val original = CaptionCue(
            id = "cue",
            startMs = 100,
            endMs = 900,
            english = "before",
            chinese = "之前",
            confidence = 0.8f,
            styleOverride = CaptionStyleOverride(bold = true),
        )
        val edited = original.copy(english = "after", startMs = 120, endMs = 920, confirmed = true)

        assertEquals(original.styleOverride, edited.styleOverride)
        assertEquals("after", edited.english)
        assertEquals(120L, edited.startMs)
        assertTrue(edited.confirmed)
    }

    @Test
    fun legacySubtitleStyleMapsToSafeV3DefaultAndLayout() {
        val legacy = SubtitleStyle(
            fontSizeSp = 31,
            bottomMarginPercent = 15,
            primaryColorHex = "#12abef",
            fontFamily = SUBTITLE_FONT_SERIF,
        )

        val style = legacy.toDefaultCaptionStyle()
        val layout = legacy.toCaptionLayout()

        assertEquals(31, style.fontSizeSp)
        assertEquals("#12ABEF", style.primaryColorHex)
        assertEquals(SUBTITLE_FONT_SERIF, style.fontFamily)
        assertEquals(0.85f, layout.yRatio)
        assertFalse(style.bold)
    }

    @Test
    fun sourceRelativeFontAndOutlineRatiosUseStableLegacyProjection() {
        val style = DefaultCaptionStyle(
            fontSizeRatio = 32f / LEGACY_PLAY_RES_Y,
            outlineWidthRatio = 3f / LEGACY_PLAY_RES_Y,
        )

        val resolved = resolveCaptionStyle(style, null)

        assertEquals(32, resolved.fontSizeSp)
        assertEquals(32f / LEGACY_PLAY_RES_Y, resolved.fontSizeRatio, 0.000001f)
        assertEquals(3f / LEGACY_PLAY_RES_Y, resolved.outlineWidthRatio, 0.000001f)
    }

    @Test
    fun legacyFontSizeOverrideMigratesToRatioWithoutMutatingDefault() {
        val default = DefaultCaptionStyle(fontSizeRatio = 24f / LEGACY_PLAY_RES_Y)
        val override = CaptionStyleOverride(fontSizeSp = 40)

        val resolved = resolveCaptionStyle(default, override)

        assertEquals(40f / LEGACY_PLAY_RES_Y, resolved.fontSizeRatio, 0.000001f)
        assertEquals(24f / LEGACY_PLAY_RES_Y, resolveCaptionStyle(default, null).fontSizeRatio, 0.000001f)
    }

    @Test
    fun canonicalRatioWinsOverConflictingLegacyProjectionAndKeepsItInSync() {
        val override = CaptionStyleOverride(
            fontSizeSp = 48,
            fontSizeRatio = 29f / LEGACY_PLAY_RES_Y,
        ).validated()

        assertEquals(29, override.fontSizeSp)
        assertEquals(29f / LEGACY_PLAY_RES_Y, override.fontSizeRatio ?: Float.NaN, 0.000001f)
        assertEquals(29, resolveCaptionStyle(DefaultCaptionStyle(), override).fontSizeSp)
    }

    @Test
    fun ratioWritesClampAndRoundAtCanonicalBoundaries() {
        assertEquals(MIN_CAPTION_FONT_SIZE_RATIO, canonicalCaptionFontSizeRatio(-1f), 0.000001f)
        assertEquals(MAX_CAPTION_FONT_SIZE_RATIO, canonicalCaptionFontSizeRatio(1f), 0.000001f)
        assertEquals(DEFAULT_CAPTION_FONT_SIZE_RATIO, canonicalCaptionFontSizeRatio(Float.NaN), 0.000001f)
        // Non-finite ratios are invalid input and safely fall back to the
        // default projection; finite values at the boundaries are clamped.
        assertEquals(24, ratioToLegacyFontSize(Float.NEGATIVE_INFINITY))
        assertEquals(24, ratioToLegacyFontSize(Float.POSITIVE_INFINITY))
        assertEquals(MIN_CAPTION_OUTLINE_WIDTH_RATIO, canonicalCaptionOutlineWidthRatio(-1f), 0.000001f)
        assertEquals(DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO, canonicalCaptionOutlineWidthRatio(Float.POSITIVE_INFINITY), 0.000001f)
        assertEquals(DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO, canonicalCaptionOutlineWidthRatio(Float.NaN), 0.000001f)
    }

    @Test
    fun adjustRatioUsesOneCanonicalStepForDefaultAndCueWrites() {
        val default = DefaultCaptionStyle().withFontSizeRatio(
            adjustCaptionFontSizeRatio(DEFAULT_CAPTION_FONT_SIZE_RATIO, 5),
        )
        val cue = CaptionStyleOverride(fontSizeSp = 24).withFontSizeRatio(
            adjustCaptionFontSizeRatio(24f / LEGACY_PLAY_RES_Y, -20),
        )

        assertEquals(29, default.fontSizeSp)
        assertEquals(29f / LEGACY_PLAY_RES_Y, default.fontSizeRatio, 0.000001f)
        assertEquals(14, cue.fontSizeSp)
        assertEquals(14f / LEGACY_PLAY_RES_Y, cue.fontSizeRatio ?: Float.NaN, 0.000001f)
    }

    @Test
    fun explicitRatioSurvivesLegacyStyleChangesAndLegacyOnlyInputStillMigrates() {
        val canonical = DefaultCaptionStyle(fontSizeSp = 40, fontSizeRatio = 30f / LEGACY_PLAY_RES_Y).validated()
        val legacyOnly = CaptionStyleOverride(fontSizeSp = 32).validated()

        assertEquals(30, canonical.fontSizeSp)
        assertEquals(30f / LEGACY_PLAY_RES_Y, canonical.fontSizeRatio, 0.000001f)
        assertEquals(32, legacyOnly.fontSizeSp)
        assertEquals(32f / LEGACY_PLAY_RES_Y, legacyOnly.fontSizeRatio ?: Float.NaN, 0.000001f)
    }
}
