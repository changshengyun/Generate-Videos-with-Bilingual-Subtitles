package com.example.lyriccaptioner.ui

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiPolicyTest {
    @Test
    fun successfulNonEmptyAsrShowsExplicitEditEntryWithoutChangingSection() {
        val activeSection = EditorSection.ASR.index

        val entry = asrEditEntryState(
            status = "Local Whisper JNI generated 2 English captions.",
            captionCount = 2,
            asrRunning = false,
            isWorking = false,
        )

        assertTrue(entry.visible)
        assertEquals(2, entry.captionCount)
        assertEquals(EditorSection.ASR.index, activeSection)
        assertFalse(showsCaptionList(activeSection))
    }

    @Test
    fun cancellationFailureEmptyResultAndRestoreNeverShowSuccessEntry() {
        val statuses = listOf(
            "ASR cancelled; temporary audio was cleaned." to 3,
            "ASR failed (LOCAL): native error" to 3,
            "Local Whisper JNI generated 0 English captions." to 0,
            "Project restored with persistent video access." to 3,
        )

        statuses.forEach { (status, count) ->
            assertFalse(
                status,
                asrEditEntryState(status, count, asrRunning = false, isWorking = false).visible,
            )
        }
    }

    @Test
    fun runningOrWorkingStateCannotExposeSuccessEntry() {
        val status = "Local Whisper JNI generated 1 English captions."

        assertFalse(asrEditEntryState(status, 1, asrRunning = true, isWorking = true).visible)
        assertFalse(asrEditEntryState(status, 1, asrRunning = false, isWorking = true).visible)
    }

    @Test
    fun captionListBelongsOnlyToCaptionEditorSection() {
        assertFalse(showsCaptionList(EditorSection.IMPORT.index))
        assertFalse(showsCaptionList(EditorSection.ASR.index))
        assertTrue(showsCaptionList(EditorSection.CAPTIONS.index))
        assertFalse(showsCaptionList(EditorSection.EXPORT.index))
    }

    @Test
    fun selectedCueUiResolvesDefaultsOverrideAndClearWithoutAffectingOtherCue() {
        val defaults = DefaultCaptionStyle(fontSizeSp = 24, primaryColorHex = "#FFFFFF")
        val overridden = cue("one").copy(
            styleOverride = CaptionStyleOverride(fontSizeSp = 30, primaryColorHex = "#61D6FF"),
        )
        val sibling = cue("two")

        val selectedUi = captionStyleUiState(defaults, overridden)
        val siblingUi = captionStyleUiState(defaults, sibling)
        val clearedUi = captionStyleUiState(defaults, overridden.copy(styleOverride = null))

        assertTrue(selectedUi.hasOverride)
        assertEquals(30, selectedUi.resolved.fontSizeSp)
        assertEquals("#61D6FF", selectedUi.resolved.primaryColorHex)
        assertFalse(siblingUi.hasOverride)
        assertEquals(24, siblingUi.resolved.fontSizeSp)
        assertFalse(clearedUi.hasOverride)
        assertEquals(24, clearedUi.resolved.fontSizeSp)
    }

    @Test
    fun textTimingAndConfirmationCopiesPreserveCueOverride() {
        val override = CaptionStyleOverride(fontSizeSp = 28, bold = true)
        val original = cue("one").copy(styleOverride = override)

        val edited = original.copy(
            startMs = 100L,
            endMs = 1_200L,
            english = "edited",
            chinese = "已编辑",
            confirmed = true,
        )

        assertEquals(override, edited.styleOverride)
    }

    private fun cue(id: String) = CaptionCue(
        id = id,
        startMs = 0L,
        endMs = 1_000L,
        english = "line",
        chinese = "字幕",
        confidence = 0.9f,
    )
}
