package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {
    @Test
    fun parsesBilingualCues() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,000
            I found a love for me
            ${"\u6211\u627e\u5230\u4e86\u5c5e\u4e8e\u6211\u7684\u7231"}

            2
            00:00:03,000 --> 00:00:05,250
            Darling just dive right in
            ${"\u4eb2\u7231\u7684\uff0c\u5c31\u52c7\u6562\u6295\u5165\u5427"}
        """.trimIndent()

        val cues = SrtParser().parse(raw)

        assertEquals(2, cues.size)
        assertEquals(1_500L, cues[0].startMs)
        assertEquals(3_000L, cues[0].endMs)
        assertEquals("I found a love for me", cues[0].english)
        assertEquals("\u6211\u627e\u5230\u4e86\u5c5e\u4e8e\u6211\u7684\u7231", cues[0].chinese)
        assertTrue(cues.all { it.confirmed })
    }

    @Test
    fun parsesEnglishOnlyCuesAsUnconfirmed() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,000
            English only
        """.trimIndent()

        val cue = SrtParser().parse(raw).single()

        assertFalse(cue.confirmed)
    }

    @Test
    fun emptyTextBlocksDoNotBecomeConfirmedCues() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,000

        """.trimIndent()

        assertTrue(SrtParser().parse(raw).isEmpty())
    }

    @Test
    fun roundTripsMergedCuesWithTwoLinesPerLanguage() {
        val cue = CaptionCue(
            id = "cue-0",
            startMs = 1_000L,
            endMs = 5_000L,
            english = "City of stars\nAre you shining just for me",
            chinese = "星光之城\n你是否只为我闪耀",
            confidence = 0.9f,
        )

        val parsed = SrtParser().parse(SrtWriter().write(listOf(cue))).single()

        assertEquals(cue.english, parsed.english)
        assertEquals(cue.chinese, parsed.chinese)
        assertTrue(parsed.confirmed)
    }

    @Test
    fun chineseOnlyBlocksKeepEnglishEmpty() {
        val raw = """
            1
            00:00:01,500 --> 00:00:03,000
            星光之城
            你是否只为我闪耀
        """.trimIndent()

        val cue = SrtParser().parse(raw).single()

        assertEquals("", cue.english)
        assertEquals("星光之城\n你是否只为我闪耀", cue.chinese)
        assertFalse(cue.confirmed)
    }
}
