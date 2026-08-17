package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionGeometry
import com.example.lyriccaptioner.model.CaptionGeometryResolver
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.ResolvedCaptionStyle
import com.example.lyriccaptioner.model.SourceVideoSize
import com.example.lyriccaptioner.model.resolveCaptionLayout
import com.example.lyriccaptioner.model.resolveCaptionStyle
import kotlin.math.roundToInt

data class ResolvedCaptionRender(
    val caption: CaptionCue,
    val layout: CaptionLayout,
    val style: ResolvedCaptionStyle,
)

/**
 * Final source-relative render contract consumed by both preview and export.
 * All dimensions are physical pixels in [geometry]'s coordinate space; no
 * renderer may reinterpret the ratio or apply a second scale.
 */
data class CaptionRenderSpec(
    val caption: CaptionCue,
    val layout: CaptionLayout,
    val style: ResolvedCaptionStyle,
    val geometry: CaptionGeometry,
    val fontSizePx: Int,
    val outlineWidthPx: Int,
)

/** Shared render boundary for Compose preview and ASS export. */
object CaptionRenderResolver {
    /** Convert a physical render pixel value to Compose sp without changing its source-relative size. */
    fun physicalPixelsToSp(physicalPixels: Int, density: Float, fontScale: Float): Float {
        require(physicalPixels >= 0) { "Physical pixels must be non-negative" }
        require(density.isFinite() && density > 0f) { "Density must be positive" }
        require(fontScale.isFinite() && fontScale > 0f) { "Font scale must be positive" }
        return physicalPixels.toFloat() / density / fontScale
    }

    fun resolve(
        caption: CaptionCue,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
    ): ResolvedCaptionRender = ResolvedCaptionRender(
        caption = caption,
        layout = resolveCaptionLayout(layout, caption.layoutOverride),
        style = resolveCaptionStyle(defaultStyle, caption.styleOverride),
    )

    fun resolveAll(
        captions: List<CaptionCue>,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
    ): List<ResolvedCaptionRender> = captions.map { caption ->
        resolve(caption, layout, defaultStyle)
    }

    fun resolveSpec(
        caption: CaptionCue,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
        source: SourceVideoSize,
        container: PreviewContainerSize,
    ): CaptionRenderSpec {
        val render = resolve(caption, layout, defaultStyle)
        val geometry = CaptionGeometryResolver.resolve(
            source = source,
            container = container,
            layout = render.layout,
            alignment = render.style.alignment,
        )
        return CaptionRenderSpec(
            caption = render.caption,
            layout = render.layout,
            style = render.style,
            geometry = geometry,
            fontSizePx = sourceRelativePixels(render.style.fontSizeRatio, geometry.videoRect.height),
            outlineWidthPx = sourceRelativePixels(render.style.outlineWidthRatio, geometry.videoRect.height, minimum = 0),
        )
    }

    private fun sourceRelativePixels(ratio: Float, videoHeightPx: Int, minimum: Int = 1): Int =
        (ratio * videoHeightPx).roundToInt().coerceIn(minimum, videoHeightPx)
}
