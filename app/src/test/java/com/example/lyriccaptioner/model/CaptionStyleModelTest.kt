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
}
