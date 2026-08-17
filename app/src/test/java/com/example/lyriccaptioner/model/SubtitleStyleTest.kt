package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleTest {
    @Test
    fun validatesColorsAndSupportedFonts() {
        assertTrue(isValidSubtitleColorHex("#12aBcD"))
        assertFalse(isValidSubtitleColorHex("12aBcD"))
        assertFalse(isValidSubtitleColorHex("#12345"))
        assertEquals("#FFFFFF", normalizeSubtitleColor("bad", "#FFFFFF"))
        assertEquals("#12ABCD", normalizeSubtitleColor("#12aBcD", "#FFFFFF"))
    }

    @Test
    fun validatedStyleClampsBoundsAndUsesSafeFont() {
        val style = SubtitleStyle(
            fontSizeSp = 100,
            bottomMarginPercent = 0,
            primaryColorHex = "bad",
            fontFamily = "unknown",
        ).validated()

        assertEquals(48, style.fontSizeSp)
        assertEquals(4, style.bottomMarginPercent)
        assertEquals("#FFFFFF", style.primaryColorHex)
        assertEquals(SUBTITLE_FONT_SANS, style.fontFamily)
    }
}
