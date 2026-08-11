package com.example.lyriccaptioner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptionGeometryModelTest {
    @Test
    fun fitRectangleCoversEqualWideAndTallContainers() {
        val source = SourceVideoSize(1920, 1080)

        assertEquals(VideoRect(0, 0, 1600, 900), CaptionGeometryResolver.effectiveVideoRect(source, PreviewContainerSize(1600, 900)))
        assertEquals(VideoRect(0, 547, 900, 506), CaptionGeometryResolver.effectiveVideoRect(source, PreviewContainerSize(900, 1600)))
        assertEquals(VideoRect(0, 218, 1000, 563), CaptionGeometryResolver.effectiveVideoRect(source, PreviewContainerSize(1000, 1000)))
    }

    @Test
    fun fitRectangleHandlesPortraitSquareAndPillarbox() {
        assertEquals(
            VideoRect(333, 0, 533, 800),
            CaptionGeometryResolver.effectiveVideoRect(
                SourceVideoSize(600, 900),
                PreviewContainerSize(1200, 800),
            ),
        )
        assertEquals(
            VideoRect(218, 0, 563, 1000),
            CaptionGeometryResolver.effectiveVideoRect(
                SourceVideoSize(1080, 1920),
                PreviewContainerSize(1000, 1000),
            ),
        )
        assertEquals(
            VideoRect(0, 0, 1000, 1000),
            CaptionGeometryResolver.effectiveVideoRect(
                SourceVideoSize(1000, 1000),
                PreviewContainerSize(1000, 1000),
            ),
        )
    }

    @Test
    fun normalizedCoordinatesMapInsideEffectiveRectangleWithAllAlignments() {
        val source = SourceVideoSize(1920, 1080)
        val container = PreviewContainerSize(1000, 1000)
        val layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.2f, widthRatio = 0.5f)

        val left = CaptionGeometryResolver.resolve(source, container, layout, CaptionAlignment.LEFT)
        val center = CaptionGeometryResolver.resolve(source, container, layout, CaptionAlignment.CENTER)
        val right = CaptionGeometryResolver.resolve(source, container, layout, CaptionAlignment.RIGHT)

        assertEquals(VideoRect(0, 218, 1000, 563), left.videoRect)
        assertEquals(100, left.textBoxLeftPx)
        assertEquals(500, left.textBoxWidthPx)
        assertEquals(100, left.anchorXpx)
        assertEquals(331, left.anchorYpx)
        assertEquals(CaptionVerticalAnchor.TOP, left.anchor)
        assertEquals(350, center.anchorXpx)
        assertEquals(600, right.anchorXpx)
        assertEquals(left.textBoxTopPx, center.textBoxTopPx)
    }

    @Test
    fun verticalAnchorsMatchSharedCaptionLayoutSemantics() {
        val source = SourceVideoSize(1920, 1080)
        val container = PreviewContainerSize(1920, 1080)

        assertEquals(CaptionVerticalAnchor.TOP, CaptionGeometryResolver.resolve(source, container, CaptionLayout(yRatio = 0.2f)).anchor)
        assertEquals(CaptionVerticalAnchor.MIDDLE, CaptionGeometryResolver.resolve(source, container, CaptionLayout(yRatio = 0.5f)).anchor)
        assertEquals(CaptionVerticalAnchor.BOTTOM, CaptionGeometryResolver.resolve(source, container, CaptionLayout(yRatio = 0.8f)).anchor)
    }

    @Test
    fun contentInsetsAreExplicitAndDoNotChangeNormalizedTextBoxWidth() {
        val layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.5f, widthRatio = 0.6f)
        val geometry = CaptionGeometryResolver.resolve(
            SourceVideoSize(1000, 1000),
            PreviewContainerSize(1000, 1000),
            layout,
            contentInsets = CaptionContentInsets(leftPx = 8, rightPx = 8),
        )

        assertEquals(600, geometry.textBoxWidthPx)
        assertEquals(CaptionContentInsets(leftPx = 8, rightPx = 8), geometry.contentInsets)
    }

    @Test
    fun cueGeometryIsIndependentAndDoesNotMutateLayouts() {
        val source = SourceVideoSize(1920, 1080)
        val container = PreviewContainerSize(1280, 720)
        val firstLayout = CaptionLayout(xRatio = 0.05f, yRatio = 0.2f, widthRatio = 0.4f)
        val secondLayout = CaptionLayout(xRatio = 0.5f, yRatio = 0.8f, widthRatio = 0.45f)

        val first = CaptionGeometryResolver.resolve(source, container, firstLayout, CaptionAlignment.LEFT)
        val second = CaptionGeometryResolver.resolve(source, container, secondLayout, CaptionAlignment.RIGHT)

        assertEquals(64, first.textBoxLeftPx)
        assertEquals(512, first.textBoxWidthPx)
        assertEquals(640, second.textBoxLeftPx)
        assertEquals(576, second.textBoxWidthPx)
        assertEquals(CaptionVerticalAnchor.TOP, first.anchor)
        assertEquals(CaptionVerticalAnchor.BOTTOM, second.anchor)
        assertEquals(0.05f, firstLayout.xRatio)
        assertEquals(0.5f, secondLayout.xRatio)
    }

    @Test
    fun normalAndFullscreenKeepTheSameNormalizedPlacement() {
        val source = SourceVideoSize(1920, 1080)
        val layout = CaptionLayout(xRatio = 0.2f, yRatio = 0.8f, widthRatio = 0.5f)

        val normal = CaptionGeometryResolver.resolve(
            source,
            PreviewContainerSize(960, 540),
            layout,
            CaptionAlignment.RIGHT,
        )
        val fullscreen = CaptionGeometryResolver.resolve(
            source,
            PreviewContainerSize(1920, 1080),
            layout,
            CaptionAlignment.RIGHT,
        )

        assertEquals(normal.anchor, fullscreen.anchor)
        assertEquals(normal.alignment, fullscreen.alignment)
        assertEquals(normal.videoRect.width * 2, fullscreen.videoRect.width)
        assertEquals(normal.textBoxLeftPx * 2, fullscreen.textBoxLeftPx)
        assertEquals(normal.textBoxWidthPx * 2, fullscreen.textBoxWidthPx)
        assertEquals(normal.anchorYpx * 2, fullscreen.anchorYpx)
    }

    @Test
    fun dimensionsMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) { SourceVideoSize(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { PreviewContainerSize(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { CaptionContentInsets(leftPx = -1) }
    }
}
