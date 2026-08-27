package com.example.lyriccaptioner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionBasicStylePreset
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.resolveCaptionLayout
import com.example.lyriccaptioner.model.resolveCaptionStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DefaultCaptionStyleControls(
    style: DefaultCaptionStyle,
    layout: CaptionLayout,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onMarginLower: () -> Unit,
    onMarginHigher: () -> Unit,
    onEnglishColorChanged: (String) -> Unit,
    onChineseColorChanged: (String) -> Unit,
    onOutlineColorChanged: (String) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onAlignmentChanged: (CaptionAlignment) -> Unit,
) {
    val positionPercent = (layout.yRatio * 100f).toInt()
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF12151A),
            contentColor = Color(0xFFF4F5F7),
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .semantics { contentDescription = "style_controls" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .clearAndSetSemantics {
                        contentDescription =
                            "style_state:${style.primaryColorHex}:${style.secondaryColorHex}:" +
                                "${style.outlineColorHex}:${style.fontFamily}:${style.fontSizeSp}:$positionPercent"
                    },
            )
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .clearAndSetSemantics {
                        contentDescription =
                            "default_style_state:${style.primaryColorHex}:${style.secondaryColorHex}:" +
                                "${style.outlineColorHex}:${style.fontFamily}:${style.fontSizeSp}:" +
                                "${style.bold}:${style.italic}:${style.alignment}:${layout.xRatio}:" +
                                "${layout.yRatio}:${layout.widthRatio}"
                    },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("项目默认样式", fontWeight = FontWeight.SemiBold)
                Text("字号 ${style.fontSizeSp}sp · 垂直位置 $positionPercent%", color = Color(0xFF9EA5B1))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onFontSmaller) { Text("A-") }
                TextButton(onClick = onFontLarger) { Text("A+") }
                TextButton(onClick = onToggleBold) { Text(if (style.bold) "取消粗体" else "粗体") }
                TextButton(onClick = onToggleItalic) { Text(if (style.italic) "取消斜体" else "斜体") }
                CaptionAlignment.entries.forEach { alignment ->
                    TextButton(onClick = { onAlignmentChanged(alignment) }) { Text(alignmentLabel(alignment)) }
                }
                TextButton(onClick = onMarginLower) { Text("下移") }
                TextButton(onClick = onMarginHigher) { Text("上移") }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("字体", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onFontFamilyChanged(SUBTITLE_FONT_SANS) }) { Text("无衬线") }
                TextButton(onClick = { onFontFamilyChanged(SUBTITLE_FONT_SERIF) }) { Text("衬线") }
                TextButton(onClick = { onFontFamilyChanged(SUBTITLE_FONT_MONO) }) { Text("等宽") }
                Text("当前 ${fontFamilyLabel(style.fontFamily)}", color = Color(0xFF9EA5B1))
            }
            SubtitleColorPalette("英文", style.primaryColorHex, onColorSelected = onEnglishColorChanged)
            SubtitleColorPalette("中文", style.secondaryColorHex, onColorSelected = onChineseColorChanged)
            SubtitleColorPalette("描边", style.outlineColorHex, onColorSelected = onOutlineColorChanged)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CueStyleControls(
    cue: CaptionCue,
    defaultStyle: DefaultCaptionStyle,
    captionLayout: CaptionLayout,
    globalMode: Boolean = false,
    globalHasOverride: Boolean = false,
    enabled: Boolean,
    onBasicStyle: (CaptionBasicStylePreset) -> Unit = {},
    onFontSmaller: (Int) -> Unit,
    onFontLarger: (Int) -> Unit,
    onEnglishColorChanged: (String) -> Unit,
    onChineseColorChanged: (String) -> Unit,
    onOutlineColorChanged: (String) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onAlignmentChanged: (CaptionAlignment) -> Unit,
    onPositionChanged: (Int) -> Unit,
    onWidthChanged: (Float) -> Unit = {},
    onClearOverride: () -> Unit,
) {
    val uiState = captionStyleUiState(defaultStyle, cue)
    val style = if (globalMode) resolveCaptionStyle(defaultStyle, null) else uiState.resolved
    val resolvedLayout = if (globalMode) captionLayout else resolveCaptionLayout(captionLayout, cue.layoutOverride)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "cue_style_controls:${cue.id}" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(1.dp)
                .clearAndSetSemantics {
                    contentDescription =
                        "cue_style_state:${cue.id}:${uiState.hasOverride}:${style.primaryColorHex}:" +
                            "${style.secondaryColorHex}:${style.outlineColorHex}:${style.fontFamily}:" +
                            "${style.fontSizeSp}:${style.bold}:${style.italic}:${style.alignment}:" +
                            "${resolvedLayout.xRatio}:${resolvedLayout.yRatio}:${resolvedLayout.widthRatio}"
                },
        )
        Text(
            text = when {
                globalMode -> "修改项目默认样式；对应的单条覆盖将被清除"
                uiState.hasOverride -> "已覆盖，未设置字段继承项目默认"
                else -> "继承项目默认样式"
            },
            color = Color(0xFF9EA5B1),
            style = MaterialTheme.typography.labelMedium,
        )
        Text("基础样式", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            directBasicStyles.forEach { (preset, label) ->
                OutlinedButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = enabled,
                    onClick = { onBasicStyle(preset) },
                ) { Text(label) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(enabled = enabled, onClick = { onFontSmaller(-2) }) { Text("A-") }
            TextButton(enabled = enabled, onClick = { onFontLarger(2) }) { Text("A+") }
            Text("当前 ${style.fontSizeSp}sp", color = Color(0xFF9EA5B1))
            TextButton(enabled = enabled, onClick = onToggleBold) { Text(if (style.bold) "取消粗体" else "粗体") }
            TextButton(enabled = enabled, onClick = onToggleItalic) { Text(if (style.italic) "取消斜体" else "斜体") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("对齐", style = MaterialTheme.typography.labelMedium)
            CaptionAlignment.entries.forEach { alignment ->
                TextButton(enabled = enabled, onClick = { onAlignmentChanged(alignment) }) {
                    Text(alignmentLabel(alignment))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("字体", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_SANS) }) { Text("无衬线") }
            TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_SERIF) }) { Text("衬线") }
            TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_MONO) }) { Text("等宽") }
        }
        val positionPercent = (resolvedLayout.yRatio * 100f).toInt()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("位置 ${positionPercent}%", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = enabled, onClick = { onPositionChanged(-2) }) { Text("下移") }
            TextButton(enabled = enabled, onClick = { onPositionChanged(2) }) { Text("上移") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("文本框宽度 ${(resolvedLayout.widthRatio * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(enabled = enabled, onClick = { onWidthChanged(-0.05f) }) { Text("变窄") }
            TextButton(enabled = enabled, onClick = { onWidthChanged(0.05f) }) { Text("变宽") }
        }
        SubtitleColorPalette("英文", style.primaryColorHex, enabled, onEnglishColorChanged)
        SubtitleColorPalette("中文", style.secondaryColorHex, enabled, onChineseColorChanged)
        SubtitleColorPalette("描边", style.outlineColorHex, enabled, onOutlineColorChanged)
        OutlinedButton(
            modifier = Modifier.semantics { contentDescription = "clear_cue_style_override:${cue.id}" },
            enabled = enabled && if (globalMode) globalHasOverride else uiState.hasOverride,
            onClick = onClearOverride,
        ) {
            Text(if (globalMode) "清除全部字幕覆盖" else "清除单条覆盖")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectedCueStyleControls(
    cue: CaptionCue?,
    defaultStyle: DefaultCaptionStyle,
    enabled: Boolean,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onEnglishColorChanged: (String) -> Unit,
    onChineseColorChanged: (String) -> Unit,
    onOutlineColorChanged: (String) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onAlignmentChanged: (CaptionAlignment) -> Unit,
    onClearOverride: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "cue_style_controls" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF12151A),
            contentColor = Color(0xFFF4F5F7),
        ),
    ) {
        if (cue == null) {
            Text(
                text = "选择一条字幕后可设置单条样式覆盖",
                modifier = Modifier.padding(12.dp),
                color = Color(0xFF9EA5B1),
            )
            return@Card
        }

        val uiState = captionStyleUiState(defaultStyle, cue)
        val style = uiState.resolved
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .clearAndSetSemantics {
                        contentDescription =
                            "cue_style_state:${cue.id}:${uiState.hasOverride}:${style.primaryColorHex}:" +
                                "${style.secondaryColorHex}:${style.outlineColorHex}:${style.fontFamily}:" +
                                "${style.fontSizeSp}:${style.bold}:${style.italic}:${style.alignment}"
                    },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("当前字幕覆盖", fontWeight = FontWeight.SemiBold)
                Text(
                    if (uiState.hasOverride) "已覆盖，未设置字段继承项目默认" else "继承项目默认样式",
                    color = Color(0xFF9EA5B1),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(enabled = enabled, onClick = onFontSmaller) { Text("A-") }
                TextButton(enabled = enabled, onClick = onFontLarger) { Text("A+") }
                Text("当前 ${style.fontSizeSp}sp", color = Color(0xFF9EA5B1))
                TextButton(enabled = enabled, onClick = onToggleBold) { Text(if (style.bold) "取消粗体" else "粗体") }
                TextButton(enabled = enabled, onClick = onToggleItalic) { Text(if (style.italic) "取消斜体" else "斜体") }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("对齐", style = MaterialTheme.typography.labelMedium)
                CaptionAlignment.entries.forEach { alignment ->
                    TextButton(enabled = enabled, onClick = { onAlignmentChanged(alignment) }) {
                        Text(alignmentLabel(alignment))
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("字体", style = MaterialTheme.typography.labelMedium)
                TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_SANS) }) { Text("无衬线") }
                TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_SERIF) }) { Text("衬线") }
                TextButton(enabled = enabled, onClick = { onFontFamilyChanged(SUBTITLE_FONT_MONO) }) { Text("等宽") }
            }
            SubtitleColorPalette("英文", style.primaryColorHex, enabled, onEnglishColorChanged)
            SubtitleColorPalette("中文", style.secondaryColorHex, enabled, onChineseColorChanged)
            SubtitleColorPalette("描边", style.outlineColorHex, enabled, onOutlineColorChanged)
            OutlinedButton(
                modifier = Modifier.semantics { contentDescription = "clear_cue_style_override" },
                enabled = enabled && uiState.hasOverride,
                onClick = onClearOverride,
            ) {
                Text("清除单条覆盖")
            }
        }
    }
}

@Composable
internal fun fontFamilyLabel(fontFamily: String): String = when (fontFamily) {
    SUBTITLE_FONT_SERIF -> "衬线"
    SUBTITLE_FONT_MONO -> "等宽"
    else -> "无衬线"
}

internal fun alignmentLabel(alignment: CaptionAlignment): String = when (alignment) {
    CaptionAlignment.LEFT -> "左"
    CaptionAlignment.CENTER -> "中"
    CaptionAlignment.RIGHT -> "右"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubtitleColorPalette(
    label: String,
    selectedColorHex: String,
    enabled: Boolean = true,
    onColorSelected: (String) -> Unit,
) {
    val colors = listOf("#FFFFFF", "#F4E7A1", "#61D6FF", "#FF8BCB", "#000000")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        colors.forEach { colorHex ->
            val selected = colorHex.equals(selectedColorHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "$label $colorHex" }
                    .clickable(enabled = enabled) { onColorSelected(colorHex) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(parseComposeColor(colorHex, Color.White))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(5.dp),
                        ),
                )
            }
        }
    }
}
