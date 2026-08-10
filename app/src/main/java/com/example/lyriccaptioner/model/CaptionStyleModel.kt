package com.example.lyriccaptioner.model

enum class CaptionAlignment {
    LEFT,
    CENTER,
    RIGHT,
}

data class CaptionLayout(
    val xRatio: Float = DEFAULT_X_RATIO,
    val yRatio: Float = DEFAULT_Y_RATIO,
    val widthRatio: Float = DEFAULT_WIDTH_RATIO,
) {
    init {
        require(xRatio.isFinite() && yRatio.isFinite() && widthRatio.isFinite()) {
            "Caption layout coordinates must be finite"
        }
        require(xRatio in 0f..1f) { "Caption layout xRatio must be normalized" }
        require(yRatio in 0f..1f) { "Caption layout yRatio must be normalized" }
        require(widthRatio > 0f && widthRatio <= 1f) {
            "Caption layout widthRatio must be normalized and positive"
        }
        require(xRatio + widthRatio <= 1f + NORMALIZED_EPSILON) {
            "Caption layout must fit within the source video width"
        }
    }

    companion object {
        const val DEFAULT_X_RATIO = 0.05f
        const val DEFAULT_Y_RATIO = 0.88f
        const val DEFAULT_WIDTH_RATIO = 0.90f
        private const val NORMALIZED_EPSILON = 0.000_001f
    }
}

data class DefaultCaptionStyle(
    val fontSizeSp: Int = 24,
    val primaryColorHex: String = "#FFFFFF",
    val secondaryColorHex: String = "#F4E7A1",
    val outlineColorHex: String = "#000000",
    val fontFamily: String = SUBTITLE_FONT_SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: CaptionAlignment = CaptionAlignment.CENTER,
)

data class CaptionStyleOverride(
    val fontSizeSp: Int? = null,
    val primaryColorHex: String? = null,
    val secondaryColorHex: String? = null,
    val outlineColorHex: String? = null,
    val fontFamily: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val alignment: CaptionAlignment? = null,
) {
    val isEmpty: Boolean
        get() = fontSizeSp == null &&
            primaryColorHex == null &&
            secondaryColorHex == null &&
            outlineColorHex == null &&
            fontFamily == null &&
            bold == null &&
            italic == null &&
            alignment == null
}

data class ResolvedCaptionStyle(
    val fontSizeSp: Int,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val outlineColorHex: String,
    val fontFamily: String,
    val bold: Boolean,
    val italic: Boolean,
    val alignment: CaptionAlignment,
)

fun resolveCaptionStyle(
    defaultStyle: DefaultCaptionStyle,
    override: CaptionStyleOverride?,
): ResolvedCaptionStyle {
    val safeDefault = defaultStyle.validated()
    val safeOverride = override?.validated()
    return ResolvedCaptionStyle(
        fontSizeSp = safeOverride?.fontSizeSp ?: safeDefault.fontSizeSp,
        primaryColorHex = safeOverride?.primaryColorHex ?: safeDefault.primaryColorHex,
        secondaryColorHex = safeOverride?.secondaryColorHex ?: safeDefault.secondaryColorHex,
        outlineColorHex = safeOverride?.outlineColorHex ?: safeDefault.outlineColorHex,
        fontFamily = safeOverride?.fontFamily ?: safeDefault.fontFamily,
        bold = safeOverride?.bold ?: safeDefault.bold,
        italic = safeOverride?.italic ?: safeDefault.italic,
        alignment = safeOverride?.alignment ?: safeDefault.alignment,
    )
}

fun DefaultCaptionStyle.validated(): DefaultCaptionStyle = copy(
    fontSizeSp = fontSizeSp.coerceIn(MIN_CAPTION_FONT_SIZE_SP, MAX_CAPTION_FONT_SIZE_SP),
    primaryColorHex = normalizeSubtitleColor(primaryColorHex, "#FFFFFF"),
    secondaryColorHex = normalizeSubtitleColor(secondaryColorHex, "#F4E7A1"),
    outlineColorHex = normalizeSubtitleColor(outlineColorHex, "#000000"),
    fontFamily = fontFamily.validatedCaptionFontFamily(),
)

fun CaptionStyleOverride.validated(): CaptionStyleOverride = copy(
    fontSizeSp = fontSizeSp?.coerceIn(MIN_CAPTION_FONT_SIZE_SP, MAX_CAPTION_FONT_SIZE_SP),
    primaryColorHex = primaryColorHex?.let { normalizeSubtitleColor(it, "#FFFFFF") },
    secondaryColorHex = secondaryColorHex?.let { normalizeSubtitleColor(it, "#F4E7A1") },
    outlineColorHex = outlineColorHex?.let { normalizeSubtitleColor(it, "#000000") },
    fontFamily = fontFamily?.validatedCaptionFontFamily(),
)

fun SubtitleStyle.toDefaultCaptionStyle(): DefaultCaptionStyle {
    val legacy = validated()
    return DefaultCaptionStyle(
        fontSizeSp = legacy.fontSizeSp,
        primaryColorHex = legacy.primaryColorHex,
        secondaryColorHex = legacy.secondaryColorHex,
        outlineColorHex = legacy.outlineColorHex,
        fontFamily = legacy.fontFamily,
    )
}

fun SubtitleStyle.toCaptionLayout(): CaptionLayout {
    val legacy = validated()
    return CaptionLayout(
        xRatio = CaptionLayout.DEFAULT_X_RATIO,
        yRatio = (1f - legacy.bottomMarginPercent / 100f).coerceIn(0f, 1f),
        widthRatio = CaptionLayout.DEFAULT_WIDTH_RATIO,
    )
}

private fun String.validatedCaptionFontFamily(): String = when (this) {
    SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO -> this
    else -> SUBTITLE_FONT_SANS
}

const val MIN_CAPTION_FONT_SIZE_SP = 14
const val MAX_CAPTION_FONT_SIZE_SP = 48
