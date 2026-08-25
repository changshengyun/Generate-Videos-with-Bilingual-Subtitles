package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue

data class CaptionCueSuggestionRequest(
    val jobId: String,
    val target: CaptionCue,
    val sibling: CaptionCue?,
    val previous: CaptionCue?,
    val next: CaptionCue?,
    val batch: List<CaptionCue>,
    val songMatch: SongMatch?,
)

data class CaptionCueSuggestion(
    val cueId: String,
    val english: String,
    val chinese: String,
    val canonicalVerified: Boolean,
)

fun interface CaptionCueSuggestionService {
    suspend fun suggest(request: CaptionCueSuggestionRequest): CaptionCueSuggestion
}

data class CaptionCueSuggestionUiState(
    val cueId: String? = null,
    val running: Boolean = false,
    val proposal: CaptionCueSuggestion? = null,
    val error: String? = null,
    val expectedCaptions: List<CaptionCue>? = null,
)
