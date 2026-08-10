package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
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
        assertSame(layout, render.layout)
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
