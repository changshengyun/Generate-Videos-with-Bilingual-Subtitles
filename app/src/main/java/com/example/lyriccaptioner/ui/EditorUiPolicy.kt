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

internal data class AsrEditEntryState(
    val visible: Boolean,
    val captionCount: Int,
)

internal fun asrEditEntryState(
    status: String,
    captionCount: Int,
    asrRunning: Boolean,
    isWorking: Boolean,
): AsrEditEntryState {
    val completedLocalAsr = status.startsWith(LOCAL_ASR_SUCCESS_PREFIX)
    val visible = completedLocalAsr && captionCount > 0 && !asrRunning && !isWorking
    return AsrEditEntryState(visible = visible, captionCount = if (visible) captionCount else 0)
}

internal fun showsCaptionList(activeSection: Int): Boolean =
    activeSection == EditorSection.CAPTIONS.index

internal fun orderedCaptionEditorItems(captions: List<CaptionCue>): List<CaptionCue> =
    captions.sortedWith(
        compareBy<CaptionCue> { it.startMs }
            .thenBy { it.endMs }
            .thenBy { captionChildSequence(it.id) }
            .thenBy { it.id },
    )

internal fun captionEditorLazyItemIndex(
    orderedCaptions: List<CaptionCue>,
    selectedCaptionId: String?,
    headerItemCount: Int,
): Int? {
    val cueIndex = orderedCaptions.indexOfFirst { it.id == selectedCaptionId }
    return cueIndex.takeIf { it >= 0 }?.plus(headerItemCount)
}

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

private const val LOCAL_ASR_SUCCESS_PREFIX = "Local Whisper JNI generated "
