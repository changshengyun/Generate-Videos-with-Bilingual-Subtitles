package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CueEditingPolicyTest {
    @Test
    fun changingEnglishPreservesChineseAndClearsCandidatesAndConfirmation() {
        val updated = CueEditingPolicy.updateEnglish(sampleCue(), "Changed English")

        assertEquals("Changed English", updated.english)
        assertEquals("原中文", updated.chinese)
        assertTrue(updated.correctionCandidates.isEmpty())
        assertFalse(updated.confirmed)
    }

    @Test
    fun unchangedEnglishDoesNotInvalidateMatchingBilingualCue() {
        val cue = sampleCue()

        assertSame(cue, CueEditingPolicy.updateEnglish(cue, cue.english))
    }

    @Test
    fun applyingEnglishCorrectionAlsoPreservesExistingChinese() {
        val updated = CueEditingPolicy.applyEnglishCorrection(sampleCue(), "Corrected lyric")

        assertEquals("Corrected lyric", updated.english)
        assertEquals("原中文", updated.chinese)
        assertFalse(updated.confirmed)
    }

    @Test
    fun changingChineseCancelsConfirmationWithoutChangingEnglishOrTiming() {
        val cue = sampleCue()
        val updated = CueEditingPolicy.updateChinese(cue, "新的中文")

        assertEquals(cue.english, updated.english)
        assertEquals(cue.startMs, updated.startMs)
        assertEquals(cue.endMs, updated.endMs)
        assertEquals("新的中文", updated.chinese)
        assertFalse(updated.confirmed)
    }

    @Test
    fun confirmationRequiresBothLanguagesToBeNonBlank() {
        assertFalse(CueEditingPolicy.confirm(sampleCue(chinese = "")).confirmed)
        assertFalse(CueEditingPolicy.confirm(sampleCue(english = "")).confirmed)
        assertTrue(CueEditingPolicy.confirm(sampleCue(confirmed = false)).confirmed)
    }

    @Test
    fun timingChangeCancelsConfirmationButNoOpTimingKeepsIt() {
        val cue = sampleCue()
        val shifted = CueEditingPolicy.updateTiming(cue, cue.copy(startMs = cue.startMs + 100L))

        assertFalse(shifted.confirmed)
        assertTrue(CueEditingPolicy.updateTiming(cue, cue.copy()).confirmed)
    }

    private fun sampleCue(
        english: String = "Original English",
        chinese: String = "原中文",
        confirmed: Boolean = true,
    ) = CaptionCue(
        id = "cue-1",
        startMs = 1_000L,
        endMs = 2_000L,
        english = english,
        chinese = chinese,
        confidence = 0.9f,
        correctionCandidates = listOf("Corrected lyric"),
        confirmed = confirmed,
    )
}
