package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
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
