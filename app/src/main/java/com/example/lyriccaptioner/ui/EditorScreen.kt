package com.example.lyriccaptioner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.captions.CaptionTimeline
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.SubtitleStyle
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.VideoImportMode
import com.example.lyriccaptioner.processing.TranslationModelState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showPasteLyrics by remember { mutableStateOf(false) }
    var pastedLyrics by remember { mutableStateOf("") }
    var videoImportMode by remember { mutableStateOf(VideoImportMode.NEW_VIDEO) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importVideo(uri, videoImportMode)
        }
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSrt(readText(context, uri))
        }
    }
    val lyricPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.applyLyricText(readText(context, uri))
        }
    }
    val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importProjectArchive(uri)
        }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importWhisperModel(uri)
        }
    }
    val srtCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip"),
    ) { uri: Uri? ->
        val srt = state.pendingSidecarSrt
        if (uri != null && srt != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(srt.toByteArray(Charsets.UTF_8))
                } ?: error("No output stream")
            }.onSuccess {
                viewModel.sidecarSrtSaved(uri)
            }.onFailure { error ->
                viewModel.sidecarSrtSaveFailed(error.message ?: "unknown error")
            }
        }
    }
    val projectCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.saveProjectArchive(uri)
    }
    val videoCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4"),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportVideo(uri)
        }
    }

    LaunchedEffect(state.pendingSidecarSrt) {
        if (state.pendingSidecarSrt != null) {
            srtCreator.launch("lyric-captioner.srt")
        }
    }

    if (showPasteLyrics) {
        AlertDialog(
            onDismissRequest = { showPasteLyrics = false },
            title = { Text("粘贴英文歌词") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = pastedLyrics,
                    onValueChange = { pastedLyrics = it },
                    label = { Text("每行对应一条字幕") },
                    minLines = 8,
                    maxLines = 12,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCaptionsFromLyrics(pastedLyrics)
                        showPasteLyrics = false
                    },
                ) { Text("生成字幕") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteLyrics = false }) { Text("取消") }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0F12),
        contentColor = Color(0xFFF4F5F7),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.74f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header()
                VideoPreview(
                    videoUri = state.videoUri.takeUnless { state.mediaState == MediaState.UNAVAILABLE },
                    captions = state.captions,
                    selectedCaptionId = state.selectedCaptionId,
                    subtitleStyle = state.exportProfile.subtitleStyle,
                    status = state.status,
                    isWorking = state.isWorking,
                )
                SpeechRuntimeStatus(
                    modelInstalled = state.modelState.speechModelInstalled,
                    nativeReady = state.modelState.speechNativeLibraryReady,
                    mode = state.modelState.speechMode,
                )
                TranslationRuntimeStatus(state.modelState.translationModelState)
                WorkflowPanel(title = "1  导入与项目", subtitle = "先选择视频，或打开已有项目") {
                    ActionRow {
                        ActionButton(
                            icon = "＋",
                            label = if (state.mediaState == MediaState.UNAVAILABLE) "重新绑定视频" else "导入视频",
                            enabled = !state.isWorking,
                            primary = true,
                            accessibilityId = "import_video",
                            onClick = {
                                videoImportMode = if (state.mediaState == MediaState.UNAVAILABLE) {
                                    VideoImportMode.RELINK
                                } else {
                                    VideoImportMode.NEW_VIDEO
                                }
                                videoPicker.launch(arrayOf("video/*"))
                            },
                        )
                        ActionButton(
                            icon = "▣",
                            label = "打开项目",
                            enabled = !state.isWorking,
                            onClick = { projectPicker.launch(arrayOf("application/octet-stream", "text/plain")) },
                        )
                    }
                    ActionRow {
                        SecondaryAction("字幕 SRT", !state.isWorking, accessibilityId = "import_srt") { srtPicker.launch("*/*") }
                        SecondaryAction("Whisper 模型", !state.isWorking) { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }
                    }
                }
                WorkflowPanel(title = "2  识别与翻译", subtitle = "使用本地模型生成英文并翻译成中文") {
                    ActionRow {
                        ActionButton(
                            icon = "▶",
                            label = if (state.modelState.speechMode == SpeechMode.LOCAL) "生成字幕" else "识别不可用",
                            enabled = state.videoUri != null && !state.isWorking && state.modelState.speechMode == SpeechMode.LOCAL,
                            primary = true,
                            onClick = viewModel::generateCaptions,
                        )
                        ActionButton(
                            icon = "中",
                            label = "翻译中文",
                            enabled = state.captions.any { it.english.isNotBlank() && it.chinese.isBlank() } && !state.isWorking,
                            onClick = viewModel::translateMissingChinese,
                        )
                    }
                    if (state.asrRunning || state.translationRunning) {
                        ActionRow {
                            SecondaryAction("取消当前任务", true) {
                                if (state.asrRunning) viewModel.cancelGenerateCaptions()
                                if (state.translationRunning) viewModel.cancelTranslation()
                            }
                        }
                    }
                }
                WorkflowPanel(title = "3  字幕编辑", subtitle = "检查文本、调整时间并确认字幕") {
                    ActionRow {
                        SecondaryAction("添加字幕", state.videoUri != null && !state.isWorking) { viewModel.addCaption() }
                        SecondaryAction("导入歌词", state.captions.isNotEmpty() && !state.isWorking) { lyricPicker.launch("text/*") }
                        SecondaryAction("粘贴歌词", state.videoUri != null && !state.isWorking) { showPasteLyrics = true }
                    }
                    SubtitleStyleControls(
                        fontSizeSp = state.exportProfile.subtitleStyle.fontSizeSp,
                        bottomMarginPercent = state.exportProfile.subtitleStyle.bottomMarginPercent,
                        fontFamily = state.exportProfile.subtitleStyle.fontFamily,
                        onFontSmaller = { viewModel.updateFontSize(-2) },
                        onFontLarger = { viewModel.updateFontSize(2) },
                        onMarginLower = { viewModel.updateBottomMargin(-2) },
                        onMarginHigher = { viewModel.updateBottomMargin(2) },
                        primaryColorHex = state.exportProfile.subtitleStyle.primaryColorHex,
                        secondaryColorHex = state.exportProfile.subtitleStyle.secondaryColorHex,
                        outlineColorHex = state.exportProfile.subtitleStyle.outlineColorHex,
                        onEnglishColorChanged = viewModel::updateEnglishColor,
                        onChineseColorChanged = viewModel::updateChineseColor,
                        onOutlineColorChanged = viewModel::updateOutlineColor,
                        onFontFamilyChanged = viewModel::updateFontFamily,
                    )
                }
                WorkflowPanel(title = "4  导出与分享", subtitle = "保存项目、导出视频或分享成品") {
                    ActionRow {
                        ActionButton(
                            icon = "⇩",
                            label = "导出视频",
                            enabled = state.videoUri != null && state.captions.isNotEmpty() && !state.isWorking,
                            primary = true,
                            onClick = { videoCreator.launch(state.exportProfile.outputName) },
                        )
                        ActionButton(
                            icon = "↗",
                            label = "分享视频",
                            enabled = state.exportUri != null && !state.isWorking,
                            onClick = { state.exportUri?.let { shareExportedVideo(context, it) } },
                        )
                    }
                    ActionRow {
                        SecondaryAction("保存项目", state.captions.isNotEmpty() && !state.isWorking) { projectCreator.launch("lyric-captioner-project.lcp") }
                        SecondaryAction("导出 SRT", state.captions.isNotEmpty() && !state.isWorking) { viewModel.exportSidecarSrt() }
                        if (state.isWorking && !state.translationRunning && !state.asrRunning) {
                            SecondaryAction("取消导出", true, onClick = viewModel::cancelExport)
                        }
                    }
                }
            }
            CaptionList(
                captions = state.captions,
                selectedId = state.selectedCaptionId,
                onSelect = viewModel::selectCue,
                onEnglishChanged = viewModel::updateEnglishText,
                onChineseChanged = viewModel::updateChineseText,
                onApplyCandidate = viewModel::applyCorrectionCandidate,
                onShiftStart = viewModel::shiftCueStart,
                onShiftEnd = viewModel::shiftCueEnd,
                onDelete = viewModel::deleteCaption,
                onConfirm = viewModel::confirmCue,
                enabled = !state.isWorking,
                modifier = Modifier.weight(0.26f),
            )
        }
    }
}

@Composable
private fun WorkflowPanel(
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.ActionButton(
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
private fun RowScope.SecondaryAction(
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
private fun TranslationRuntimeStatus(state: TranslationModelState) {
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
private fun SpeechRuntimeStatus(
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
private fun RuntimeStatusCard(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleStyleControls(
    fontSizeSp: Int,
    bottomMarginPercent: Int,
    fontFamily: String,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onMarginLower: () -> Unit,
    onMarginHigher: () -> Unit,
    primaryColorHex: String,
    secondaryColorHex: String,
    outlineColorHex: String,
    onEnglishColorChanged: (String) -> Unit,
    onChineseColorChanged: (String) -> Unit,
    onOutlineColorChanged: (String) -> Unit,
    onFontFamilyChanged: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("字幕样式", fontWeight = FontWeight.SemiBold)
                Text("字号 ${fontSizeSp}sp · 位置 ${bottomMarginPercent}%", color = Color(0xFF9EA5B1))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onFontSmaller) { Text("A-") }
                TextButton(onClick = onFontLarger) { Text("A+") }
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
                Text("当前 ${fontFamilyLabel(fontFamily)}", color = Color(0xFF9EA5B1))
            }
            SubtitleColorPalette(
                label = "英文",
                selectedColorHex = primaryColorHex,
                onColorSelected = onEnglishColorChanged,
            )
            SubtitleColorPalette(
                label = "中文",
                selectedColorHex = secondaryColorHex,
                onColorSelected = onChineseColorChanged,
            )
            SubtitleColorPalette(
                label = "描边",
                selectedColorHex = outlineColorHex,
                onColorSelected = onOutlineColorChanged,
            )
        }
    }
}

@Composable
private fun fontFamilyLabel(fontFamily: String): String = when (fontFamily) {
    SUBTITLE_FONT_SERIF -> "衬线"
    SUBTITLE_FONT_MONO -> "等宽"
    else -> "无衬线"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleColorPalette(
    label: String,
    selectedColorHex: String,
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
                    .size(28.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(parseComposeColor(colorHex, Color.White))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .clickable { onColorSelected(colorHex) },
            )
        }
    }
}

private fun readText(context: Context, uri: Uri): String {
    return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
        it.readText()
    }.orEmpty()
}

private fun shareExportedVideo(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享双语视频"))
}


@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "歌词字幕工作台",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "本地双语 · 轻量编辑",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9EA5B1),
        )
        }
        Text("V2", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB7F36B))
    }
}

private fun localizeStatus(status: String): String {
    return when {
        status.isBlank() -> "等待操作"
        status.startsWith("Import a video") -> "导入 5 分钟以内的视频开始编辑"
        status.startsWith("Checking video access") -> "正在检查视频访问权限…"
        status.startsWith("Video imported") -> "视频已导入，可继续识别或编辑"
        status.startsWith("Video re-associated") -> "视频已重新绑定"
        status.startsWith("Could not import video") -> "视频导入失败：${status.substringAfter(": ", "未知原因")}"
        status.startsWith("Preparing") -> "正在准备本地翻译模型…"
        status.startsWith("Translated") -> "中文翻译完成：${status.substringAfter("Translated ").substringBefore(" captions") } 条"
        status.startsWith("Translation") -> "翻译状态：${status.substringAfter(": ", status)}"
        status.startsWith("Created") -> "字幕已生成：${status.substringAfter("Created ").substringBefore(" lyric captions")} 条"
        status.startsWith("Rendering") -> "正在渲染双语字幕…"
        status.startsWith("Export complete") -> "视频导出完成"
        status.startsWith("Video export") -> "导出状态：${status.substringAfter(": ", status)}"
        status.startsWith("ASR") -> "识别状态：${status.substringAfter(": ", status)}"
        status.startsWith("Project") -> "项目状态：${status.substringAfter(": ", status)}"
        status.startsWith("SRT") -> "SRT 状态：${status.substringAfter(": ", status)}"
        else -> status
    }
}

@Composable
private fun VideoPreview(
    videoUri: Uri?,
    captions: List<CaptionCue>,
    selectedCaptionId: String?,
    subtitleStyle: SubtitleStyle,
    status: String,
    isWorking: Boolean,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("视频预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (videoUri == null) "未导入" else "可编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (videoUri == null) Color(0xFFFFB4AB) else Color(0xFFB7F36B),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF171A1F)),
                contentAlignment = Alignment.Center,
            ) {
                if (videoUri == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("＋", color = Color(0xFFB7F36B), fontSize = 28.sp)
                        Text("导入视频开始编辑", color = Color.White)
                    }
                } else if (isWorking) {
                    // Removing PlayerView releases its decoder and Surface before Media3 starts exporting.
                    Text(text = "处理中，预览暂时暂停", color = Color.White)
                } else {
                    VideoPlayer(
                        uri = videoUri,
                        captions = captions,
                        selectedCaptionId = selectedCaptionId,
                        subtitleStyle = subtitleStyle,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = localizeStatus(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD5DAE3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    uri: Uri,
    captions: List<CaptionCue>,
    selectedCaptionId: String?,
    subtitleStyle: SubtitleStyle,
) {
    val context = LocalContext.current
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var fullscreen by remember(uri) { mutableStateOf(false) }
    val timeline = remember(captions) { CaptionTimeline(captions) }
    val currentCue = timeline.cueAt(positionMs)
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(selectedCaptionId, captions) {
        val selectedCue = captions.firstOrNull { it.id == selectedCaptionId }
        if (selectedCue != null) {
            player.seekTo(selectedCue.startMs)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(100L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!fullscreen) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                    }
                },
                update = { playerView ->
                    playerView.player = player
                },
            )
            TextButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .semantics { contentDescription = "preview_fullscreen" },
                onClick = { fullscreen = true },
            ) { Text("全屏", color = Color.White) }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("正在全屏预览", color = Color.White) }
        }
        if (currentCue != null) {
            SubtitlePreviewOverlay(
                cue = currentCue,
                style = subtitleStyle,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            BackHandler { fullscreen = false }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .semantics { contentDescription = "preview_fullscreen_dialog" },
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        update = { playerView -> playerView.player = player },
                    )
                    if (currentCue != null) {
                        SubtitlePreviewOverlay(
                            cue = currentCue,
                            style = subtitleStyle,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("全屏预览", color = Color.White, fontWeight = FontWeight.Bold)
                        TextButton(
                            modifier = Modifier.semantics { contentDescription = "preview_exit" },
                            onClick = { fullscreen = false },
                        ) {
                            Text("退出", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitlePreviewOverlay(
    cue: CaptionCue,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val shadow = Shadow(
        color = parseComposeColor(style.outlineColorHex, Color.Black),
        offset = Offset(0f, 2f),
        blurRadius = 4f,
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.94f)
                .padding(bottom = maxHeight * (style.bottomMarginPercent.coerceIn(4, 28) / 100f))
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cue.english.isNotBlank()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = cue.english,
                    color = parseComposeColor(style.primaryColorHex, Color.White),
                    fontSize = style.fontSizeSp.coerceIn(14, 48).sp,
                    fontFamily = subtitleFontFamily(style.fontFamily),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                )
            }
            if (cue.chinese.isNotBlank()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = cue.chinese,
                    color = parseComposeColor(style.secondaryColorHex, Color(0xFFF4E7A1)),
                    fontSize = (style.fontSizeSp.coerceIn(14, 48) * 0.82f).sp,
                    fontFamily = subtitleFontFamily(style.fontFamily),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(shadow = shadow),
                )
            }
        }
    }
}

private fun subtitleFontFamily(fontFamily: String): FontFamily = when (fontFamily) {
    SUBTITLE_FONT_SERIF -> FontFamily.Serif
    SUBTITLE_FONT_MONO -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

private fun parseComposeColor(value: String, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(value)) }
        .getOrDefault(fallback)
}

@Composable
private fun CaptionList(
    captions: List<CaptionCue>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onEnglishChanged: (String, String) -> Unit,
    onChineseChanged: (String, String) -> Unit,
    onApplyCandidate: (String, String) -> Unit,
    onShiftStart: (String, Long) -> Unit,
    onShiftEnd: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onConfirm: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (captions.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionCard(
    cue: CaptionCue,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onEnglishChanged: (String) -> Unit,
    onChineseChanged: (String) -> Unit,
    onApplyCandidate: (String) -> Unit,
    onShiftStart: (Long) -> Unit,
    onShiftEnd: (Long) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
) {
    val containerColor = when {
        cue.confirmed -> Color(0xFF1C3328)
        cue.needsReview -> Color(0xFF3A3020)
        selected -> Color(0xFF253A28)
        else -> Color(0xFF1B1F25)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
private fun TimingControl(
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
