package com.example.lyriccaptioner.model

import kotlin.math.floor
import kotlin.math.roundToInt

/** Source video dimensions reported by the player or media metadata. */
data class SourceVideoSize(
    val width: Int,
    val height: Int,
    /**
     * Media3's [VideoSize.pixelWidthHeightRatio]. A value other than one
     * describes non-square source pixels and therefore changes the displayed
     * aspect ratio without changing the coded dimensions.
     */
    val pixelWidthHeightRatio: Float = 1f,
) {
    init {
        require(width > 0 && height > 0) { "Source video dimensions must be positive" }
        require(pixelWidthHeightRatio.isFinite() && pixelWidthHeightRatio > 0f) {
            "Source pixel width/height ratio must be finite and positive"
        }
    }

    /** Width of the displayed image in square-pixel units. */
    val displayedWidth: Double
        get() = width.toDouble() * pixelWidthHeightRatio.toDouble()

    /** Aspect ratio used by Media3's [AspectRatioFrameLayout]. */
    val displayedAspectRatio: Double
        get() = displayedWidth / height.toDouble()

    /** Explicit name for the ratio consumed by the effective-rectangle resolver. */
    val effectiveDisplayAspectRatio: Double
        get() = displayedAspectRatio
}

/** Pixel dimensions of the Compose preview container. */
data class PreviewContainerSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Preview container dimensions must be positive" }
    }
}

/** A pixel rectangle in the preview/container coordinate space. */
data class VideoRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(left >= 0 && top >= 0 && width > 0 && height > 0) {
            "Video rectangle must have non-negative origin and positive dimensions"
        }
    }

    val right: Int
        get() = left + width

    val bottom: Int
        get() = top + height
}

/**
 * Explicit decoration/inset contract for a cue text box.
 *
 * The normalized width maps to the complete text-box width.  Insets are kept
 * separate so a renderer cannot silently shrink or shift that mapping.  R2
 * currently uses [ZERO] in both Compose and ASS.
 */
data class CaptionContentInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
) {
    init {
        require(leftPx >= 0 && topPx >= 0 && rightPx >= 0 && bottomPx >= 0) {
            "Caption content insets must be non-negative"
        }
    }

    companion object {
        val ZERO = CaptionContentInsets()
    }
}

/**
 * Resolved cue geometry shared by the Compose preview and ASS exporter.
 *
 * [textBoxTopPx] and [anchorYpx] are the cue's vertical anchor coordinate,
 * not an assumed text-height-dependent top edge.  The renderer applies
 * [anchor] to place its measured text around that coordinate.  This keeps
 * geometry independent of font metrics while preserving the same top,
 * middle, and bottom semantics in both renderers.
 */
data class CaptionGeometry(
    val videoRect: VideoRect,
    val textBoxLeftPx: Int,
    val textBoxTopPx: Int,
    val textBoxWidthPx: Int,
    val anchor: CaptionVerticalAnchor,
    val alignment: CaptionAlignment,
    val anchorXpx: Int = textBoxLeftPx,
    val anchorYpx: Int = textBoxTopPx,
    val contentInsets: CaptionContentInsets = CaptionContentInsets.ZERO,
) {
    init {
        require(textBoxLeftPx >= videoRect.left) {
            "Caption text box must start inside the effective video rectangle"
        }
        require(textBoxWidthPx > 0 && textBoxLeftPx + textBoxWidthPx <= videoRect.right) {
            "Caption text box must fit inside the effective video rectangle"
        }
        require(anchorXpx in videoRect.left..videoRect.right) {
            "Caption horizontal anchor must be inside the effective video rectangle"
        }
        require(anchorYpx in videoRect.top..videoRect.bottom) {
            "Caption vertical anchor must be inside the effective video rectangle"
        }
    }
}

/**
 * Resolves normalized source-video coordinates into the Media3 FIT rectangle.
 * No renderer-specific padding, scale factor, or coordinate origin is used.
 */
object CaptionGeometryResolver {
    private const val ASPECT_DEFORMATION_TOLERANCE = 0.01

    /**
     * Computes the same FIT dimensions as Media3's AspectRatioFrameLayout:
     * keep the measured dimension that constrains the image, truncate the
     * other dimension to an integer, then center the child. Integer division
     * deliberately leaves an odd remainder on the right/bottom, matching the
     * FrameLayout center gravity used by PlayerView.
     */
    fun effectiveVideoRect(
        source: SourceVideoSize,
        container: PreviewContainerSize,
    ): VideoRect {
        val containerAspect = container.width.toDouble() / container.height.toDouble()
        val displayedAspect = source.displayedAspectRatio
        // AspectRatioFrameLayout intentionally leaves dimensions untouched for
        // negligible deformation (|videoAspect/containerAspect - 1| <= 1%).
        val deformation = displayedAspect / containerAspect - 1.0
        val (width, height) = if (kotlin.math.abs(deformation) <= ASPECT_DEFORMATION_TOLERANCE) {
            container.width to container.height
        } else if (displayedAspect > containerAspect) {
            container.width to floor(container.width.toDouble() / displayedAspect).toInt().coerceIn(1, container.height)
        } else {
            floor(container.height.toDouble() * displayedAspect).toInt().coerceIn(1, container.width) to container.height
        }
        return VideoRect(
            left = ((container.width - width) / 2).coerceAtLeast(0),
            top = ((container.height - height) / 2).coerceAtLeast(0),
            width = width,
            height = height,
        )
    }

    /** Convert a source-height-relative value to preview pixels. */
    fun sourceHeightToPreviewPixels(
        source: SourceVideoSize,
        container: PreviewContainerSize,
        sourcePixels: Float,
    ): Float {
        require(sourcePixels.isFinite() && sourcePixels >= 0f) {
            "Source pixel value must be finite and non-negative"
        }
        return sourcePixels * effectiveVideoRect(source, container).height / source.height.toFloat()
    }

    /** Convert a source-width-relative value to preview pixels. */
    fun sourceWidthToPreviewPixels(
        source: SourceVideoSize,
        container: PreviewContainerSize,
        sourcePixels: Float,
    ): Float {
        require(sourcePixels.isFinite() && sourcePixels >= 0f) {
            "Source pixel value must be finite and non-negative"
        }
        return sourcePixels * effectiveVideoRect(source, container).width / source.width.toFloat()
    }

    fun resolve(
        source: SourceVideoSize,
        container: PreviewContainerSize,
        layout: CaptionLayout,
        alignment: CaptionAlignment = CaptionAlignment.CENTER,
        contentInsets: CaptionContentInsets = CaptionContentInsets.ZERO,
    ): CaptionGeometry {
        val videoRect = effectiveVideoRect(source, container)
        val left = (layout.xRatio * videoRect.width).roundToInt()
            .coerceIn(0, videoRect.width)
        val right = ((layout.xRatio + layout.widthRatio) * videoRect.width).roundToInt()
            .coerceIn(left + 1, videoRect.width)
        val textBoxLeft = videoRect.left + left
        val textBoxWidth = (right - left).coerceAtLeast(1)
        val anchorX = when (alignment) {
            CaptionAlignment.LEFT -> textBoxLeft
            CaptionAlignment.CENTER -> textBoxLeft + textBoxWidth / 2
            CaptionAlignment.RIGHT -> textBoxLeft + textBoxWidth
        }.coerceIn(videoRect.left, videoRect.right)
        val anchorY = (layout.yRatio * videoRect.height)
            .roundToInt()
            .coerceIn(0, videoRect.height)
        val absoluteAnchorY = videoRect.top + anchorY
        return CaptionGeometry(
            videoRect = videoRect,
            textBoxLeftPx = textBoxLeft,
            textBoxTopPx = absoluteAnchorY,
            textBoxWidthPx = textBoxWidth,
            anchor = layout.verticalAnchor(),
            alignment = alignment,
            anchorXpx = anchorX,
            anchorYpx = absoluteAnchorY,
            contentInsets = contentInsets,
        )
    }
}
