package com.example.lyriccaptioner.model

import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DerivedOutputPolicyTest {
    @Test
    fun invalidationClearsAllDerivedOutputsAndPreservesEditorContent() {
        val state = EditorState(
            captions = listOf(CaptionCue("cue-1", 0L, 1_000L, "Hello", "你好", 1f, confirmed = true)),
            selectedCaptionId = "cue-1",
            exportUri = TestUri("export"),
            exportState = ExportState.SUCCEEDED,
        )

        val invalidated = DerivedOutputPolicy.invalidateDerivedOutputs(state)

        assertNull(invalidated.exportUri)
        assertEquals(ExportState.IDLE, invalidated.exportState)
        assertEquals(state.captions, invalidated.captions)
        assertEquals(state.selectedCaptionId, invalidated.selectedCaptionId)
    }
}
