package com.example.lyriccaptioner.model

data class CaptionCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val english: String,
    val chinese: String,
    val confidence: Float,
    val correctionCandidates: List<String> = emptyList(),
    val confirmed: Boolean = false,
    val styleOverride: CaptionStyleOverride? = null,
    val layoutOverride: CaptionLayoutOverride? = null,
) {
    val canConfirm: Boolean
        get() = english.isNotBlank() && chinese.isNotBlank()

    val needsReview: Boolean
        get() = confidence < 0.82f || correctionCandidates.isNotEmpty()
}

/** Clear both visual and placement overrides for exactly one stable cue id. */
internal fun List<CaptionCue>.clearOverridesForCue(cueId: String): List<CaptionCue> = map { cue ->
    if (cue.id == cueId) cue.copy(styleOverride = null, layoutOverride = null) else cue
}
