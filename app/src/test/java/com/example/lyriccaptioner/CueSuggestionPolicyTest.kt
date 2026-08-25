package com.example.lyriccaptioner

import android.net.TestUri
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CueSuggestionPolicyTest {
    @Test
    fun applyingSuggestionChangesOnlyTargetAndInvalidatesOldExport() {
        val first = cue("parent:1", "draft one", "草稿一")
        val second = cue("parent:2", "draft two", "草稿二")
        val state = EditorState(
            captions = listOf(first, second),
            exportUri = TestUri("content://media/old"),
            exportState = ExportState.SUCCEEDED,
        )

        val updated = state.withAppliedCueSuggestion(
            CaptionCueSuggestion("parent:1", "repaired one", "修复一", canonicalVerified = true),
        )

        assertEquals("repaired one", updated.captions[0].english)
        assertEquals("修复一", updated.captions[0].chinese)
        assertFalse(updated.captions[0].confirmed)
        assertEquals(second, updated.captions[1])
        assertNull(updated.exportUri)
        assertEquals(ExportState.IDLE, updated.exportState)
    }

    private fun cue(id: String, english: String, chinese: String) = CaptionCue(
        id = id,
        startMs = 0L,
        endMs = 1_000L,
        english = english,
        chinese = chinese,
        confidence = 0.9f,
        confirmed = true,
    )
}
