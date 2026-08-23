package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue

/** The only two paint passes used by the Compose subtitle renderer. */
enum class CaptionPaintPass {
    STROKE,
    FILL,
}

/**
 * Height contract for an opaque subtitle background.
 *
 * Text height is deliberately not guessed in the renderer-neutral layer: it depends on the
 * renderer's real font metrics and wrapping result.  A consumer lays text out within
 * [CaptionPaintBackground.widthPx], then paints one opaque rectangle for every resulting line
 * box, expanded by [CaptionPaintBackground.boxPaddingPx] on all sides.  This is the contract
 * exposed by ASS/libass `BorderStyle=3` as well as the contract Compose must reproduce.
 */
enum class CaptionBackgroundHeightPolicy {
    TEXT_LAYOUT_LINE_BOXES,
}

/**
 * Optional opaque-background description for one caption text layout.
 *
 * [leftPx], [topPx] and [widthPx] describe the same text-box constraint used by the glyph layers,
 * not a pre-measured rectangle.  The actual line-box height is resolved by the renderer according
 * to [heightPolicy].  `BorderStyle=3` treats ASS Outline as opaque-box padding, so [boxPaddingPx]
 * intentionally mirrors the resolved outline width.  A renderer must not add another padding.
 */
data class CaptionPaintBackground(
    val enabled: Boolean,
    val colorHex: String,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val boxPaddingPx: Int,
    val heightPolicy: CaptionBackgroundHeightPolicy = CaptionBackgroundHeightPolicy.TEXT_LAYOUT_LINE_BOXES,
) {
    init {
        require(enabled) { "A present caption background must be enabled" }
        require(leftPx >= 0 && topPx >= 0 && widthPx > 0) {
            "Background position and width must be non-negative and positive"
        }
        require(boxPaddingPx >= 0) { "Background box padding must be non-negative" }
    }
}

/**
 * A renderer-neutral description of one subtitle paint pass.
 *
 * The values are physical pixels in the shared [CaptionRenderSpec] coordinate
 * space.  Keeping these values outside Compose makes the ordering and the
 * no-drift contract directly testable on the JVM.
 */
data class CaptionPaintLayer(
    val pass: CaptionPaintPass,
    val text: String,
    val fontFamily: String,
    val fontSizePx: Int,
    val outlineWidthPx: Int,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val colorHex: String,
    val alignment: CaptionAlignment,
    val bold: Boolean,
    val italic: Boolean,
) {
    init {
        require(fontSizePx > 0) { "Paint font size must be positive" }
        require(outlineWidthPx >= 0) { "Paint outline width must be non-negative" }
        require(leftPx >= 0 && topPx >= 0 && widthPx > 0) {
            "Paint position and width must be non-negative and positive"
        }
        require(text.isNotEmpty()) { "Paint text must not be empty" }
        require(fontFamily.isNotBlank()) { "Paint font family must not be blank" }
    }
}

/**
 * Canonical Compose paint plan for one cue text.
 *
 * A plan always has exactly two layers: glyph [CaptionPaintPass.STROKE]
 * first, followed by glyph [CaptionPaintPass.FILL].  Construction rejects
 * plans whose layers drift in text, font, size, or position, preventing a
 * renderer from accidentally painting a different glyph on the second pass.
 */
data class CaptionPaintPlan(
    val layers: List<CaptionPaintLayer>,
    val background: CaptionPaintBackground? = null,
) {
    init {
        require(layers.size == 2) { "A caption paint plan requires stroke and fill layers" }
        require(layers[0].pass == CaptionPaintPass.STROKE) {
            "Stroke must be painted before fill"
        }
        require(layers[1].pass == CaptionPaintPass.FILL) {
            "Fill must be the final paint pass"
        }
        val stroke = layers[0]
        val fill = layers[1]
        require(stroke.text == fill.text) { "Stroke and fill text must match" }
        require(stroke.fontFamily == fill.fontFamily) { "Stroke and fill font must match" }
        require(stroke.fontSizePx == fill.fontSizePx) { "Stroke and fill font size must match" }
        require(stroke.leftPx == fill.leftPx && stroke.topPx == fill.topPx) {
            "Stroke and fill position must match"
        }
        require(stroke.widthPx == fill.widthPx) { "Stroke and fill text width must match" }
        require(stroke.alignment == fill.alignment) { "Stroke and fill alignment must match" }
        require(stroke.bold == fill.bold && stroke.italic == fill.italic) {
            "Stroke and fill font style must match"
        }
        background?.let {
            require(
                it.leftPx == stroke.leftPx &&
                    it.topPx == stroke.topPx &&
                    it.widthPx == stroke.widthPx,
            ) {
                "Background and glyph text-box geometry must match"
            }
            require(it.boxPaddingPx == stroke.outlineWidthPx) {
                "Background padding must match the resolved outline width"
            }
        }
    }

    val stroke: CaptionPaintLayer
        get() = layers[0]

    val fill: CaptionPaintLayer
        get() = layers[1]

    companion object {
        /** Build the production plan directly from the shared render spec. */
        fun from(
            spec: CaptionRenderSpec,
            text: String,
            fillColorHex: String,
        ): CaptionPaintPlan {
            val geometry = spec.geometry
            val style = spec.style
            val common = CaptionPaintLayer(
                pass = CaptionPaintPass.STROKE,
                text = text,
                fontFamily = style.fontFamily,
                fontSizePx = spec.fontSizePx,
                outlineWidthPx = spec.outlineWidthPx,
                leftPx = geometry.textBoxLeftPx,
                topPx = geometry.textBoxTopPx,
                widthPx = geometry.textBoxWidthPx,
                colorHex = style.outlineColorHex,
                alignment = style.alignment,
                bold = style.bold,
                italic = style.italic,
            )
            return CaptionPaintPlan(
                layers = listOf(
                    common,
                    common.copy(
                        pass = CaptionPaintPass.FILL,
                        colorHex = fillColorHex,
                    ),
                ),
                background = if (style.backgroundEnabled) {
                    CaptionPaintBackground(
                        enabled = true,
                        colorHex = style.backgroundColorHex,
                        leftPx = geometry.textBoxLeftPx,
                        topPx = geometry.textBoxTopPx,
                        widthPx = geometry.textBoxWidthPx,
                        boxPaddingPx = spec.outlineWidthPx,
                    )
                } else {
                    null
                },
            )
        }

        fun from(spec: CaptionRenderSpec, cue: CaptionCue, chinese: Boolean): CaptionPaintPlan =
            from(
                spec = spec,
                text = if (chinese) cue.chinese else cue.english,
                fillColorHex = if (chinese) spec.style.secondaryColorHex else spec.style.primaryColorHex,
            )
    }
}
