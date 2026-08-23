package com.example.lyriccaptioner.model

import android.net.TestUri
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementOutcome
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionBatchCommitPolicyTest {
    @Test
    fun completeCloudBatchCommitsOnceAndOnlyThenInvalidatesOldDerivedOutputs() {
        val original = captions()
        val state = EditorState(
            captions = original,
            exportUri = TestUri("old-export"),
            exportState = ExportState.SUCCEEDED,
        )
        val updated = original.map { it.copy(english = "fixed:${it.english}", chinese = "translated") }
        val outcome = CaptionEnhancementOutcome(
            captions = updated,
            source = CaptionResultSource.CLOUD_AI,
            state = CaptionEnhancementState.CLOUD_APPLIED,
            processingVersion = "provider-v1",
        )

        val result = CaptionBatchCommitPolicy.commit(state, expectedCaptions = original, outcome = outcome)

        assertTrue(result.committed)
        assertEquals(updated, result.state.captions)
        assertNull(result.state.exportUri)
        assertEquals(ExportState.IDLE, result.state.exportState)
        assertEquals(CaptionResultSource.CLOUD_AI, result.state.captionProcessing.source)
        assertEquals("provider-v1", result.state.captionProcessing.processingVersion)
    }

    @Test
    fun concurrentUserEditRejectsStaleBatchAndPreservesOldDerivedOutputs() {
        val original = captions()
        val edited = original.mapIndexed { index, cue -> if (index == 0) cue.copy(english = "manual edit") else cue }
        val state = EditorState(
            captions = edited,
            exportUri = TestUri("old-export"),
            exportState = ExportState.SUCCEEDED,
        )
        val outcome = CaptionEnhancementOutcome(
            captions = original.map { it.copy(chinese = "translated") },
            source = CaptionResultSource.LOCAL_FALLBACK,
            state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
            errorKind = CaptionEnhancementErrorKind.OFFLINE,
        )

        val result = CaptionBatchCommitPolicy.commit(state, expectedCaptions = original, outcome = outcome)

        assertFalse(result.committed)
        assertSame(state, result.state)
        assertEquals("old-export", result.state.exportUri.toString())
        assertEquals(ExportState.SUCCEEDED, result.state.exportState)
    }

    @Test
    fun incompleteOutcomeRejectsWholeBatchWithoutInvalidatingOldExport() {
        val original = captions()
        val state = EditorState(captions = original, exportUri = TestUri("old-export"))
        val outcome = CaptionEnhancementOutcome(
            captions = listOf(original.first().copy(chinese = "partial")),
            source = CaptionResultSource.LOCAL_FALLBACK,
            state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
        )

        val result = CaptionBatchCommitPolicy.commit(state, expectedCaptions = original, outcome = outcome)

        assertFalse(result.committed)
        assertSame(state, result.state)
        assertEquals("old-export", result.state.exportUri.toString())
    }

    private fun captions() = listOf(
        CaptionCue("a", 0L, 1_000L, "alpha", "", 0.9f),
        CaptionCue("b", 1_000L, 2_000L, "beta", "", 0.8f),
    )
}
