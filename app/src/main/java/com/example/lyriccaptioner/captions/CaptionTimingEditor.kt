package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue

class CaptionTimingEditor(
    private val minimumDurationMs: Long = 100L,
) {
    fun shiftStart(
        cue: CaptionCue,
        deltaMs: Long,
        earliestStartMs: Long = 0L,
    ): CaptionCue {
        val latestStart = (cue.endMs - minimumDurationMs).coerceAtLeast(0L)
        val lowerBound = earliestStartMs.coerceIn(0L, latestStart)
        return cue.copy(startMs = (cue.startMs + deltaMs).coerceIn(lowerBound, latestStart))
    }

    fun shiftEnd(
        cue: CaptionCue,
        deltaMs: Long,
        videoDurationMs: Long?,
        latestEndMs: Long? = null,
    ): CaptionCue {
        val earliestEnd = cue.startMs + minimumDurationMs
        val latestEnd = listOfNotNull(videoDurationMs, latestEndMs)
            .minOrNull()
            ?.coerceAtLeast(earliestEnd)
            ?: Long.MAX_VALUE
        return cue.copy(endMs = (cue.endMs + deltaMs).coerceIn(earliestEnd, latestEnd))
    }
}
