package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
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
        assertTrue(ass.contains("Hello \\{world\\}\\N{\\c&H00CCBBAA&}你好"))
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
    fun appliesChineseColorOnlyToChineseTextAndScopesItPerDialogue() {
        val ass = AssSubtitleWriter.write(
            captions = listOf(
                CaptionCue(
                    id = "bilingual",
                    startMs = 0L,
                    endMs = 1_000L,
                    english = "English",
                    chinese = "中文",
                    confidence = 1f,
                    styleOverride = CaptionStyleOverride(secondaryColorHex = "#112233"),
                ),
                CaptionCue(
                    id = "english-only",
                    startMs = 1_000L,
                    endMs = 2_000L,
                    english = "Next",
                    chinese = "",
                    confidence = 1f,
                ),
            ),
            layout = CaptionLayout(),
            defaultStyle = DefaultCaptionStyle(),
        )

        val dialogues = ass.lineSequence().filter { it.startsWith("Dialogue:") }.toList()
        assertEquals(2, dialogues.size)
        assertTrue(dialogues[0].contains("English\\N{\\c&H00332211&}中文"))
        assertFalse(dialogues[0].substringBefore("\\N").contains("\\c&H"))
        assertTrue(dialogues[1].endsWith("}Next"))
        assertFalse(dialogues[1].contains("\\c&H"))
    }

    @Test
    fun mapsLeftCenterRightAndTopMiddleBottomUsingSharedSourceCoordinates() {
        val layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.2f, widthRatio = 0.4f)
        val captions = listOf(
            CaptionCue("left-top", 0L, 1_000L, "L", "一", 1f,
                styleOverride = CaptionStyleOverride(alignment = com.example.lyriccaptioner.model.CaptionAlignment.LEFT)),
            CaptionCue("center-middle", 1_000L, 2_000L, "C", "二", 1f,
                styleOverride = CaptionStyleOverride(alignment = com.example.lyriccaptioner.model.CaptionAlignment.CENTER),
                layoutOverride = CaptionLayoutOverride(xRatio = 0.2f, yRatio = 0.5f, widthRatio = 0.4f)),
            CaptionCue("right-bottom", 2_000L, 3_000L, "R", "三", 1f,
                styleOverride = CaptionStyleOverride(alignment = com.example.lyriccaptioner.model.CaptionAlignment.RIGHT),
                layoutOverride = CaptionLayoutOverride(xRatio = 0.3f, yRatio = 0.8f, widthRatio = 0.4f)),
        )

        val dialogues = AssSubtitleWriter.write(captions, layout, DefaultCaptionStyle())
            .lineSequence().filter { it.startsWith("Dialogue:") }.toList()

        assertTrue(dialogues[0].contains("{\\an7\\pos(192,216)\\q0}"))
        assertTrue(dialogues[1].contains("{\\an5\\pos(768,540)\\q0}"))
        assertTrue(dialogues[2].contains("{\\an3\\pos(1344,864)\\q0}"))
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
                    "{\\an7\\pos(192,216)\\q0}First\\N{\\c&H00A1E7F4&}One",
            ),
        )
        assertTrue(
            ass.contains(
                "Dialogue: 0,0:00:05.00,0:00:06.78,Cue0001,,192,768,216,," +
                    "{\\an8\\pos(672,216)\\q0}Second\\N{\\c&H00A1E7F4&}Two",
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
        assertTrue(ass.contains("  literal \\{text\\}  \\N{\\c&H00A1E7F4&}line\\Nbreak"))
    }

    @Test
    fun writesEachCueUsingTheSameResolvedStyleAndLayoutAsComposeBoundary() {
        val projectLayout = CaptionLayout(xRatio = 0.1f, yRatio = 0.8f, widthRatio = 0.8f)
        val defaultStyle = DefaultCaptionStyle(fontSizeSp = 20)
        val moved = CaptionCue(
            id = "moved",
            startMs = 1_000L,
            endMs = 2_000L,
            english = "Moved",
            chinese = "移动",
            confidence = 1f,
            styleOverride = CaptionStyleOverride(
                fontSizeSp = 32,
                alignment = com.example.lyriccaptioner.model.CaptionAlignment.RIGHT,
            ),
            layoutOverride = CaptionLayoutOverride(
                xRatio = 0.25f,
                yRatio = 0.25f,
                widthRatio = 0.5f,
            ),
        )
        val inherited = CaptionCue(
            id = "inherited",
            startMs = 2_000L,
            endMs = 3_000L,
            english = "Inherited",
            chinese = "继承",
            confidence = 1f,
        )

        val composeRenders = CaptionRenderResolver.resolveAll(
            captions = listOf(moved, inherited),
            layout = projectLayout,
            defaultStyle = defaultStyle,
        )
        val ass = AssSubtitleWriter.write(
            captions = listOf(moved, inherited),
            layout = projectLayout,
            defaultStyle = defaultStyle,
        )

        assertEquals(CaptionLayout(0.25f, 0.25f, 0.5f), composeRenders[0].layout)
        assertEquals(32, composeRenders[0].style.fontSizeSp)
        assertTrue(ass.contains("Style: Cue0000,sans-serif,32,"))
        assertTrue(
            ass.contains(
                "Dialogue: 0,0:00:01.00,0:00:02.00,Cue0000,,480,480,270,," +
                    "{\\an9\\pos(1440,270)\\q0}Moved\\N{\\c&H00A1E7F4&}移动",
            ),
        )

        assertEquals(projectLayout, composeRenders[1].layout)
        assertEquals(20, composeRenders[1].style.fontSizeSp)
        assertTrue(ass.contains("Style: Cue0001,sans-serif,20,"))
        assertTrue(
            ass.contains(
                "Dialogue: 0,0:00:02.00,0:00:03.00,Cue0001,,192,192,216,," +
                    "{\\an2\\pos(960,864)\\q0}Inherited\\N{\\c&H00A1E7F4&}继承",
            ),
        )
    }
}
