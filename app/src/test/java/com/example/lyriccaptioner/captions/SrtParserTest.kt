package com.example.lyriccaptioner.captions

import org.junit.Assert.assertEquals
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
    }
}
