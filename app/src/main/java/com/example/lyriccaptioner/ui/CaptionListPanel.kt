package com.example.lyriccaptioner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle

@Composable
internal fun CaptionList(
    captions: List<CaptionCue>,
    selectedId: String?,
    defaultStyle: DefaultCaptionStyle,
    captionLayout: CaptionLayout,
    onSelect: (String) -> Unit,
    onEnglishChanged: (String, String) -> Unit,
    onChineseChanged: (String, String) -> Unit,
    onApplyCandidate: (String, String) -> Unit,
    onShiftStart: (String, Long) -> Unit,
    onShiftEnd: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onFontSmaller: (String, Int) -> Unit,
    onFontLarger: (String, Int) -> Unit,
    onEnglishColorChanged: (String, String) -> Unit,
    onChineseColorChanged: (String, String) -> Unit,
    onOutlineColorChanged: (String, String) -> Unit,
    onFontFamilyChanged: (String, String) -> Unit,
    onToggleBold: (String) -> Unit,
    onToggleItalic: (String) -> Unit,
    onAlignmentChanged: (String, CaptionAlignment) -> Unit,
    onPositionChanged: (String, Int) -> Unit,
    onClearOverride: (String) -> Unit,
    enabled: Boolean,
    editorSnapshot: String,
    modifier: Modifier = Modifier,
) {
    if (captions.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth().semantics { contentDescription = "caption_list" },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("字幕列表将在识别或导入后显示", color = Color(0xFF9EA5B1))
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "caption_list" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .clearAndSetSemantics { contentDescription = "caption_state:$editorSnapshot" },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("字幕列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${captions.size} 条", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9EA5B1))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(captions, key = { it.id }) { cue ->
                    CaptionCard(
                        cue = cue,
                        selected = cue.id == selectedId,
                        enabled = enabled,
                        onSelect = { onSelect(cue.id) },
                        onEnglishChanged = { onEnglishChanged(cue.id, it) },
                        onChineseChanged = { onChineseChanged(cue.id, it) },
                        onApplyCandidate = { onApplyCandidate(cue.id, it) },
                        onShiftStart = { onShiftStart(cue.id, it) },
                        onShiftEnd = { onShiftEnd(cue.id, it) },
                        onDelete = { onDelete(cue.id) },
                        onConfirm = { onConfirm(cue.id) },
                        defaultStyle = defaultStyle,
                        captionLayout = captionLayout,
                        onFontSmaller = { delta -> onFontSmaller(cue.id, delta) },
                        onFontLarger = { delta -> onFontLarger(cue.id, delta) },
                        onEnglishColorChanged = { color -> onEnglishColorChanged(cue.id, color) },
                        onChineseColorChanged = { color -> onChineseColorChanged(cue.id, color) },
                        onOutlineColorChanged = { color -> onOutlineColorChanged(cue.id, color) },
                        onFontFamilyChanged = { font -> onFontFamilyChanged(cue.id, font) },
                        onToggleBold = { onToggleBold(cue.id) },
                        onToggleItalic = { onToggleItalic(cue.id) },
                        onAlignmentChanged = { alignment -> onAlignmentChanged(cue.id, alignment) },
                        onPositionChanged = { delta -> onPositionChanged(cue.id, delta) },
                        onClearOverride = { onClearOverride(cue.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptionCard(
    cue: CaptionCue,
    selected: Boolean,
    enabled: Boolean,
    defaultStyle: DefaultCaptionStyle,
    captionLayout: CaptionLayout,
    onSelect: () -> Unit,
    onEnglishChanged: (String) -> Unit,
    onChineseChanged: (String) -> Unit,
    onApplyCandidate: (String) -> Unit,
    onShiftStart: (Long) -> Unit,
    onShiftEnd: (Long) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
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
    onClearOverride: () -> Unit,
) {
    var styleExpanded by remember(cue.id) { mutableStateOf(false) }
    val containerColor = when {
        cue.confirmed -> Color(0xFF1C3328)
        cue.needsReview -> Color(0xFF3A3020)
        selected -> Color(0xFF253A28)
        else -> Color(0xFF1B1F25)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "caption_card:${cue.id}" }
            .clickable(enabled = enabled, onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${cue.startMs / 1000.0}s - ${cue.endMs / 1000.0}s",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "置信度 ${(cue.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(
                modifier = Modifier.semantics { contentDescription = "cue_style_toggle:${cue.id}" },
                enabled = enabled,
                onClick = { styleExpanded = !styleExpanded },
            ) {
                Text(if (styleExpanded) "收起样式" else "样式/位置")
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TimingControl(
                    label = "开始",
                    enabled = enabled,
                    onEarlier = { onShiftStart(-100L) },
                    onLater = { onShiftStart(100L) },
                )
                TimingControl(
                    label = "结束",
                    enabled = enabled,
                    onEarlier = { onShiftEnd(-100L) },
                    onLater = { onShiftEnd(100L) },
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = cue.english,
                onValueChange = onEnglishChanged,
                enabled = enabled,
                    label = { Text("英文字幕") },
                singleLine = false,
            )
            if (cue.correctionCandidates.isNotEmpty()) {
                Text(
                    text = "纠错候选",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    cue.correctionCandidates.forEach { candidate ->
                        TextButton(
                            enabled = enabled,
                            onClick = { onApplyCandidate(candidate) },
                        ) {
                            Text(candidate)
                        }
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = cue.chinese,
                onValueChange = onChineseChanged,
                enabled = enabled,
                    label = { Text("中文字幕") },
                singleLine = false,
            )
            if (styleExpanded) {
                CueStyleControls(
                    cue = cue,
                    defaultStyle = defaultStyle,
                    captionLayout = captionLayout,
                    enabled = enabled,
                    onFontSmaller = onFontSmaller,
                    onFontLarger = onFontLarger,
                    onEnglishColorChanged = onEnglishColorChanged,
                    onChineseColorChanged = onChineseColorChanged,
                    onOutlineColorChanged = onOutlineColorChanged,
                    onFontFamilyChanged = onFontFamilyChanged,
                    onToggleBold = onToggleBold,
                    onToggleItalic = onToggleItalic,
                    onAlignmentChanged = onAlignmentChanged,
                    onPositionChanged = onPositionChanged,
                    onClearOverride = onClearOverride,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = when {
                        cue.confirmed -> "已确认"
                        cue.needsReview -> "建议复核"
                        else -> "待确认"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(
                    enabled = enabled && cue.canConfirm && !cue.confirmed,
                    onClick = onConfirm,
                ) {
                    Text(if (cue.confirmed) "已确认" else "确认")
                }
                TextButton(enabled = enabled, onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
internal fun TimingControl(
    label: String,
    enabled: Boolean,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(48.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(enabled = enabled, onClick = onEarlier) { Text("−0.1 秒") }
        TextButton(enabled = enabled, onClick = onLater) { Text("＋0.1 秒") }
    }
}
