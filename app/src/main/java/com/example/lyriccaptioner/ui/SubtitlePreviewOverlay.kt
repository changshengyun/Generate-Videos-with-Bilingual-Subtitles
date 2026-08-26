package com.example.lyriccaptioner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionGeometryResolver
import com.example.lyriccaptioner.model.CaptionVerticalAnchor
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.SourceVideoSize
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.canonicalCaptionFontSizeRatio
import com.example.lyriccaptioner.model.movedToDirectEditPosition
import com.example.lyriccaptioner.model.withDirectEditWidth
import com.example.lyriccaptioner.processing.CaptionPaintPlan
import com.example.lyriccaptioner.processing.CaptionRenderResolver
import com.example.lyriccaptioner.processing.CaptionRenderSpec
import com.example.lyriccaptioner.processing.ResolvedCaptionRender
import kotlin.math.roundToInt

@Composable
internal fun SubtitlePreviewOverlay(
    render: ResolvedCaptionRender,
    sourceVideoSize: SourceVideoSize?,
    directEditMode: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPositionCommitted: (String, Float, Float) -> Unit,
    onWidthCommitted: (String, Float) -> Unit,
    onFontSizeCommitted: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cue = render.caption
    if (sourceVideoSize == null) return
    val density = LocalDensity.current
    var transientLayout by remember(cue.id, render.layout) { mutableStateOf(render.layout) }
    var transientFontSizeRatio by remember(cue.id, render.style.fontSizeRatio) {
        mutableStateOf(render.style.fontSizeRatio)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerSize = with(density) {
            PreviewContainerSize(maxWidth.roundToPx(), maxHeight.roundToPx())
        }
        val geometry = CaptionGeometryResolver.resolve(
            source = sourceVideoSize,
            container = containerSize,
            layout = transientLayout,
            alignment = render.style.alignment,
        )
        val transientStyle = render.style.copy(fontSizeRatio = transientFontSizeRatio)
        val spec = CaptionRenderSpec(
            caption = cue,
            layout = transientLayout,
            style = transientStyle,
            geometry = geometry,
            fontSizePx = (transientFontSizeRatio * geometry.videoRect.height).roundToInt().coerceAtLeast(1),
            outlineWidthPx = (transientStyle.outlineWidthRatio * geometry.videoRect.height).roundToInt().coerceAtLeast(0),
        )
        val englishPlan = cue.english.takeIf { it.isNotBlank() }?.let {
            CaptionPaintPlan.from(spec = spec, text = it, fillColorHex = spec.style.primaryColorHex)
        }
        val chinesePlan = cue.chinese.takeIf { it.isNotBlank() }?.let {
            CaptionPaintPlan.from(spec = spec, text = it, fillColorHex = spec.style.secondaryColorHex)
        }
        val positionPlan = englishPlan ?: chinesePlan ?: return@BoxWithConstraints
        val verticalBand = geometry.anchor
        val verticalAlignment = when (verticalBand) {
            CaptionVerticalAnchor.TOP -> Alignment.TopStart
            CaptionVerticalAnchor.MIDDLE -> Alignment.CenterStart
            CaptionVerticalAnchor.BOTTOM -> Alignment.BottomStart
        }
        val yOffsetPx = when (verticalBand) {
            CaptionVerticalAnchor.TOP -> positionPlan.stroke.topPx
            CaptionVerticalAnchor.MIDDLE -> positionPlan.stroke.topPx - containerSize.height / 2
            CaptionVerticalAnchor.BOTTOM -> positionPlan.stroke.topPx - containerSize.height
        }
        val xOffsetPx = positionPlan.stroke.leftPx
        val directModifier = if (directEditMode) {
            Modifier
                .semantics {
                    contentDescription = if (selected) "已选中字幕:${cue.id}" else "选择字幕:${cue.id}"
                }
                .clickable(onClick = onSelect)
                .then(
                    if (selected) {
                        Modifier.pointerInput(cue.id, geometry.videoRect) {
                            detectDragGestures(
                                onDragEnd = {
                                    onPositionCommitted(cue.id, transientLayout.xRatio, transientLayout.yRatio)
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                transientLayout = transientLayout.movedToDirectEditPosition(
                                    xRatio = transientLayout.xRatio + dragAmount.x / geometry.videoRect.width,
                                    yRatio = transientLayout.yRatio + dragAmount.y / geometry.videoRect.height,
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .align(verticalAlignment)
                .offset(
                    x = with(density) { xOffsetPx.toDp() },
                    y = with(density) { yOffsetPx.toDp() },
                )
                .width(with(density) { positionPlan.stroke.widthPx.toDp() }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selected && directEditMode) Modifier.border(1.dp, Color.White) else Modifier)
                    .then(directModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (cue.english.isNotBlank()) {
                    CaptionOutlinedText(
                        modifier = Modifier.fillMaxWidth(),
                        plan = englishPlan ?: error("English paint plan missing"),
                    )
                }
                if (cue.chinese.isNotBlank()) {
                    CaptionOutlinedText(
                        modifier = Modifier.fillMaxWidth(),
                        plan = chinesePlan ?: error("Chinese paint plan missing"),
                    )
                }
            }
            if (selected && directEditMode) {
                DirectEditDeleteHandle(
                    modifier = Modifier.align(Alignment.TopStart).offset((-24).dp, (-24).dp),
                    onDelete = onDelete,
                )
                DirectEditWidthHandle(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = 24.dp),
                    onDrag = { deltaPx ->
                        transientLayout = transientLayout.withDirectEditWidth(
                            transientLayout.widthRatio + deltaPx / geometry.videoRect.width,
                        )
                    },
                    onCommit = { onWidthCommitted(cue.id, transientLayout.widthRatio) },
                )
                DirectEditFontSizeHandle(
                    modifier = Modifier.align(Alignment.BottomEnd).offset(24.dp, 24.dp),
                    onDrag = { deltaPx ->
                        transientFontSizeRatio = canonicalCaptionFontSizeRatio(
                            transientFontSizeRatio + deltaPx / geometry.videoRect.height,
                        )
                    },
                    onCommit = { onFontSizeCommitted(cue.id, transientFontSizeRatio) },
                )
            }
        }
    }
}

internal val DirectEditTouchTarget = 48.dp

@Composable
internal fun DirectEditDeleteHandle(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(DirectEditTouchTarget)
            .semantics { contentDescription = "删除当前字幕" }
            .clickable(onClick = onDelete),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun DirectEditWidthHandle(
    onDrag: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(DirectEditTouchTarget)
            .semantics { contentDescription = "左右拉伸字幕宽度" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onCommit,
                    onDragCancel = {},
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}

@Composable
internal fun DirectEditFontSizeHandle(
    onDrag: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(DirectEditTouchTarget)
            .semantics { contentDescription = "缩放字幕字号" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onCommit,
                    onDragCancel = {},
                ) { change, amount ->
                    change.consume()
                    onDrag((amount.x - amount.y) * 0.5f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("↘", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun CaptionOutlinedText(
    plan: CaptionPaintPlan,
    modifier: Modifier = Modifier,
) {
    val stroke = plan.stroke
    val fill = plan.fill
    val density = LocalDensity.current
    var textLayout by remember(plan) { mutableStateOf<TextLayoutResult?>(null) }
    val fontSize = CaptionRenderResolver.physicalPixelsToSp(
        physicalPixels = stroke.fontSizePx,
        density = density.density,
        fontScale = density.fontScale,
    ).sp
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = fontSize,
        fontFamily = subtitleFontFamily(stroke.fontFamily),
        fontWeight = if (stroke.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (stroke.italic) FontStyle.Italic else FontStyle.Normal,
        textAlign = stroke.alignment.toTextAlign(),
    )
    val backgroundModifier = plan.background?.let { background ->
        Modifier.drawBehind {
            val result = textLayout ?: return@drawBehind
            val color = parseComposeColor(background.colorHex, Color.Black)
            val padding = background.boxPaddingPx.toFloat()
            repeat(result.lineCount) { lineIndex ->
                val left = result.getLineLeft(lineIndex) - padding
                val right = result.getLineRight(lineIndex) + padding
                val top = result.getLineTop(lineIndex) - padding
                val bottom = result.getLineBottom(lineIndex) + padding
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                )
            }
        }
    } ?: Modifier
    Box(modifier = modifier.then(backgroundModifier)) {
        if (stroke.outlineWidthPx > 0) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stroke.text,
                color = parseComposeColor(stroke.colorHex, Color.Black),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = baseStyle.copy(drawStyle = Stroke(width = stroke.outlineWidthPx.toFloat())),
            )
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = fill.text,
            color = parseComposeColor(fill.colorHex, Color.White),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayout = it },
            style = baseStyle.copy(drawStyle = Fill),
        )
    }
}

internal fun CaptionAlignment.toTextAlign(): TextAlign = when (this) {
    CaptionAlignment.LEFT -> TextAlign.Start
    CaptionAlignment.CENTER -> TextAlign.Center
    CaptionAlignment.RIGHT -> TextAlign.End
}

internal fun subtitleFontFamily(fontFamily: String): FontFamily = when (fontFamily) {
    SUBTITLE_FONT_SERIF -> FontFamily.Serif
    SUBTITLE_FONT_MONO -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

internal fun parseComposeColor(value: String, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(value)) }
        .getOrDefault(fallback)
}
