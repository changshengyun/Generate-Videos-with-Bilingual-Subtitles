package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LyricLineAlignerTest {
    private val aligner = LyricLineAligner()

    @Test
    fun matchesSongLinesDespiteSmallRecognitionErrors() {
        val matches = aligner.align(
            captions = listOf(
                cue("one", "I found a love for me"),
                cue("two", "Darling just dive right inn"),
                cue("three", "Follow my lead"),
            ),
            lyricLines = listOf(
                "I found a love for me",
                "Darling just dive right in",
                "Follow my lead",
            ),
        )

        assertEquals("Darling just dive right in", matches.getValue("two").lyric)
        assertEquals(1.0, matches.getValue("one").similarity, 0.0)
    }

    @Test
    fun skipsUnrelatedCaptionWithoutShiftingLaterLines() {
        val matches = aligner.align(
            captions = listOf(
                cue("one", "I found a love for me"),
                cue("noise", "background crowd talking"),
                cue("three", "Follow my lead"),
            ),
            lyricLines = listOf("I found a love for me", "Follow my lead"),
        )

        assertEquals("Follow my lead", matches.getValue("three").lyric)
        assertFalse(matches.containsKey("noise"))
    }

    private fun cue(id: String, english: String) = CaptionCue(
        id = id,
        startMs = 0L,
        endMs = 1_000L,
        english = english,
        chinese = "",
        confidence = 0.5f,
    )
}
