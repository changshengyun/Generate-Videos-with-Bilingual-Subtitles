package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.SourceVideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class CaptionPaintPlanTest {
    @Test
    fun productionPlanPaintsStrokeBeforeFillWithIdenticalGlyphMetrics() {
        val cue = cue("cue-a", "Hello world")
        val spec = CaptionRenderResolver.resolveSpec(
            caption = cue,
            layout = CaptionLayout(xRatio = 0.1f, yRatio = 0.8f, widthRatio = 0.7f),
            defaultStyle = DefaultCaptionStyle(
                fontSizeRatio = 24f / 1080f,
                outlineWidthRatio = 3f / 1080f,
                fontFamily = "sans",
                bold = true,
                italic = true,
                alignment = CaptionAlignment.RIGHT,
            ),
            source = SourceVideoSize(1920, 1080),
            container = PreviewContainerSize(1920, 1080),
        )

        val plan = CaptionPaintPlan.from(spec, cue, chinese = false)

        assertEquals(listOf(CaptionPaintPass.STROKE, CaptionPaintPass.FILL), plan.layers.map { it.pass })
        assertEquals("Hello world", plan.stroke.text)
        assertEquals(plan.stroke.text, plan.fill.text)
        assertEquals(plan.stroke.fontFamily, plan.fill.fontFamily)
        assertEquals(plan.stroke.fontSizePx, plan.fill.fontSizePx)
        assertEquals(plan.stroke.outlineWidthPx, plan.fill.outlineWidthPx)
        assertEquals(plan.stroke.leftPx, plan.fill.leftPx)
        assertEquals(plan.stroke.topPx, plan.fill.topPx)
        assertEquals(plan.stroke.widthPx, plan.fill.widthPx)
        assertEquals(plan.stroke.alignment, plan.fill.alignment)
        assertEquals(plan.stroke.bold, plan.fill.bold)
        assertEquals(plan.stroke.italic, plan.fill.italic)
        assertEquals("#000000", plan.stroke.colorHex)
        assertEquals("#FFFFFF", plan.fill.colorHex)
        assertNull(plan.background)
    }

    @Test
    fun enabledBackgroundUsesTheSharedTextBoxAndResolvedOutlineAsBoxPadding() {
        val cue = cue("cue-background", "Opaque")
        val spec = CaptionRenderResolver.resolveSpec(
            caption = cue,
            layout = CaptionLayout(xRatio = 0.2f, yRatio = 0.3f, widthRatio = 0.5f),
            defaultStyle = DefaultCaptionStyle(
                backgroundEnabled = true,
                backgroundColorHex = "#123456",
                outlineWidthRatio = 4f / 1080f,
            ),
            source = SourceVideoSize(1920, 1080),
            container = PreviewContainerSize(1920, 1080),
        )

        val plan = CaptionPaintPlan.from(spec, cue, chinese = false)
        val background = assertNotNull(plan.background).let { plan.background!! }

        assertEquals(true, background.enabled)
        assertEquals("#123456", background.colorHex)
        assertEquals(plan.stroke.leftPx, background.leftPx)
        assertEquals(plan.stroke.topPx, background.topPx)
        assertEquals(plan.stroke.widthPx, background.widthPx)
        assertEquals(4, background.boxPaddingPx)
        assertEquals(CaptionBackgroundHeightPolicy.TEXT_LAYOUT_LINE_BOXES, background.heightPolicy)
    }

    @Test
    fun englishAndChinesePlansShareSpecButKeepIndependentTextAndFillColor() {
        val cue = cue("cue-b", "English", "中文")
        val spec = CaptionRenderResolver.resolveSpec(
            caption = cue,
            layout = CaptionLayout(),
            defaultStyle = DefaultCaptionStyle(primaryColorHex = "#102030", secondaryColorHex = "#E0D0C0"),
            source = SourceVideoSize(1280, 720),
            container = PreviewContainerSize(1280, 720),
        )

        val english = CaptionPaintPlan.from(spec, cue, chinese = false)
        val chinese = CaptionPaintPlan.from(spec, cue, chinese = true)

        assertEquals("English", english.fill.text)
        assertEquals("中文", chinese.fill.text)
        assertEquals("#102030", english.fill.colorHex)
        assertEquals("#E0D0C0", chinese.fill.colorHex)
        assertEquals(english.stroke.fontSizePx, chinese.stroke.fontSizePx)
        assertEquals(english.stroke.leftPx, chinese.stroke.leftPx)
        assertEquals(english.stroke.topPx, chinese.stroke.topPx)
        assertEquals(english.stroke.widthPx, chinese.stroke.widthPx)
    }

    @Test(expected = IllegalArgumentException::class)
    fun planRejectsFillBeforeStroke() {
        val layer = CaptionPaintLayer(
            pass = CaptionPaintPass.FILL,
            text = "x",
            fontFamily = "sans",
            fontSizePx = 24,
            outlineWidthPx = 2,
            leftPx = 0,
            topPx = 0,
            widthPx = 100,
            colorHex = "#FFFFFF",
            alignment = CaptionAlignment.CENTER,
            bold = false,
            italic = false,
        )
        CaptionPaintPlan(listOf(layer, layer.copy(pass = CaptionPaintPass.STROKE)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun planRejectsMismatchedTextBetweenPasses() {
        val stroke = layer(CaptionPaintPass.STROKE, "stroke")
        val fill = layer(CaptionPaintPass.FILL, "fill")
        CaptionPaintPlan(listOf(stroke, fill))
    }

    private fun layer(pass: CaptionPaintPass, text: String) = CaptionPaintLayer(
        pass = pass,
        text = text,
        fontFamily = "sans",
        fontSizePx = 24,
        outlineWidthPx = 2,
        leftPx = 3,
        topPx = 4,
        widthPx = 100,
        colorHex = "#000000",
        alignment = CaptionAlignment.CENTER,
        bold = false,
        italic = false,
    )

    private fun cue(id: String, english: String, chinese: String = "") = CaptionCue(
        id = id,
        startMs = 0,
        endMs = 1_000,
        english = english,
        chinese = chinese,
        confidence = 1f,
    )
}
