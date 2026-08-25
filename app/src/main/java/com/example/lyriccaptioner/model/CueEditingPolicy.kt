package com.example.lyriccaptioner.model

object CueEditingPolicy {
    fun updateEnglish(cue: CaptionCue, text: String): CaptionCue {
        if (cue.english == text) return cue
        return cue.copy(
            english = text,
            correctionCandidates = emptyList(),
            confirmed = false,
        )
    }

    fun updateChinese(cue: CaptionCue, text: String): CaptionCue {
        if (cue.chinese == text) return cue
        return cue.copy(chinese = text, confirmed = false)
    }

    fun applyEnglishCorrection(cue: CaptionCue, candidate: String): CaptionCue {
        if (candidate !in cue.correctionCandidates) return cue
        return updateEnglish(cue, candidate).copy(correctionCandidates = emptyList())
    }

    fun confirm(cue: CaptionCue): CaptionCue {
        return cue.copy(confirmed = cue.canConfirm)
    }

    fun updateTiming(cue: CaptionCue, updated: CaptionCue): CaptionCue {
        return if (cue.startMs == updated.startMs && cue.endMs == updated.endMs) {
            updated
        } else {
            updated.copy(confirmed = false)
        }
    }
}
