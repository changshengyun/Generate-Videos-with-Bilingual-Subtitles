package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ResolvedCaptionStyle
import com.example.lyriccaptioner.model.resolveCaptionStyle

data class ResolvedCaptionRender(
    val caption: CaptionCue,
    val layout: CaptionLayout,
    val style: ResolvedCaptionStyle,
)

/** Shared render boundary for Compose preview and ASS export. */
object CaptionRenderResolver {
    fun resolve(
        caption: CaptionCue,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
    ): ResolvedCaptionRender = ResolvedCaptionRender(
        caption = caption,
        layout = layout,
        style = resolveCaptionStyle(defaultStyle, caption.styleOverride),
    )

    fun resolveAll(
        captions: List<CaptionCue>,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
    ): List<ResolvedCaptionRender> = captions.map { caption ->
        resolve(caption, layout, defaultStyle)
    }
}
