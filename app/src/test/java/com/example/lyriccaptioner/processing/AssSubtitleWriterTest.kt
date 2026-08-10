package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssSubtitleWriterTest {
    @Test
    fun writesBilingualTimedDialogueAndStyle() {
        val ass = AssSubtitleWriter.write(
            captions = listOf(
                CaptionCue(
                    id = "cue-1",
                    startMs = 1_230L,
                    endMs = 4_560L,
                    english = "Hello {world}",
                    chinese = "你好",
                    confidence = 1f,
                ),
            ),
            style = SubtitleStyle(
                fontSizeSp = 30,
                bottomMarginPercent = 12,
                primaryColorHex = "#112233",
                secondaryColorHex = "#AABBCC",
                outlineColorHex = "#000000",
            ),
        )

        assertTrue(ass.contains("Style: Cue0000,sans-serif,30,&H00332211,&H00CCBBAA,&H00000000"))
        assertTrue(ass.contains("Dialogue: 0,0:00:01.23,0:00:04.56"))
        assertTrue(ass.contains("Hello \\{world\\}\\N你好"))
    }

    @Test
    fun mapsSelectedFontFamilyToAssStyle() {
        val ass = AssSubtitleWriter.write(
            captions = listOf(CaptionCue("cue", 0L, 1_000L, "Hello", "你好", 1f)),
            style = SubtitleStyle(fontFamily = "mono"),
        )

        assertTrue(ass.contains("Style: Cue0000,monospace,"))
    }

    @Test
    fun writesIndependentResolvedStyleAndSharedLayoutForEveryCue() {
        val ass = AssSubtitleWriter.write(
            captions = listOf(
                CaptionCue(
                    id = "first",
                    startMs = 1_230L,
                    endMs = 4_560L,
                    english = "First",
                    chinese = "One",
                    confidence = 1f,
                    styleOverride = CaptionStyleOverride(
                        fontSizeSp = 40,
                        primaryColorHex = "#112233",
                        bold = true,
                        alignment = com.example.lyriccaptioner.model.CaptionAlignment.LEFT,
                    ),
                ),
                CaptionCue(
                    id = "second",
                    startMs = 5_000L,
                    endMs = 6_780L,
                    english = "Second",
                    chinese = "Two",
                    confidence = 1f,
                ),
            ),
            layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.2f, widthRatio = 0.5f),
            defaultStyle = DefaultCaptionStyle(fontSizeSp = 20),
        )

        assertTrue(ass.contains("Style: Cue0000,sans-serif,40,&H00332211"))
        assertTrue(ass.contains("&H80000000,-1,0,0,0,100,100"))
        assertTrue(ass.contains("Style: Cue0001,sans-serif,20,&H00FFFFFF"))
        assertTrue(
            ass.contains(
                "Dialogue: 0,0:00:01.23,0:00:04.56,Cue0000,,192,768,216,," +
                    "{\\an7\\pos(192,216)\\q0}First\\NOne",
            ),
        )
        assertTrue(
            ass.contains(
                "Dialogue: 0,0:00:05.00,0:00:06.78,Cue0001,,192,768,216,," +
                    "{\\an8\\pos(672,216)\\q0}Second\\NTwo",
            ),
        )
        assertEquals(2, ass.lineSequence().count { it.startsWith("Dialogue:") })
        assertFalse(ass.contains("Dialogue: 0,0:00:05.00,0:00:06.78,Cue0000"))
    }

    @Test
    fun normalizesUnsafeStyleAndKeepsTextAndTimestamps() {
        val ass = AssSubtitleWriter.write(
            captions = listOf(
                CaptionCue(
                    id = "cue",
                    startMs = 12_340L,
                    endMs = 56_780L,
                    english = "  literal {text}  ",
                    chinese = "line\nbreak",
                    confidence = 1f,
                ),
            ),
            layout = CaptionLayout(),
            defaultStyle = DefaultCaptionStyle(
                fontSizeSp = 999,
                primaryColorHex = "not-a-color",
                fontFamily = "unknown-font",
            ),
        )

        assertTrue(ass.contains("Style: Cue0000,sans-serif,48,&H00FFFFFF"))
        assertTrue(ass.contains("Dialogue: 0,0:00:12.34,0:00:56.78"))
        assertTrue(ass.contains("  literal \\{text\\}  \\Nline\\Nbreak"))
    }
}
