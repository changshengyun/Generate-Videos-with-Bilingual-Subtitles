package com.example.lyriccaptioner.model

import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementOutcome
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState

/** Applies an enhancement result only when the complete batch is still current. */
object CaptionBatchCommitPolicy {
    data class Result(
        val committed: Boolean,
        val state: EditorState,
    )

    fun commit(
        state: EditorState,
        expectedCaptions: List<CaptionCue>,
        outcome: CaptionEnhancementOutcome,
    ): Result {
        // Equality is the optimistic concurrency token: any user edit makes this batch stale.
        if (
            outcome.state == CaptionEnhancementState.CANCELLED ||
            state.captions != expectedCaptions ||
            !isCompleteBatch(expectedCaptions, outcome.captions)
        ) {
            return Result(committed = false, state = state)
        }

        // Derived outputs are invalidated only after the whole, validated batch is ready.
        return Result(
            committed = true,
            state = state.copy(
                captions = outcome.captions,
                captionProcessing = CaptionProcessingSnapshot.from(outcome),
                exportUri = null,
                exportState = ExportState.IDLE,
            ),
        )
    }

    private fun isCompleteBatch(
        expected: List<CaptionCue>,
        actual: List<CaptionCue>,
    ): Boolean {
        if (expected.size != actual.size) return false
        return expected.zip(actual).all { (source, result) ->
            source.id == result.id &&
                source.startMs == result.startMs &&
                source.endMs == result.endMs
        } && actual.map { it.id }.toSet().size == actual.size
    }
}
