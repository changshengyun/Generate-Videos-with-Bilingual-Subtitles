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
        if (actual.map { it.id }.toSet().size != actual.size) return false
        var actualIndex = 0
        expected.forEach { source ->
            val first = actual.getOrNull(actualIndex) ?: return false
            if (first.id == source.id) {
                if (first.startMs != source.startMs || first.endMs != source.endMs) return false
                actualIndex += 1
            } else {
                val second = actual.getOrNull(actualIndex + 1) ?: return false
                if (
                    first.id != "${source.id}:1" || second.id != "${source.id}:2" ||
                    first.startMs != source.startMs || second.endMs != source.endMs ||
                    first.endMs > second.startMs
                ) {
                    return false
                }
                actualIndex += 2
            }
        }
        return actualIndex == actual.size
    }
}
