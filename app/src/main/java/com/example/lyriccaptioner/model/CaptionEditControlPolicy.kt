package com.example.lyriccaptioner.model

/** The neighbor selected by a manual merge action. */
enum class CaptionMergeDirection {
    PREVIOUS,
    NEXT,
}

/**
 * Merge exactly two adjacent cues in timeline order. Visual overrides come from the cue on which
 * the user initiated the action, while content, time and confidence are combined deterministically.
 */
fun EditorState.mergeCaptionCue(
    cueId: String,
    direction: CaptionMergeDirection,
): EditorState {
    val ordered = captions.sortedWith(
        compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.id },
    )
    val selectedIndex = ordered.indexOfFirst { it.id == cueId }
    if (selectedIndex < 0) return this
    val neighborIndex = when (direction) {
        CaptionMergeDirection.PREVIOUS -> selectedIndex - 1
        CaptionMergeDirection.NEXT -> selectedIndex + 1
    }
    val neighbor = ordered.getOrNull(neighborIndex) ?: return this
    val selected = ordered[selectedIndex]
    val first = if (selectedIndex < neighborIndex) selected else neighbor
    val second = if (selectedIndex < neighborIndex) neighbor else selected
    val occupiedIds = ordered
        .asSequence()
        .filterNot { it.id == first.id || it.id == second.id }
        .map(CaptionCue::id)
        .toSet()
    val mergedId = restoredParentId(first.id, second.id)
        ?.takeUnless(occupiedIds::contains)
        ?: first.id
    val merged = CaptionCue(
        id = mergedId,
        startMs = minOf(first.startMs, second.startMs),
        endMs = maxOf(first.endMs, second.endMs),
        english = listOf(first.english.trim(), second.english.trim())
            .filter(String::isNotEmpty)
            .joinToString(" "),
        chinese = listOf(first.chinese.trim(), second.chinese.trim())
            .filter(String::isNotEmpty)
            .joinToString(""),
        confidence = minOf(first.confidence, second.confidence),
        correctionCandidates = emptyList(),
        confirmed = false,
        styleOverride = selected.styleOverride,
        layoutOverride = selected.layoutOverride,
    )
    val mergedCaptions = ordered
        .filterNot { it.id == first.id || it.id == second.id }
        .plus(merged)
        .sortedWith(compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
    return DerivedOutputPolicy.invalidateDerivedOutputs(
        copy(
            captions = mergedCaptions,
            selectedCaptionId = merged.id,
            status = "字幕已合并，可继续拆分、编辑或执行 AI 增强。",
        ),
    )
}

private fun restoredParentId(firstId: String, secondId: String): String? {
    val firstSuffix = firstId.substringAfterLast(':', missingDelimiterValue = "")
    val secondSuffix = secondId.substringAfterLast(':', missingDelimiterValue = "")
    val firstParent = firstId.substringBeforeLast(':', missingDelimiterValue = "")
    val secondParent = secondId.substringBeforeLast(':', missingDelimiterValue = "")
    return firstParent.takeIf {
        it.isNotEmpty() && it == secondParent && setOf(firstSuffix, secondSuffix) == setOf("1", "2")
    }
}

/** Global direct-position edit: update the default and clear only X/Y cue overrides. */
fun EditorState.withGlobalDirectPosition(xRatio: Float, yRatio: Float): EditorState {
    val widestRetainedBox = maxOf(
        captionLayout.widthRatio,
        captions.maxOfOrNull { it.layoutOverride?.widthRatio ?: captionLayout.widthRatio }
            ?: captionLayout.widthRatio,
    )
    val requestedX = xRatio.takeIf(Float::isFinite) ?: captionLayout.xRatio
    val requestedY = yRatio.takeIf(Float::isFinite) ?: captionLayout.yRatio
    val updatedLayout = captionLayout.copy(
        xRatio = requestedX.coerceIn(0f, 1f - widestRetainedBox),
        yRatio = requestedY.coerceIn(0f, 1f),
    )
    return updateGlobalLayout(updatedLayout) { override ->
        override.copy(xRatio = null, yRatio = null)
    }
}

/** Global width edit: update the default and clear only width cue overrides. */
fun EditorState.withGlobalDirectWidth(widthRatio: Float): EditorState {
    val furthestRetainedX = maxOf(
        captionLayout.xRatio,
        captions.maxOfOrNull { it.layoutOverride?.xRatio ?: captionLayout.xRatio }
            ?: captionLayout.xRatio,
    )
    val maximum = 1f - furthestRetainedX
    val minimum = MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO.coerceAtMost(maximum)
    val requested = widthRatio.takeIf(Float::isFinite) ?: captionLayout.widthRatio
    val updatedLayout = captionLayout.copy(widthRatio = requested.coerceIn(minimum, maximum))
    return updateGlobalLayout(updatedLayout) { override -> override.copy(widthRatio = null) }
}

/** Global font-size edit: update the default and clear only both font-size projections. */
fun EditorState.withGlobalDirectFontSize(fontSizeRatio: Float): EditorState {
    val updatedStyle = defaultCaptionStyle.withFontSizeRatio(fontSizeRatio)
    return updateGlobalStyle(updatedStyle) { override ->
        override.copy(fontSizeSp = null, fontSizeRatio = null)
    }
}

fun EditorState.withGlobalFontSizeDelta(deltaSp: Int): EditorState {
    val ratio = adjustCaptionFontSizeRatio(defaultCaptionStyle.validated().fontSizeRatio, deltaSp)
    return withGlobalDirectFontSize(ratio)
}

fun EditorState.withGlobalEnglishColor(colorHex: String): EditorState {
    val updated = defaultCaptionStyle.copy(
        primaryColorHex = normalizeSubtitleColor(colorHex, defaultCaptionStyle.primaryColorHex),
    )
    return updateGlobalStyle(updated) { it.copy(primaryColorHex = null) }
}

fun EditorState.withGlobalChineseColor(colorHex: String): EditorState {
    val updated = defaultCaptionStyle.copy(
        secondaryColorHex = normalizeSubtitleColor(colorHex, defaultCaptionStyle.secondaryColorHex),
    )
    return updateGlobalStyle(updated) { it.copy(secondaryColorHex = null) }
}

fun EditorState.withGlobalOutlineColor(colorHex: String): EditorState {
    val updated = defaultCaptionStyle.copy(
        outlineColorHex = normalizeSubtitleColor(colorHex, defaultCaptionStyle.outlineColorHex),
    )
    return updateGlobalStyle(updated) { it.copy(outlineColorHex = null) }
}

fun EditorState.withGlobalFontFamily(fontFamily: String): EditorState {
    val supported = setOf(SUBTITLE_FONT_SANS, SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO)
    if (fontFamily !in supported) return this
    return updateGlobalStyle(defaultCaptionStyle.copy(fontFamily = fontFamily)) {
        it.copy(fontFamily = null)
    }
}

fun EditorState.withGlobalBoldToggled(): EditorState =
    updateGlobalStyle(defaultCaptionStyle.copy(bold = !defaultCaptionStyle.bold)) {
        it.copy(bold = null)
    }

fun EditorState.withGlobalItalicToggled(): EditorState =
    updateGlobalStyle(defaultCaptionStyle.copy(italic = !defaultCaptionStyle.italic)) {
        it.copy(italic = null)
    }

fun EditorState.withGlobalAlignment(alignment: CaptionAlignment): EditorState =
    updateGlobalStyle(defaultCaptionStyle.copy(alignment = alignment)) {
        it.copy(alignment = null)
    }

fun EditorState.withGlobalVerticalPosition(delta: Int): EditorState {
    val updated = captionLayout.copy(yRatio = (captionLayout.yRatio - delta / 100f).coerceIn(0f, 1f))
    return updateGlobalLayout(updated) { it.copy(yRatio = null) }
}

fun EditorState.withGlobalWidthDelta(delta: Float): EditorState =
    withGlobalDirectWidth(captionLayout.widthRatio + delta)

fun EditorState.withGlobalBasicStyle(preset: CaptionBasicStylePreset): EditorState {
    val updated = defaultCaptionStyle.toOverride().withBasicStylePreset(preset)
    val nextDefault = defaultCaptionStyle.copy(
        primaryColorHex = updated.primaryColorHex ?: defaultCaptionStyle.primaryColorHex,
        secondaryColorHex = updated.secondaryColorHex ?: defaultCaptionStyle.secondaryColorHex,
        outlineColorHex = updated.outlineColorHex ?: defaultCaptionStyle.outlineColorHex,
        outlineWidthRatio = updated.outlineWidthRatio ?: defaultCaptionStyle.outlineWidthRatio,
        backgroundEnabled = updated.backgroundEnabled ?: defaultCaptionStyle.backgroundEnabled,
        backgroundColorHex = updated.backgroundColorHex ?: defaultCaptionStyle.backgroundColorHex,
    )
    return updateGlobalStyle(nextDefault) { override ->
        override.copy(
            primaryColorHex = null,
            secondaryColorHex = null,
            outlineColorHex = null,
            outlineWidthRatio = null,
            backgroundEnabled = null,
            backgroundColorHex = null,
        )
    }
}

fun EditorState.clearAllCaptionOverrides(): EditorState {
    val updated = captions.map { cue -> cue.copy(styleOverride = null, layoutOverride = null) }
    if (updated == captions) return this
    return DerivedOutputPolicy.invalidateDerivedOutputs(copy(captions = updated))
}

private fun DefaultCaptionStyle.toOverride(): CaptionStyleOverride = CaptionStyleOverride(
    primaryColorHex = primaryColorHex,
    secondaryColorHex = secondaryColorHex,
    outlineColorHex = outlineColorHex,
    fontFamily = fontFamily,
    bold = bold,
    italic = italic,
    alignment = alignment,
    fontSizeRatio = fontSizeRatio,
    outlineWidthRatio = outlineWidthRatio,
    backgroundEnabled = backgroundEnabled,
    backgroundColorHex = backgroundColorHex,
)

private fun EditorState.updateGlobalStyle(
    updatedStyle: DefaultCaptionStyle,
    clearProperty: (CaptionStyleOverride) -> CaptionStyleOverride,
): EditorState {
    val canonical = updatedStyle.validated()
    val updatedCaptions = captions.map { cue ->
        val cleared = cue.styleOverride?.let(clearProperty)?.validated()
        cue.copy(styleOverride = cleared?.takeUnless { it.isEmpty })
    }
    if (canonical == defaultCaptionStyle.validated() && updatedCaptions == captions) return this
    val updated = copy(
        defaultCaptionStyle = canonical,
        captions = updatedCaptions,
        exportProfile = exportProfile.copy(
            subtitleStyle = exportProfile.subtitleStyle.copy(
                fontSizeSp = canonical.fontSizeSp,
                primaryColorHex = canonical.primaryColorHex,
                secondaryColorHex = canonical.secondaryColorHex,
                outlineColorHex = canonical.outlineColorHex,
                fontFamily = canonical.fontFamily,
            ),
        ),
    )
    return DerivedOutputPolicy.invalidateDerivedOutputs(updated)
}

private fun EditorState.updateGlobalLayout(
    updatedLayout: CaptionLayout,
    clearProperty: (CaptionLayoutOverride) -> CaptionLayoutOverride,
): EditorState {
    val updatedCaptions = captions.map { cue ->
        val cleared = cue.layoutOverride?.let(clearProperty)
        cue.copy(layoutOverride = cleared?.takeUnless { it.isEmpty })
    }
    if (updatedLayout == captionLayout && updatedCaptions == captions) return this
    val bottomMargin = ((1f - updatedLayout.yRatio) * 100f).toInt().coerceIn(4, 28)
    return DerivedOutputPolicy.invalidateDerivedOutputs(
        copy(
            captionLayout = updatedLayout,
            captions = updatedCaptions,
            exportProfile = exportProfile.copy(
                subtitleStyle = exportProfile.subtitleStyle.copy(bottomMarginPercent = bottomMargin),
            ),
        ),
    )
}
