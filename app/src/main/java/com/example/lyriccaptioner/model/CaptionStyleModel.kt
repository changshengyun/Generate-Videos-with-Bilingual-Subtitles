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
    /** Legacy UI/archive projection. New code must use [fontSizeRatio]. */
    val fontSizeSp: Int = 24,
    val primaryColorHex: String = "#FFFFFF",
    val secondaryColorHex: String = "#F4E7A1",
    val outlineColorHex: String = "#000000",
    val fontFamily: String = SUBTITLE_FONT_SANS,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: CaptionAlignment = CaptionAlignment.CENTER,
    /** Font size as a fraction of the source video's height. */
    val fontSizeRatio: Float = DEFAULT_CAPTION_FONT_SIZE_RATIO,
    /** Glyph outline width as a fraction of the source video's height. */
    val outlineWidthRatio: Float = DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO,
    /** Whether a solid subtitle background is painted behind the text box. */
    val backgroundEnabled: Boolean = false,
    /** Solid background color shared by Compose preview and ASS export. */
    val backgroundColorHex: String = DEFAULT_CAPTION_BACKGROUND_COLOR_HEX,
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
    val fontSizeRatio: Float? = null,
    val outlineWidthRatio: Float? = null,
    val backgroundEnabled: Boolean? = null,
    val backgroundColorHex: String? = null,
) {
    val isEmpty: Boolean
        get() = fontSizeSp == null &&
            primaryColorHex == null &&
            secondaryColorHex == null &&
            outlineColorHex == null &&
            fontFamily == null &&
            bold == null &&
            italic == null &&
            alignment == null &&
            fontSizeRatio == null &&
            outlineWidthRatio == null &&
            backgroundEnabled == null &&
            backgroundColorHex == null
}

data class ResolvedCaptionStyle(
    /** Legacy 1080p projection retained for existing controls and diagnostics. */
    val fontSizeSp: Int,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val outlineColorHex: String,
    val fontFamily: String,
    val bold: Boolean,
    val italic: Boolean,
    val alignment: CaptionAlignment,
    val fontSizeRatio: Float,
    val outlineWidthRatio: Float,
    val backgroundEnabled: Boolean,
    val backgroundColorHex: String,
)

fun resolveCaptionStyle(
    defaultStyle: DefaultCaptionStyle,
    override: CaptionStyleOverride?,
): ResolvedCaptionStyle {
    val safeDefault = defaultStyle.validated()
    val safeOverride = override?.validated()
    val ratio = safeOverride?.fontSizeRatio
        ?: safeOverride?.fontSizeSp?.let(::legacyFontSizeToRatio)
        ?: safeDefault.fontSizeRatio
    val outlineRatio = safeOverride?.outlineWidthRatio ?: safeDefault.outlineWidthRatio
    return ResolvedCaptionStyle(
        fontSizeSp = ratioToLegacyFontSize(ratio),
        primaryColorHex = safeOverride?.primaryColorHex ?: safeDefault.primaryColorHex,
        secondaryColorHex = safeOverride?.secondaryColorHex ?: safeDefault.secondaryColorHex,
        outlineColorHex = safeOverride?.outlineColorHex ?: safeDefault.outlineColorHex,
        fontFamily = safeOverride?.fontFamily ?: safeDefault.fontFamily,
        bold = safeOverride?.bold ?: safeDefault.bold,
        italic = safeOverride?.italic ?: safeDefault.italic,
        alignment = safeOverride?.alignment ?: safeDefault.alignment,
        fontSizeRatio = ratio,
        outlineWidthRatio = outlineRatio,
        backgroundEnabled = safeOverride?.backgroundEnabled ?: safeDefault.backgroundEnabled,
        backgroundColorHex = safeOverride?.backgroundColorHex ?: safeDefault.backgroundColorHex,
    )
}

fun DefaultCaptionStyle.validated(): DefaultCaptionStyle {
    // A style constructed by pre-v6 callers only has fontSizeSp. Prefer that
    // value when the new field still has its default, then canonicalize both
    // projections so subsequent renderers use the source-relative ratio.
    val ratio = if (
        fontSizeRatio == DEFAULT_CAPTION_FONT_SIZE_RATIO &&
        fontSizeSp != DEFAULT_LEGACY_CAPTION_FONT_SIZE_SP
    ) {
        legacyFontSizeToRatio(fontSizeSp)
    } else {
        fontSizeRatio
    }
    val safeRatio = canonicalCaptionFontSizeRatio(ratio)
    return copy(
        fontSizeSp = ratioToLegacyFontSize(safeRatio),
        fontSizeRatio = safeRatio,
        outlineWidthRatio = canonicalCaptionOutlineWidthRatio(outlineWidthRatio),
        primaryColorHex = normalizeSubtitleColor(primaryColorHex, "#FFFFFF"),
        secondaryColorHex = normalizeSubtitleColor(secondaryColorHex, "#F4E7A1"),
        outlineColorHex = normalizeSubtitleColor(outlineColorHex, "#000000"),
        backgroundColorHex = normalizeSubtitleColor(
            backgroundColorHex,
            DEFAULT_CAPTION_BACKGROUND_COLOR_HEX,
        ),
        fontFamily = fontFamily.validatedCaptionFontFamily(),
    )
}

fun CaptionStyleOverride.validated(): CaptionStyleOverride {
    val legacyRatio = fontSizeSp?.let(::legacyFontSizeToRatio)
    val canonicalRatio = fontSizeRatio?.let(::canonicalCaptionFontSizeRatio) ?: legacyRatio
    return copy(
        // Ratio is canonical when both fields are supplied.  Keep the legacy
        // projection in sync so an archive round-trip cannot resurrect a
        // stale fontSizeSp value after an A+/A- edit.
        fontSizeSp = canonicalRatio?.let(::ratioToLegacyFontSize),
        fontSizeRatio = canonicalRatio,
        outlineWidthRatio = outlineWidthRatio?.let(::canonicalCaptionOutlineWidthRatio),
        primaryColorHex = primaryColorHex?.let { normalizeSubtitleColor(it, "#FFFFFF") },
        secondaryColorHex = secondaryColorHex?.let { normalizeSubtitleColor(it, "#F4E7A1") },
        outlineColorHex = outlineColorHex?.let { normalizeSubtitleColor(it, "#000000") },
        backgroundColorHex = backgroundColorHex?.let {
            normalizeSubtitleColor(it, DEFAULT_CAPTION_BACKGROUND_COLOR_HEX)
        },
        fontFamily = fontFamily?.validatedCaptionFontFamily(),
    )
}

/* Kept as a named helper for callers that need to migrate old records. */
fun legacyFontSizeToRatio(fontSizeSp: Int): Float =
    fontSizeSp.coerceIn(MIN_CAPTION_FONT_SIZE_SP, MAX_CAPTION_FONT_SIZE_SP).toFloat() / LEGACY_PLAY_RES_Y

/** Canonical source-relative ratio used by every style write path. */
fun canonicalCaptionFontSizeRatio(fontSizeRatio: Float): Float {
    val finite = if (fontSizeRatio.isFinite()) fontSizeRatio else DEFAULT_CAPTION_FONT_SIZE_RATIO
    return finite.coerceIn(MIN_CAPTION_FONT_SIZE_RATIO, MAX_CAPTION_FONT_SIZE_RATIO)
}

/** Canonical outline ratio; invalid values fall back to the default width. */
fun canonicalCaptionOutlineWidthRatio(outlineWidthRatio: Float): Float {
    val finite = if (outlineWidthRatio.isFinite()) outlineWidthRatio else DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO
    return finite.coerceIn(MIN_CAPTION_OUTLINE_WIDTH_RATIO, MAX_CAPTION_OUTLINE_WIDTH_RATIO)
}

/** Stable 1080p display projection for legacy controls and diagnostics. */
fun ratioToLegacyFontSize(fontSizeRatio: Float): Int =
    kotlin.math.round(canonicalCaptionFontSizeRatio(fontSizeRatio) * LEGACY_PLAY_RES_Y)
        .toInt()

/**
 * Adjust a source-relative font ratio by a legacy integer step.  The step is
 * interpreted in 1080p projection units, then clamped once at the canonical
 * ratio boundary so Compose, ASS and archive writes observe the same value.
 */
fun adjustCaptionFontSizeRatio(currentRatio: Float, deltaSp: Int): Float {
    val deltaRatio = deltaSp.toFloat() / LEGACY_PLAY_RES_Y
    return canonicalCaptionFontSizeRatio(canonicalCaptionFontSizeRatio(currentRatio) + deltaRatio)
}

/** Canonical write helper for a project/default style. */
fun DefaultCaptionStyle.withFontSizeRatio(fontSizeRatio: Float): DefaultCaptionStyle {
    val canonical = canonicalCaptionFontSizeRatio(fontSizeRatio)
    return copy(fontSizeRatio = canonical, fontSizeSp = ratioToLegacyFontSize(canonical))
}

/** Canonical write helper for a cue override. */
fun CaptionStyleOverride.withFontSizeRatio(fontSizeRatio: Float): CaptionStyleOverride {
    val canonical = canonicalCaptionFontSizeRatio(fontSizeRatio)
    return copy(fontSizeRatio = canonical, fontSizeSp = ratioToLegacyFontSize(canonical))
}

fun DefaultCaptionStyle.sourceRelativeFontSizeRatio(): Float = validated().fontSizeRatio
fun DefaultCaptionStyle.sourceRelativeOutlineWidthRatio(): Float = validated().outlineWidthRatio

/*
 * Deprecated compatibility accessors are intentionally represented by the
 * existing fontSizeSp field. New persistence and render code must consume the
 * ratio fields above; the integer is only a 1080p display projection.
 */
/* old validation implementation retained below only for color/font helpers */
/*
fun DefaultCaptionStyle.validated(): DefaultCaptionStyle = copy(
    fontSizeSp = fontSizeSp.coerceIn(MIN_CAPTION_FONT_SIZE_SP, MAX_CAPTION_FONT_SIZE_SP),
    primaryColorHex = normalizeSubtitleColor(primaryColorHex, "#FFFFFF"),
    secondaryColorHex = normalizeSubtitleColor(secondaryColorHex, "#F4E7A1"),
    outlineColorHex = normalizeSubtitleColor(outlineColorHex, "#000000"),
    fontFamily = fontFamily.validatedCaptionFontFamily(),
)
*/

fun SubtitleStyle.toDefaultCaptionStyle(): DefaultCaptionStyle {
    val legacy = validated()
    return DefaultCaptionStyle(
        fontSizeSp = legacy.fontSizeSp,
        primaryColorHex = legacy.primaryColorHex,
        secondaryColorHex = legacy.secondaryColorHex,
        outlineColorHex = legacy.outlineColorHex,
        fontFamily = legacy.fontFamily,
        fontSizeRatio = legacyFontSizeToRatio(legacy.fontSizeSp),
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
const val LEGACY_PLAY_RES_Y = 1080
const val DEFAULT_LEGACY_CAPTION_FONT_SIZE_SP = 24
const val MIN_CAPTION_FONT_SIZE_RATIO = 0.012962963f
const val MAX_CAPTION_FONT_SIZE_RATIO = 0.044444446f
const val DEFAULT_CAPTION_FONT_SIZE_RATIO = 0.022222223f
const val MIN_CAPTION_OUTLINE_WIDTH_RATIO = 0f
const val MAX_CAPTION_OUTLINE_WIDTH_RATIO = 0.011111112f
const val DEFAULT_CAPTION_OUTLINE_WIDTH_RATIO = 0.0018518519f
const val DEFAULT_CAPTION_BACKGROUND_COLOR_HEX = "#000000"
