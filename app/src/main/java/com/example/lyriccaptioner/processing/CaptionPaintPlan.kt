package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue

/** The only two paint passes used by the Compose subtitle renderer. */
enum class CaptionPaintPass {
    STROKE,
    FILL,
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
