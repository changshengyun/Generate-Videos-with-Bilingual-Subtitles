package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue

class CaptionTimeline(captions: List<CaptionCue>) {
    private val cues = captions.sortedBy { it.startMs }

    fun cueAt(positionMs: Long): CaptionCue? {
        var low = 0
        var high = cues.lastIndex

        while (low <= high) {
            val middle = (low + high).ushr(1)
            val cue = cues[middle]
            when {
                positionMs < cue.startMs -> high = middle - 1
                positionMs >= cue.endMs -> low = middle + 1
                else -> return cue
            }
        }
        return null
    }
}
