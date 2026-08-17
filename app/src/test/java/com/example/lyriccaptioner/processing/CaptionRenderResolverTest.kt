package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.SourceVideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CaptionRenderResolverTest {
    @Test
    fun resolvesPerCueOverrideWithoutChangingCaptionOrSharedLayout() {
        val layout = CaptionLayout(xRatio = 0.12f, yRatio = 0.76f, widthRatio = 0.7f)
        val defaultStyle = DefaultCaptionStyle(
            fontSizeSp = 22,
            primaryColorHex = "#ABCDEF",
            bold = false,
            alignment = CaptionAlignment.CENTER,
        )
        val caption = CaptionCue(
            id = "cue-1",
            startMs = 1_234L,
            endMs = 5_678L,
            english = "Exact English",
            chinese = "Exact Chinese",
            confidence = 0.9f,
            styleOverride = CaptionStyleOverride(
                fontSizeSp = 34,
                bold = true,
                alignment = CaptionAlignment.RIGHT,
            ),
        )

        val render = CaptionRenderResolver.resolve(caption, layout, defaultStyle)

        assertSame(caption, render.caption)
        assertEquals(layout, render.layout)
        assertEquals(1_234L, render.caption.startMs)
        assertEquals(5_678L, render.caption.endMs)
        assertEquals("Exact English", render.caption.english)
        assertEquals("Exact Chinese", render.caption.chinese)
        assertEquals(34, render.style.fontSizeSp)
        assertEquals("#ABCDEF", render.style.primaryColorHex)
        assertEquals(true, render.style.bold)
        assertEquals(CaptionAlignment.RIGHT, render.style.alignment)
    }

    @Test
    fun resolveAllDoesNotLeakOneCueOverrideIntoAnotherCue() {
        val overridden = cue(
            id = "overridden",
            styleOverride = CaptionStyleOverride(
                primaryColorHex = "#123456",
                italic = true,
            ),
        )
        val inherited = cue(id = "inherited")

        val renders = CaptionRenderResolver.resolveAll(
            captions = listOf(overridden, inherited),
            layout = CaptionLayout(),
            defaultStyle = DefaultCaptionStyle(primaryColorHex = "#FEDCBA", italic = false),
        )

        assertEquals(listOf("overridden", "inherited"), renders.map { it.caption.id })
        assertEquals("#123456", renders[0].style.primaryColorHex)
        assertEquals(true, renders[0].style.italic)
        assertEquals("#FEDCBA", renders[1].style.primaryColorHex)
        assertEquals(false, renders[1].style.italic)
        assertEquals(renders[0].layout, renders[1].layout)
    }

    @Test
    fun resolvesCueLayoutOverrideAndInheritsUnspecifiedProjectCoordinates() {
        val projectLayout = CaptionLayout(xRatio = 0.1f, yRatio = 0.8f, widthRatio = 0.7f)
        val caption = cue(id = "placed").copy(
            layoutOverride = CaptionLayoutOverride(yRatio = 0.25f, widthRatio = 0.5f),
        )

        val render = CaptionRenderResolver.resolve(
            caption = caption,
            layout = projectLayout,
            defaultStyle = DefaultCaptionStyle(),
        )

        assertSame(caption, render.caption)
        assertEquals(0.1f, render.layout.xRatio)
        assertEquals(0.25f, render.layout.yRatio)
        assertEquals(0.5f, render.layout.widthRatio)
    }

    @Test
    fun resolveAllKeepsCuePlacementAndStyleOverridesIndependent() {
        val projectLayout = CaptionLayout(xRatio = 0.05f, yRatio = 0.88f, widthRatio = 0.9f)
        val placed = cue(id = "placed").copy(
            layoutOverride = CaptionLayoutOverride(xRatio = 0.2f, yRatio = 0.3f, widthRatio = 0.4f),
        )
        val styled = cue(
            id = "styled",
            styleOverride = CaptionStyleOverride(fontSizeSp = 36, alignment = CaptionAlignment.RIGHT),
        )

        val renders = CaptionRenderResolver.resolveAll(
            captions = listOf(placed, styled),
            layout = projectLayout,
            defaultStyle = DefaultCaptionStyle(fontSizeSp = 20),
        )

        assertEquals(CaptionLayout(0.2f, 0.3f, 0.4f), renders[0].layout)
        assertEquals(20, renders[0].style.fontSizeSp)
        assertEquals(projectLayout, renders[1].layout)
        assertEquals(36, renders[1].style.fontSizeSp)
        assertEquals(CaptionAlignment.RIGHT, renders[1].style.alignment)
    }

    @Test
    fun safelyNormalizesInvalidDefaultAndOverrideFields() {
        val render = CaptionRenderResolver.resolve(
            caption = cue(
                id = "unsafe",
                styleOverride = CaptionStyleOverride(
                    fontSizeSp = -50,
                    outlineColorHex = "invalid",
                    fontFamily = "invalid",
                ),
            ),
            layout = CaptionLayout(),
            defaultStyle = DefaultCaptionStyle(
                fontSizeSp = 999,
                primaryColorHex = "invalid",
            ),
        )

        assertEquals(14, render.style.fontSizeSp)
        assertEquals("#FFFFFF", render.style.primaryColorHex)
        assertEquals("#000000", render.style.outlineColorHex)
        assertEquals("sans", render.style.fontFamily)
    }

    @Test
    fun resolveSpecUsesOneSourceRelativePixelContractAcrossPreviewSizes() {
        val source = SourceVideoSize(1920, 1080)
        val caption = cue("scaled")
        val layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.8f, widthRatio = 0.8f)
        val style = DefaultCaptionStyle(fontSizeRatio = 24f / 1080f, outlineWidthRatio = 2f / 1080f)

        val normal = CaptionRenderResolver.resolveSpec(
            caption, layout, style, source, PreviewContainerSize(1920, 1080),
        )
        val fullscreen = CaptionRenderResolver.resolveSpec(
            caption, layout, style, source, PreviewContainerSize(1280, 720),
        )

        assertEquals(24, normal.fontSizePx)
        assertEquals(2, normal.outlineWidthPx)
        assertEquals(16, fullscreen.fontSizePx)
        assertEquals(1, fullscreen.outlineWidthPx)
        assertEquals(0.8f, normal.layout.widthRatio)
        assertEquals(normal.style.fontSizeRatio, fullscreen.style.fontSizeRatio)
    }

    @Test
    fun physicalPixelsToSpCancelsDensityAndFontScale() {
        val physicalPixels = 24
        assertEquals(24f, CaptionRenderResolver.physicalPixelsToSp(physicalPixels, 1f, 1f))
        assertEquals(12f, CaptionRenderResolver.physicalPixelsToSp(physicalPixels, 2f, 1f))
        assertEquals(12f, CaptionRenderResolver.physicalPixelsToSp(physicalPixels, 1f, 2f))
        assertEquals(6f, CaptionRenderResolver.physicalPixelsToSp(physicalPixels, 2f, 2f))
    }

    @Test
    fun resolveSpecScalesConsistentlyFor720pAnd4kAndKeepsCueIsolation() {
        val layout = CaptionLayout()
        val defaultStyle = DefaultCaptionStyle(fontSizeRatio = 24f / 1080f, outlineWidthRatio = 2f / 1080f)
        val small = CaptionRenderResolver.resolveSpec(
            cue("small"), layout, defaultStyle,
            SourceVideoSize(1280, 720), PreviewContainerSize(1280, 720),
        )
        val large = CaptionRenderResolver.resolveSpec(
            cue("large", CaptionStyleOverride(fontSizeRatio = 36f / 1080f, outlineWidthRatio = 4f / 1080f)),
            layout, defaultStyle,
            SourceVideoSize(3840, 2160), PreviewContainerSize(3840, 2160),
        )

        assertEquals(16, small.fontSizePx)
        assertEquals(1, small.outlineWidthPx)
        assertEquals(72, large.fontSizePx)
        assertEquals(8, large.outlineWidthPx)
        assertEquals("small", small.caption.id)
        assertEquals("large", large.caption.id)
        assertEquals(defaultStyle.fontSizeRatio, small.style.fontSizeRatio)
        assertEquals(36f / 1080f, large.style.fontSizeRatio)
    }

    private fun cue(
        id: String,
        styleOverride: CaptionStyleOverride? = null,
    ): CaptionCue = CaptionCue(
        id = id,
        startMs = 100L,
        endMs = 200L,
        english = id,
        chinese = "",
        confidence = 1f,
        styleOverride = styleOverride,
    )
}
