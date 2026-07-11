package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Test

class SrtWriterTest {
    @Test
    fun writesBilingualCuesInStartTimeOrder() {
        val captions = listOf(
            CaptionCue(
                id = "b",
                startMs = 2_000,
                endMs = 3_250,
                english = "Follow my lead",
                chinese = "\u8ddf\u968f\u6211\u7684\u8282\u594f",
                confidence = 1.0f,
            ),
            CaptionCue(
                id = "a",
                startMs = 0,
                endMs = 1_500,
                english = "I found a love",
                chinese = "\u6211\u627e\u5230\u4e86\u7231",
                confidence = 1.0f,
            ),
        )

        val output = SrtWriter().write(captions)

        assertEquals(
            """
            1
            00:00:00,000 --> 00:00:01,500
            I found a love
            ${"\u6211\u627e\u5230\u4e86\u7231"}

            2
            00:00:02,000 --> 00:00:03,250
            Follow my lead
            ${"\u8ddf\u968f\u6211\u7684\u8282\u594f"}

            """.trimIndent().trimEnd(),
            output.trimEnd(),
        )
    }
}
