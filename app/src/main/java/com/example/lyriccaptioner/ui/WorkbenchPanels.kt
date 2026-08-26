package com.example.lyriccaptioner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.processing.TranslationModelState

@Composable
internal fun WorkbenchTabs(
    activeSection: Int,
    onSectionSelected: (Int) -> Unit,
) {
    val tabs = listOf(
        Triple("导入", "＋", "workbench_import"),
        Triple("识别/翻译", "▶", "workbench_asr"),
        Triple("字幕编辑", "Aa", "workbench_subtitles"),
        Triple("导出", "⇩", "workbench_export"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, (label, icon, accessibilityId) ->
            val selected = index == activeSection
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clickable { onSectionSelected(index) }
                    .semantics { contentDescription = accessibilityId },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) Color(0xFFB7F36B) else Color(0xFF1B1F25),
                contentColor = if (selected) Color(0xFF162000) else Color(0xFFD5DAE3),
                tonalElevation = if (selected) 2.dp else 0.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(icon, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun RuntimeStatusStrip(
    speechReady: Boolean,
    translationState: TranslationModelState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeStatusChip(
            modifier = Modifier.weight(1f),
            label = "识别",
            value = if (speechReady) "Whisper 就绪" else "Whisper 不可用",
            healthy = speechReady,
        )
        RuntimeStatusChip(
            modifier = Modifier.weight(1f),
            label = "翻译",
            value = when (translationState) {
                TranslationModelState.READY -> "OPUS-MT 就绪"
                TranslationModelState.PREPARING -> "准备中"
                TranslationModelState.NEEDS_INSTALL -> "未安装"
                TranslationModelState.FAILED -> "不可用"
            },
            healthy = translationState == TranslationModelState.READY,
        )
    }
}

@Composable
internal fun RuntimeStatusChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    healthy: Boolean,
) {
    Surface(
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF12151A),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (healthy) Color(0xFFB7F36B) else Color(0xFFFF7B78)),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9EA5B1))
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WorkflowPanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282D35)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9EA5B1))
                }
                content()
            },
        )
    }
}

@Composable
internal fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RowScope.ActionButton(
    icon: String,
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    accessibilityId: String? = null,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .weight(1f)
            .then(
                if (accessibilityId == null) Modifier else Modifier.semantics {
                    contentDescription = accessibilityId
                },
            ),
        enabled = enabled,
        onClick = onClick,
        colors = if (primary) {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB7F36B),
                contentColor = Color(0xFF162000),
                disabledContainerColor = Color(0xFF30352B),
                disabledContentColor = Color(0xFF737B6A),
            )
        } else {
            androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2B3038),
                contentColor = Color(0xFFF4F5F7),
                disabledContainerColor = Color(0xFF202329),
                disabledContentColor = Color(0xFF666C76),
            )
        },
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(icon, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun RowScope.SecondaryAction(
    label: String,
    enabled: Boolean,
    accessibilityId: String? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .weight(1f)
            .then(
                if (accessibilityId == null) Modifier else Modifier.semantics {
                    contentDescription = accessibilityId
                },
            ),
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF454C57)),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFFD5DAE3),
            disabledContentColor = Color(0xFF666C76),
        ),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TranslationRuntimeStatus(state: TranslationModelState) {
    RuntimeStatusCard(
        label = "翻译",
        value = "本地 OPUS-MT · ${when (state) {
            TranslationModelState.READY -> "就绪"
            TranslationModelState.PREPARING -> "准备中"
            TranslationModelState.NEEDS_INSTALL -> "未安装"
            TranslationModelState.FAILED -> "不可用"
        }}",
        healthy = state == TranslationModelState.READY,
    )
}

@Composable
internal fun SpeechRuntimeStatus(
    modelInstalled: Boolean,
    nativeReady: Boolean,
    mode: SpeechMode,
) {
    RuntimeStatusCard(
        label = "识别",
        value = if (mode == SpeechMode.LOCAL && modelInstalled && nativeReady) {
            "本地 Whisper · 就绪"
        } else {
            "本地 Whisper · 不可用"
        },
        healthy = mode == SpeechMode.LOCAL && modelInstalled && nativeReady,
        detail = if (mode == SpeechMode.LOCAL && modelInstalled && nativeReady) {
            "本地 JNI 已就绪"
        } else {
            "请检查模型和 JNI"
        },
    )
}

@Composable
internal fun RuntimeStatusCard(
    label: String,
    value: String,
    healthy: Boolean,
    detail: String = "",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (healthy) Color(0xFFB7F36B) else Color(0xFFFF7B78)),
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF9EA5B1))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9EA5B1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
