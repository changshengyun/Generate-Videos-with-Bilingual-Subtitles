package com.example.lyriccaptioner.model

import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementOutcome
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import com.example.lyriccaptioner.processing.enhancement.CaptionProcessingLevel
import com.example.lyriccaptioner.processing.enhancement.SongMatch

/** Persistable, non-sensitive summary of the most recent caption processing batch. */
data class CaptionProcessingSnapshot(
    val state: CaptionEnhancementState = CaptionEnhancementState.RAW_ASR_READY,
    val source: CaptionResultSource = CaptionResultSource.RAW_ASR,
    val processingVersion: String? = null,
    val lastErrorKind: CaptionEnhancementErrorKind? = null,
    val songMatch: SongMatch? = null,
    val processingLevel: CaptionProcessingLevel = CaptionProcessingLevel.LEGACY_UNKNOWN,
) {
    companion object {
        fun from(outcome: CaptionEnhancementOutcome): CaptionProcessingSnapshot =
            CaptionProcessingSnapshot(
                state = outcome.state,
                source = outcome.source,
                processingVersion = outcome.processingVersion,
                lastErrorKind = outcome.errorKind,
                songMatch = outcome.songMatch,
                processingLevel = outcome.processingLevel,
            )
    }
}
