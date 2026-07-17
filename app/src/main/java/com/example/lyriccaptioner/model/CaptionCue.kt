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
) {
    val canConfirm: Boolean
        get() = english.isNotBlank() && chinese.isNotBlank()

    val needsReview: Boolean
        get() = confidence < 0.82f || correctionCandidates.isNotEmpty()
}
