package com.example.lyriccaptioner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionBasicStylePreset
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.resolveCaptionStyle

internal enum class DirectEditPanelTab(val label: String) {
    KEYBOARD("键盘"),
    STYLE("样式"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DirectCaptionEditPanel(
    cue: CaptionCue?,
    defaultStyle: DefaultCaptionStyle,
    enabled: Boolean,
    onEnglishChanged: (String, String) -> Unit,
    onChineseChanged: (String, String) -> Unit,
    onApplyBasicStyle: (String, CaptionBasicStylePreset) -> Unit,
    onUnifiedColorChanged: (String, String) -> Unit,
    onAlignmentChanged: (String, CaptionAlignment) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(DirectEditPanelTab.KEYBOARD) }
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .semantics { contentDescription = "字幕直接编辑面板" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DirectEditPanelTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    if (isSelected) {
                        Button(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            onClick = { activeTab = tab },
                        ) { Text(tab.label) }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            onClick = { activeTab = tab },
                        ) { Text(tab.label) }
                    }
                }
            }
            if (cue == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请先点击视频中的字幕", color = Color(0xFF9EA5B1))
                }
            } else if (activeTab == DirectEditPanelTab.KEYBOARD) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "编辑英文字幕" },
                        value = cue.english,
                        onValueChange = { onEnglishChanged(cue.id, it) },
                        enabled = enabled,
                        label = { Text("英文字幕") },
                        minLines = 1,
                        maxLines = 3,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "编辑中文字幕" },
                        value = cue.chinese,
                        onValueChange = { onChineseChanged(cue.id, it) },
                        enabled = enabled,
                        label = { Text("中文字幕") },
                        minLines = 1,
                        maxLines = 3,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { contentDescription = "完成字幕文字编辑" },
                        enabled = enabled,
                        onClick = { keyboardController?.hide() },
                    ) {
                        Text("完成")
                    }
                }
            } else {
                val resolvedStyle = resolveCaptionStyle(defaultStyle, cue.styleOverride)
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DirectStyleGroupTitle("基础样式")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        directBasicStyles.forEach { (preset, label) ->
                            OutlinedButton(
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .semantics { contentDescription = "应用基础样式:$label" },
                                enabled = enabled,
                                onClick = { onApplyBasicStyle(cue.id, preset) },
                            ) { Text(label) }
                        }
                    }
                    DirectStyleGroupTitle("文字颜色")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        directTextColors.forEach { (hex, label) ->
                            val selectedColor = resolvedStyle.primaryColorHex.equals(hex, ignoreCase = true) &&
                                resolvedStyle.secondaryColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(DirectEditTouchTarget)
                                    .semantics { contentDescription = "字幕文字颜色:$label" }
                                    .clickable(enabled = enabled) { onUnifiedColorChanged(cue.id, hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(if (selectedColor) 34.dp else 30.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(parseComposeColor(hex, Color.White))
                                        .then(if (selectedColor) Modifier.border(2.dp, Color(0xFFB7F36B), RoundedCornerShape(17.dp)) else Modifier),
                                )
                            }
                        }
                    }
                    DirectStyleGroupTitle("对齐方式")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            CaptionAlignment.LEFT to "左对齐",
                            CaptionAlignment.CENTER to "居中对齐",
                            CaptionAlignment.RIGHT to "右对齐",
                        ).forEach { (alignment, label) ->
                            val isSelected = resolvedStyle.alignment == alignment
                            if (isSelected) {
                                Button(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        .semantics { contentDescription = label },
                                    enabled = enabled,
                                    onClick = { onAlignmentChanged(cue.id, alignment) },
                                ) { Text(label.take(1)) }
                            } else {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        .semantics { contentDescription = label },
                                    enabled = enabled,
                                    onClick = { onAlignmentChanged(cue.id, alignment) },
                                ) { Text(label.take(1)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectStyleGroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

internal val directBasicStyles = listOf(
    CaptionBasicStylePreset.PLAIN_TEXT to "纯文字",
    CaptionBasicStylePreset.DARK_OUTLINE to "黑描边",
    CaptionBasicStylePreset.LIGHT_OUTLINE to "白描边",
    CaptionBasicStylePreset.LIGHT_BACKGROUND to "浅色底",
    CaptionBasicStylePreset.DARK_BACKGROUND to "深色底",
    CaptionBasicStylePreset.GRAY_BACKGROUND to "灰色底",
)

internal val directTextColors = listOf(
    "#FFFFFF" to "白色",
    "#000000" to "黑色",
    "#EF4444" to "红色",
    "#FB923C" to "橙色",
    "#FACC15" to "黄色",
    "#6CCB5F" to "绿色",
    "#5EC9B8" to "青色",
    "#3B82F6" to "蓝色",
)
