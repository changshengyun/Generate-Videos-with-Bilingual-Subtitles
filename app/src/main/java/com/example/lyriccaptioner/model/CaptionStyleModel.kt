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

enum class CaptionVerticalAnchor {
    TOP,
    MIDDLE,
    BOTTOM,
}

/** Shared source-video anchor semantics for Compose preview and ASS export. */
fun CaptionLayout.verticalAnchor(): CaptionVerticalAnchor = when {
    yRatio < 1f / 3f -> CaptionVerticalAnchor.TOP
    yRatio > 2f / 3f -> CaptionVerticalAnchor.BOTTOM
    else -> CaptionVerticalAnchor.MIDDLE
}

/** Offset from the corresponding top/center/bottom parent anchor to the cue y coordinate. */
fun CaptionLayout.verticalAnchorOffsetRatio(): Float = when (verticalAnchor()) {
    CaptionVerticalAnchor.TOP -> yRatio
    CaptionVerticalAnchor.MIDDLE -> yRatio - 0.5f
    CaptionVerticalAnchor.BOTTOM -> yRatio - 1f
}

/**
 * Optional per-cue placement changes.  Null fields inherit the project layout;
 * a non-null field replaces only that coordinate for the cue.
 */
data class CaptionLayoutOverride(
    val xRatio: Float? = null,
    val yRatio: Float? = null,
    val widthRatio: Float? = null,
) {
    init {
        xRatio?.let {
            require(it.isFinite() && it in 0f..1f) {
                "Caption layout override xRatio must be normalized"
            }
        }
        yRatio?.let {
            require(it.isFinite() && it in 0f..1f) {
                "Caption layout override yRatio must be normalized"
            }
        }
        widthRatio?.let {
            require(it.isFinite() && it > 0f && it <= 1f) {
                "Caption layout override widthRatio must be normalized and positive"
            }
        }
    }

    val isEmpty: Boolean
        get() = xRatio == null && yRatio == null && widthRatio == null
}

/** Resolve a cue placement while retaining the project layout as the fallback. */
fun resolveCaptionLayout(
    defaultLayout: CaptionLayout,
    override: CaptionLayoutOverride?,
): CaptionLayout {
    val safeDefault = CaptionLayout(defaultLayout.xRatio, defaultLayout.yRatio, defaultLayout.widthRatio)
    if (override == null || override.isEmpty) return safeDefault
    return CaptionLayout(
        xRatio = override.xRatio ?: safeDefault.xRatio,
        yRatio = override.yRatio ?: safeDefault.yRatio,
        widthRatio = override.widthRatio ?: safeDefault.widthRatio,
    )
}

fun CaptionLayoutOverride.validated(): CaptionLayoutOverride = CaptionLayoutOverride(
    xRatio = xRatio,
    yRatio = yRatio,
    widthRatio = widthRatio,
)

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
