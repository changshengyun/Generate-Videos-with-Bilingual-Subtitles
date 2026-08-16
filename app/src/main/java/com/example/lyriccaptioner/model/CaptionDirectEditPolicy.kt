package com.example.lyriccaptioner.model

/** Smallest text-box width used by direct editing while horizontal room permits it. */
const val MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO = 0.15f

const val CAPTION_STYLE_DARK_HEX = "#000000"
const val CAPTION_STYLE_LIGHT_HEX = "#FFFFFF"
const val CAPTION_STYLE_GRAY_HEX = "#4A4A4A"

/** Stable, deliberately small set of basic styles exposed by the direct editor. */
enum class CaptionBasicStylePreset {
    PLAIN_TEXT,
    DARK_OUTLINE,
    LIGHT_OUTLINE,
    LIGHT_BACKGROUND,
    DARK_BACKGROUND,
    GRAY_BACKGROUND,
}

/**
 * Move a resolved source-video layout to a normalized coordinate.
 * Invalid inputs preserve the corresponding current coordinate, and the box is kept in-frame.
 */
fun CaptionLayout.movedToDirectEditPosition(
    xRatio: Float,
    yRatio: Float,
): CaptionLayout {
    val requestedX = xRatio.takeIf { it.isFinite() } ?: this.xRatio
    val requestedY = yRatio.takeIf { it.isFinite() } ?: this.yRatio
    return copy(
        xRatio = requestedX.coerceIn(0f, 1f - widthRatio),
        yRatio = requestedY.coerceIn(0f, 1f),
    )
}

/**
 * Resize from the right edge while retaining the left edge. If a legacy box is too near the
 * right edge to admit the normal minimum, the remaining in-frame width is the effective minimum.
 */
fun CaptionLayout.withDirectEditWidth(widthRatio: Float): CaptionLayout {
    val maximum = 1f - xRatio
    val minimum = MIN_DIRECT_EDIT_CAPTION_WIDTH_RATIO.coerceAtMost(maximum)
    val requested = widthRatio.takeIf { it.isFinite() } ?: this.widthRatio
    return copy(widthRatio = requested.coerceIn(minimum, maximum))
}

/** Adjust only the canonical source-relative font size of a cue override. */
fun CaptionStyleOverride.withDirectEditFontSize(fontSizeRatio: Float): CaptionStyleOverride =
    withFontSizeRatio(fontSizeRatio)

/** Apply one user-selected color to both lines of the bilingual cue. */
fun CaptionStyleOverride.withUnifiedTextColor(colorHex: String): CaptionStyleOverride {
    val color = normalizeSubtitleColor(colorHex, CAPTION_STYLE_LIGHT_HEX)
    return copy(primaryColorHex = color, secondaryColorHex = color)
}

/** Apply one user-selected color to both lines of the project default style. */
fun DefaultCaptionStyle.withUnifiedTextColor(colorHex: String): DefaultCaptionStyle {
    val color = normalizeSubtitleColor(colorHex, CAPTION_STYLE_LIGHT_HEX)
    return copy(primaryColorHex = color, secondaryColorHex = color)
}

/**
 * Apply only the visual fields owned by a basic-style preset. Font family, font size and alignment
 * are intentionally preserved so the basic-style panel cannot behave like a font/template picker.
 */
fun CaptionStyleOverride.withBasicStylePreset(
    preset: CaptionBasicStylePreset,
): CaptionStyleOverride = when (preset) {
    CaptionBasicStylePreset.PLAIN_TEXT -> copy(
        outlineWidthRatio = MIN_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = false,
    )
    CaptionBasicStylePreset.DARK_OUTLINE -> copy(
        primaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        secondaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        outlineColorHex = CAPTION_STYLE_DARK_HEX,
        outlineWidthRatio = DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = false,
    )
    CaptionBasicStylePreset.LIGHT_OUTLINE -> copy(
        primaryColorHex = CAPTION_STYLE_DARK_HEX,
        secondaryColorHex = CAPTION_STYLE_DARK_HEX,
        outlineColorHex = CAPTION_STYLE_LIGHT_HEX,
        outlineWidthRatio = DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = false,
    )
    CaptionBasicStylePreset.LIGHT_BACKGROUND -> copy(
        primaryColorHex = CAPTION_STYLE_DARK_HEX,
        secondaryColorHex = CAPTION_STYLE_DARK_HEX,
        outlineWidthRatio = MIN_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = true,
        backgroundColorHex = CAPTION_STYLE_LIGHT_HEX,
    )
    CaptionBasicStylePreset.DARK_BACKGROUND -> copy(
        primaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        secondaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        outlineWidthRatio = MIN_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = true,
        backgroundColorHex = CAPTION_STYLE_DARK_HEX,
    )
    CaptionBasicStylePreset.GRAY_BACKGROUND -> copy(
        primaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        secondaryColorHex = CAPTION_STYLE_LIGHT_HEX,
        outlineWidthRatio = MIN_CAPTION_OUTLINE_WIDTH_RATIO,
        backgroundEnabled = true,
        backgroundColorHex = CAPTION_STYLE_GRAY_HEX,
    )
}
