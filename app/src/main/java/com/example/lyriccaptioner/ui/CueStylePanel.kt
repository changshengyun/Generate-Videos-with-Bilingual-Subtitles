package com.example.lyriccaptioner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestionUiState

@Composable
internal fun CueStylePanel(
    cue: CaptionCue,
    index: Int,
    count: Int,
    defaultStyle: DefaultCaptionStyle,
    captionLayout: CaptionLayout,
    styleEditLocked: Boolean,
    hasAnyOverride: Boolean,
    enabled: Boolean,
    onCollapse: () -> Unit,
    onToggleStyleLock: () -> Unit,
    onHeightDrag: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    panelHeight: Dp,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
) {
    Surface(
        modifier = modifier
            .imePadding()
            .height(panelHeight)
            .semantics { contentDescription = "cue_style_fixed_panel:${cue.id}" },
        color = Color(0xFF171A1F),
        contentColor = Color(0xFFF4F5F7),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "style_panel_drag_handle" }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onHeightDrag(dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF747B86)),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "style_panel_collapse" },
                        onClick = onCollapse,
                    ) { Text("收起") }
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = if (styleEditLocked) {
                                "style_lock:locked"
                            } else {
                                "style_lock:unlocked"
                            }
                        },
                        onClick = onToggleStyleLock,
                    ) { Text(if (styleEditLocked) "🔒 全部" else "🔓 单条") }
                }
                Text(
                    "第 ${index + 1}/$count 条 · ${formatPlaybackTime(cue.startMs)}",
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Row {
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "上一条字幕" },
                        enabled = index > 0,
                        onClick = onPrevious,
                    ) { Text("上一条") }
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "下一条字幕" },
                        enabled = index < count - 1,
                        onClick = onNext,
                    ) { Text("下一条") }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (styleEditLocked) "整体样式模式：修改项目默认并清除对应单条覆盖" else "单条样式模式：只修改当前字幕",
                    color = Color(0xFFB7F36B),
                    style = MaterialTheme.typography.labelMedium,
                )
                CueStyleControls(
                    cue = cue,
                    defaultStyle = defaultStyle,
                    captionLayout = captionLayout,
                    globalMode = styleEditLocked,
                    globalHasOverride = hasAnyOverride,
                    enabled = enabled,
                    onBasicStyle = { viewModel.applyCueBasicStyle(cue.id, it) },
                    onFontSmaller = { viewModel.updateCueFontSize(cue.id, it) },
                    onFontLarger = { viewModel.updateCueFontSize(cue.id, it) },
                    onEnglishColorChanged = { viewModel.updateCueEnglishColor(cue.id, it) },
                    onChineseColorChanged = { viewModel.updateCueChineseColor(cue.id, it) },
                    onOutlineColorChanged = { viewModel.updateCueOutlineColor(cue.id, it) },
                    onFontFamilyChanged = { viewModel.updateCueFontFamily(cue.id, it) },
                    onToggleBold = { viewModel.toggleCueBold(cue.id) },
                    onToggleItalic = { viewModel.toggleCueItalic(cue.id) },
                    onAlignmentChanged = { viewModel.updateCueAlignment(cue.id, it) },
                    onPositionChanged = { viewModel.updateCuePosition(cue.id, it) },
                    onWidthChanged = { viewModel.updateCueWidthFromStylePanel(cue.id, it) },
                    onClearOverride = { viewModel.clearCueStyleOverride(cue.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun MergeCaptionDialog(
    cue: CaptionCue,
    canMergePrevious: Boolean,
    canMergeNext: Boolean,
    onMergePrevious: () -> Unit,
    onMergeNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "cue_merge_dialog:${cue.id}"
            },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF171A1F),
                contentColor = Color(0xFFF4F5F7),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("合并字幕", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("选择与当前字幕相邻的一条进行合并", color = Color(0xFF9EA5B1))
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { contentDescription = "merge_with_previous:${cue.id}" },
                    enabled = canMergePrevious,
                    onClick = onMergePrevious,
                ) { Text("与上一条合并") }
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { contentDescription = "merge_with_next:${cue.id}" },
                    enabled = canMergeNext,
                    onClick = onMergeNext,
                ) { Text("与下一条合并") }
                TextButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = onDismiss,
                ) { Text("取消") }
            }
        }
    }
}

@Composable
internal fun CueSuggestionDialog(
    current: CaptionCue,
    suggestion: CaptionCueSuggestionUiState,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val proposal = suggestion.proposal ?: return
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "cue_ai_suggestion:${current.id}" },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF171A1F),
                contentColor = Color(0xFFF4F5F7),
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("AI 增强建议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("当前英文", color = Color(0xFF9EA5B1))
                Text(current.english)
                Text("当前中文", color = Color(0xFF9EA5B1))
                Text(current.chinese)
                Text("AI 建议", color = Color(0xFFFFC857))
                Text(proposal.english)
                Text(proposal.chinese)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onDismiss) { Text("取消") }
                    Button(modifier = Modifier.heightIn(min = 48.dp), onClick = onApply) { Text("应用到此字幕") }
                }
            }
        }
    }
}
