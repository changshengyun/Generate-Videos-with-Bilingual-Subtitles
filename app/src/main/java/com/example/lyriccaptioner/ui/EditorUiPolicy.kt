package com.example.lyriccaptioner.ui

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ResolvedCaptionStyle
import com.example.lyriccaptioner.model.resolveCaptionStyle

internal enum class EditorSection(val index: Int) {
    IMPORT(0),
    ASR(1),
    CAPTIONS(2),
    EXPORT(3),
}

internal fun orderedCaptionEditorItems(captions: List<CaptionCue>): List<CaptionCue> =
    captions.sortedWith(
        compareBy<CaptionCue> { it.startMs }
            .thenBy { it.endMs }
            .thenBy { captionChildSequence(it.id) }
            .thenBy { it.id },
    )

private fun captionChildSequence(cueId: String): Int =
    cueId.substringAfterLast(':', missingDelimiterValue = "")
        .toIntOrNull()
        ?: Int.MAX_VALUE

internal data class CaptionStyleUiState(
    val resolved: ResolvedCaptionStyle,
    val hasOverride: Boolean,
)

internal fun captionStyleUiState(
    defaultStyle: DefaultCaptionStyle,
    cue: CaptionCue,
): CaptionStyleUiState = CaptionStyleUiState(
    resolved = resolveCaptionStyle(defaultStyle, cue.styleOverride),
    // A cue override includes both visual style and placement.  In particular,
    // a position-only edit must keep the card's clear action enabled.
    hasOverride = cue.styleOverride?.isEmpty == false || cue.layoutOverride?.isEmpty == false,
)
