package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.SubtitleStyle
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

        assertTrue(ass.contains("Style: Default,Arial,30,&H00332211,&H00CCBBAA,&H00000000"))
        assertTrue(ass.contains("Dialogue: 0,0:00:01.23,0:00:04.56"))
        assertTrue(ass.contains("Hello \\{world\\}\\N你好"))
    }
}
