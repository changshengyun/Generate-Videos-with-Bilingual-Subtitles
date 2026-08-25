package com.example.lyriccaptioner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize as Media3VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.captions.CaptionTimeline
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionBasicStylePreset
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionCueSplitPolicy
import com.example.lyriccaptioner.model.CaptionReadability
import com.example.lyriccaptioner.model.CaptionReadabilityIssue
import com.example.lyriccaptioner.model.CaptionGeometryResolver
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionVerticalAnchor
import com.example.lyriccaptioner.model.CaptionWorkflowStage
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.ResolvedCaptionStyle
import com.example.lyriccaptioner.model.canonicalCaptionFontSizeRatio
import com.example.lyriccaptioner.model.movedToDirectEditPosition
import com.example.lyriccaptioner.model.resolveCaptionStyle
import com.example.lyriccaptioner.model.withDirectEditWidth
import com.example.lyriccaptioner.model.verticalAnchor
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.VideoImportMode
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.SourceVideoSize
import com.example.lyriccaptioner.model.resolveCaptionLayout
import com.example.lyriccaptioner.processing.TranslationModelState
import com.example.lyriccaptioner.processing.CaptionRenderResolver
import com.example.lyriccaptioner.processing.CaptionPaintPlan
import com.example.lyriccaptioner.processing.CaptionRenderSpec
import com.example.lyriccaptioner.processing.ResolvedCaptionRender
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiModel
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private fun uniqueDocumentName(requestedName: String): String {
    val extensionStart = requestedName.lastIndexOf('.')
    val base = if (extensionStart > 0) requestedName.substring(0, extensionStart) else requestedName
    val extension = if (extensionStart > 0) requestedName.substring(extensionStart) else ""
    return "$base-${System.currentTimeMillis()}$extension"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val deepSeekKeyUi by viewModel.deepSeekKeyUi.collectAsState()
    val context = LocalContext.current
    val editorSnapshot = buildEditorSnapshot(state)
    var videoImportMode by remember { mutableStateOf(VideoImportMode.NEW_VIDEO) }
    var activeSection by remember { mutableStateOf(EditorSection.IMPORT.index) }
    var playbackPositionMs by remember(state.videoUri) { mutableLongStateOf(0L) }
    LaunchedEffect(state.captionWorkflowStage) {
        if (state.captionWorkflowStage == CaptionWorkflowStage.READY_FOR_EDIT) {
            activeSection = EditorSection.CAPTIONS.index
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importVideo(uri, videoImportMode)
        }
    }
    val legacyWritePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.exportVideo() }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importSrt(uri)
        }
    }
    val lyricPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.importLyricText(uri)
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
    val projectCreator = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.saveProjectArchive(uri)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0F12),
        contentColor = Color(0xFFF4F5F7),
    ) {
        if (activeSection == EditorSection.CAPTIONS.index) {
            CaptionEditorPage(
                state = state,
                editorSnapshot = editorSnapshot,
                playbackPositionMs = playbackPositionMs,
                onPlaybackPositionChanged = { playbackPositionMs = it },
                onImportLyrics = { lyricPicker.launch("text/*") },
                onSectionSelected = { activeSection = it },
                viewModel = viewModel,
            )
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.72f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeSection != EditorSection.CAPTIONS.index) {
                    DeepSeekKeySettingsPanel(
                        model = deepSeekKeyUi,
                        onSave = viewModel::saveDeepSeekKey,
                        onReplace = viewModel::replaceDeepSeekKey,
                        onTestConnection = viewModel::testDeepSeekConnection,
                        onDelete = viewModel::deleteDeepSeekKey,
                        onCancelInput = viewModel::cancelDeepSeekKeyInput,
                    )
                }
                VideoPreview(
                    videoUri = state.videoUri.takeUnless { state.mediaState == MediaState.UNAVAILABLE },
                    captions = state.captions,
                    selectedCaptionId = state.selectedCaptionId,
                    captionLayout = state.captionLayout,
                    defaultCaptionStyle = state.defaultCaptionStyle,
                    status = state.status,
                    isWorking = state.isWorking,
                    exportState = state.exportState,
                    mediaRevision = state.mediaRevision,
                    directEditMode = activeSection == EditorSection.CAPTIONS.index,
                    onSelectCue = viewModel::selectCue,
                    onDeleteCue = viewModel::deleteCaption,
                    onPositionCommitted = viewModel::updateCueDirectPosition,
                    onWidthCommitted = viewModel::updateCueDirectWidth,
                    onFontSizeCommitted = viewModel::updateCueDirectFontSize,
                    onPlaybackPositionChanged = { playbackPositionMs = it },
                )
                WorkbenchTabs(activeSection = activeSection, onSectionSelected = { activeSection = it })
                when (activeSection) {
                    0 -> WorkflowPanel(title = "导入", subtitle = "视频、项目和字幕文件") {
                        ActionRow {
                            ActionButton(
                                icon = "＋",
                                label = if (state.requiresVideoAssociation) "重新绑定视频" else "导入视频",
                                enabled = !state.isWorking,
                                primary = true,
                                accessibilityId = "import_video",
                                onClick = {
                                    videoImportMode = if (state.requiresVideoAssociation) VideoImportMode.RELINK else VideoImportMode.NEW_VIDEO
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                    )
                                },
                            )
                            ActionButton(
                                icon = "▣",
                                label = "打开项目",
                                enabled = !state.isWorking,
                                accessibilityId = "open_project",
                                onClick = { projectPicker.launch(arrayOf("application/octet-stream", "text/plain")) },
                            )
                        }
                        ActionRow {
                            SecondaryAction("字幕 SRT", !state.isWorking, accessibilityId = "import_srt") { srtPicker.launch("*/*") }
                            SecondaryAction("Whisper 模型", !state.isWorking) { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }
                        }
                    }
                    1 -> WorkflowPanel(title = "生成完整字幕", subtitle = "本地识别后自动执行 AI 增强") {
                        ActionRow {
                            ActionButton(
                                icon = "▶",
                                label = if (state.modelState.speechMode == SpeechMode.LOCAL) "开始识别" else "识别不可用",
                                enabled = state.videoUri != null && !state.isWorking && state.modelState.speechMode == SpeechMode.LOCAL,
                                primary = true,
                                accessibilityId = "generate_captions",
                                onClick = viewModel::generateCompleteCaptions,
                            )
                        }
                        if (state.asrRunning || state.enhancementRunning) {
                            ActionRow {
                                SecondaryAction("取消当前任务", true, onClick = viewModel::cancelCaptionWorkflow)
                            }
                        }
                    }
                    2 -> WorkflowPanel(title = "字幕编辑", subtitle = "在视频画面内直接调整字幕") {
                        ActionRow {
                            SecondaryAction("添加字幕", state.videoUri != null && !state.isWorking) {
                                viewModel.addCaptionAt(playbackPositionMs)
                            }
                            SecondaryAction("导入歌词", state.captions.isNotEmpty() && !state.isWorking) { lyricPicker.launch("text/*") }
                        }
                        Text(
                            text = "点击画面中的字幕可移动、删除、拉伸宽度或缩放字号",
                            color = Color(0xFF9EA5B1),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> WorkflowPanel(title = "导出", subtitle = "保存项目、导出视频或分享成品") {
                        ActionRow {
                            ActionButton(
                                icon = "⇩",
                                label = "导出视频",
                                enabled = state.videoUri != null && state.captions.isNotEmpty() && !state.isWorking,
                                primary = true,
                                accessibilityId = "export_video",
                                onClick = {
                                    val needsPermission = Build.VERSION.SDK_INT in 26..28 &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) {
                                        legacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    } else {
                                        viewModel.exportVideo()
                                    }
                                },
                            )
                            ActionButton(
                                icon = "↗",
                                label = "分享视频",
                                enabled = state.exportUri != null && !state.isWorking,
                                accessibilityId = "share_video",
                                onClick = { state.exportUri?.let { shareExportedVideo(context, it) } },
                            )
                        }
                        ActionRow {
                            SecondaryAction("保存项目", state.captions.isNotEmpty() && !state.isWorking, accessibilityId = "save_project") {
                                projectCreator.launch(uniqueDocumentName("lyric-captioner-project.lcp"))
                            }
                            if (state.exportState == ExportState.RUNNING) {
                                SecondaryAction("取消导出", true, onClick = viewModel::cancelExport)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionEditorPage(
    state: EditorState,
    editorSnapshot: String,
    playbackPositionMs: Long,
    onPlaybackPositionChanged: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onSectionSelected: (Int) -> Unit,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val playableUri = state.videoUri.takeUnless {
        state.mediaState == MediaState.UNAVAILABLE || state.isWorking
    }
    val sharedPlayer = remember(playableUri) {
        playableUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }
    }
    val selectionSeekGuard = remember(playableUri) { SelectionSeekGuard() }
    DisposableEffect(sharedPlayer) {
        onDispose { sharedPlayer?.release() }
    }
    val orderedCaptions = remember(state.captions) {
        state.captions.sortedWith(compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            VideoPreview(
                videoUri = state.videoUri.takeUnless { state.mediaState == MediaState.UNAVAILABLE },
                captions = orderedCaptions,
                selectedCaptionId = state.selectedCaptionId,
                captionLayout = state.captionLayout,
                defaultCaptionStyle = state.defaultCaptionStyle,
                status = state.status,
                isWorking = state.isWorking,
                exportState = state.exportState,
                mediaRevision = state.mediaRevision,
                directEditMode = true,
                onSelectCue = viewModel::selectCue,
                onDeleteCue = viewModel::deleteCaption,
                onPositionCommitted = viewModel::updateCueDirectPosition,
                onWidthCommitted = viewModel::updateCueDirectWidth,
                onFontSizeCommitted = viewModel::updateCueDirectFontSize,
                onPlaybackPositionChanged = onPlaybackPositionChanged,
                sharedPlayer = sharedPlayer,
                selectionSeekGuard = selectionSeekGuard,
            )
        }
        item {
            WorkbenchTabs(
                activeSection = EditorSection.CAPTIONS.index,
                onSectionSelected = onSectionSelected,
            )
        }
        item {
            WorkflowPanel(title = "字幕编辑", subtitle = "按歌曲时序直接修改全部双语字幕") {
                ActionRow {
                    SecondaryAction("添加字幕", state.videoUri != null && !state.isWorking) {
                        viewModel.addCaptionAt(playbackPositionMs)
                    }
                    SecondaryAction("导入歌词", orderedCaptions.isNotEmpty() && !state.isWorking, onClick = onImportLyrics)
                }
                Text(
                    text = "点击字幕卡片直接编辑；样式设置在对应字幕下方展开。",
                    color = Color(0xFF9EA5B1),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            CaptionResultSourceBanner(state.captionProcessing.source)
        }
        item {
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
                Text("${orderedCaptions.size} 条", color = Color(0xFF9EA5B1))
            }
        }
        if (orderedCaptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "caption_list" },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
                ) {
                    Text("最终字幕将在增强完成后显示", modifier = Modifier.padding(24.dp), color = Color(0xFF9EA5B1))
                }
            }
        } else {
            items(orderedCaptions, key = { it.id }) { cue ->
                CaptionCard(
                    cue = cue,
                    selected = cue.id == state.selectedCaptionId,
                    enabled = !state.isWorking,
                    onSelect = { viewModel.selectCue(cue.id) },
                    onEnglishChanged = { viewModel.updateEnglishText(cue.id, it) },
                    onChineseChanged = { viewModel.updateChineseText(cue.id, it) },
                    onApplyCandidate = { viewModel.applyCorrectionCandidate(cue.id, it) },
                    onShiftStart = { viewModel.shiftCueStart(cue.id, it) },
                    onShiftEnd = { viewModel.shiftCueEnd(cue.id, it) },
                    onDelete = { viewModel.deleteCaption(cue.id) },
                    onConfirm = { viewModel.confirmCue(cue.id) },
                    onSplit = { firstEnglish, firstChinese, secondEnglish, secondChinese ->
                        viewModel.splitCaption(cue.id, firstEnglish, firstChinese, secondEnglish, secondChinese)
                    },
                    defaultStyle = state.defaultCaptionStyle,
                    captionLayout = state.captionLayout,
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
                    onClearOverride = { viewModel.clearCueStyleOverride(cue.id) },
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CaptionResultSourceBanner(source: CaptionResultSource) {
    val (text, color) = when (source) {
        CaptionResultSource.CLOUD_AI -> "当前成品：DeepSeek 增强字幕" to Color(0xFFB7F36B)
        CaptionResultSource.LOCAL_FALLBACK -> "当前成品：本地回退；英文未经过标准歌词校正" to Color(0xFFFFC857)
        CaptionResultSource.RAW_ASR -> "尚无最终增强字幕" to Color(0xFF9EA5B1)
    }
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "caption_result_source:$source" },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DeepSeekKeySettingsPanel(
    model: DeepSeekKeyUiModel,
    onSave: (String) -> Unit,
    onReplace: (String) -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit,
    onCancelInput: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    val hasExistingKey = model.maskedKey != null
    val showReplace = model.showReplace || (hasExistingKey && model.state == DeepSeekKeyState.VALIDATION_FAILED)
    val showDelete = model.showDelete || (hasExistingKey && model.state == DeepSeekKeyState.VALIDATION_FAILED)
    val showSave = model.showSave && !showReplace
    val operationInProgress = model.state == DeepSeekKeyState.VALIDATING_NEW_KEY ||
        model.state == DeepSeekKeyState.TESTING_CONNECTION
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282D35)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AI 服务配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Provider: ${model.provider}", style = MaterialTheme.typography.bodySmall)
                    Text("状态：${deepSeekKeyStatusLabel(model)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9EA5B1))
                }
                TextButton(onClick = {
                    expanded = !expanded
                    if (!expanded) apiKeyInput = ""
                }) {
                    Text(if (expanded) "收起" else "配置")
                }
            }
            if (model.maskedKey != null) {
                Text(
                    text = "API Key：${model.maskedKey}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { contentDescription = "deepseek_key_masked" },
                )
            }
            if (model.detail != null) {
                Text(
                    text = model.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9EA5B1),
                    modifier = Modifier.semantics { contentDescription = "deepseek_key_detail" },
                )
            }
            if (expanded) {
                Text("Base URL：${model.baseUrl}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9EA5B1))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "deepseek_api_key_input" },
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("DeepSeek API Key") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showSave) {
                        Button(
                            modifier = Modifier.weight(1f).semantics { contentDescription = "deepseek_key_save" },
                            enabled = apiKeyInput.isNotBlank() && !operationInProgress,
                            onClick = {
                                val key = apiKeyInput
                                apiKeyInput = ""
                                onSave(key)
                            },
                        ) { Text("保存并验证") }
                    }
                    if (showReplace) {
                        Button(
                            modifier = Modifier.weight(1f).semantics { contentDescription = "deepseek_key_replace" },
                            enabled = apiKeyInput.isNotBlank() && !operationInProgress,
                            onClick = {
                                val key = apiKeyInput
                                apiKeyInput = ""
                                onReplace(key)
                            },
                        ) { Text("更换 API Key") }
                    }
                }
                if (model.showTestConnection) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "deepseek_key_test_connection" },
                        enabled = !operationInProgress,
                        onClick = onTestConnection,
                    ) { Text("测试连接") }
                }
                if (showDelete) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "deepseek_key_delete" },
                        onClick = {
                            apiKeyInput = ""
                            onDelete()
                        },
                    ) { Text("删除 API Key") }
                }
                if (model.showCancel) {
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "deepseek_key_cancel" },
                        onClick = {
                            apiKeyInput = ""
                            onCancelInput()
                        },
                    ) { Text("取消") }
                }
            }
        }
    }
}

private fun deepSeekKeyStatusLabel(model: DeepSeekKeyUiModel): String = when (model.state) {
    DeepSeekKeyState.UNCONFIGURED -> "未配置"
    DeepSeekKeyState.INPUT_NEW_KEY -> "请输入新 Key"
    DeepSeekKeyState.VALIDATING_NEW_KEY -> "验证中…"
    DeepSeekKeyState.TESTING_CONNECTION -> "正在测试连接…"
    DeepSeekKeyState.CONFIGURED -> "已配置"
    DeepSeekKeyState.VALIDATION_FAILED -> "验证失败，旧 Key 保持不变"
    DeepSeekKeyState.NEEDS_REENTRY -> "需要重新输入"
}

@Composable
private fun WorkbenchTabs(
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
private fun RuntimeStatusStrip(
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
private fun RuntimeStatusChip(
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
private fun DefaultCaptionStyleControls(
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
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
                TextButton(onClick = onToggleBold) { Text(if (style.bold) "鍙栨秷绮椾綋" else "绮椾綋") }
                TextButton(onClick = onToggleItalic) { Text(if (style.italic) "鍙栨秷鏂滀綋" else "鏂滀綋") }
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
private fun CueStyleControls(
    cue: CaptionCue,
    defaultStyle: DefaultCaptionStyle,
    captionLayout: CaptionLayout,
    enabled: Boolean,
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
    val uiState = captionStyleUiState(defaultStyle, cue)
    val style = uiState.resolved
    val resolvedLayout = resolveCaptionLayout(captionLayout, cue.layoutOverride)
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
            text = if (uiState.hasOverride) "已覆盖，未设置字段继承项目默认" else "继承项目默认样式",
            color = Color(0xFF9EA5B1),
            style = MaterialTheme.typography.labelMedium,
        )
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
        SubtitleColorPalette("英文", style.primaryColorHex, enabled, onEnglishColorChanged)
        SubtitleColorPalette("中文", style.secondaryColorHex, enabled, onChineseColorChanged)
        SubtitleColorPalette("描边", style.outlineColorHex, enabled, onOutlineColorChanged)
        OutlinedButton(
            modifier = Modifier.semantics { contentDescription = "clear_cue_style_override:${cue.id}" },
            enabled = enabled && uiState.hasOverride,
            onClick = onClearOverride,
        ) {
            Text("清除单条覆盖")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedCueStyleControls(
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12151A)),
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
private fun fontFamilyLabel(fontFamily: String): String = when (fontFamily) {
    SUBTITLE_FONT_SERIF -> "衬线"
    SUBTITLE_FONT_MONO -> "等宽"
    else -> "无衬线"
}

private fun alignmentLabel(alignment: CaptionAlignment): String = when (alignment) {
    CaptionAlignment.LEFT -> "左"
    CaptionAlignment.CENTER -> "中"
    CaptionAlignment.RIGHT -> "右"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleColorPalette(
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

private fun shareExportedVideo(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享双语视频"))
}

private fun buildEditorSnapshot(state: EditorState): String = buildString {
    append("video=").append(if (state.videoUri == null) "none" else "present")
    append(";duration=").append(state.videoDurationMs)
    append(";media=").append(state.mediaState)
    append(";requiresAssociation=").append(state.requiresVideoAssociation)
    append(";export=").append(if (state.exportUri == null) "none" else "present")
    append(";style=").append(state.exportProfile.subtitleStyle)
    append(";layout=").append(state.captionLayout)
    append(";defaultStyle=").append(state.defaultCaptionStyle)
    append(";caption_count=").append(state.captions.size)
    append(";captions=")
    state.captions.forEach { cue ->
        append(cue.id).append(',')
            .append(cue.startMs).append(',')
            .append(cue.endMs).append(',')
            .append(cue.english).append(',')
            .append(cue.chinese).append(',')
            .append(cue.confirmed).append('|')
    }
}


private fun localizeStatus(status: String): String {
    return when {
        status.isBlank() -> "等待操作"
        status.startsWith("Import a video") -> "导入 5 分钟以内的视频开始编辑"
        status.startsWith("Checking video access") -> "正在检查视频访问权限…"
        status.startsWith("Video imported with persistent") -> "视频已导入：已保留持久访问权限"
        status.startsWith("Video imported for this session only") -> "视频已导入：仅本次会话可用"
        status.startsWith("Video imported") -> "视频已导入，可继续识别或编辑"
        status.startsWith("Video re-associated and persisted") -> "视频已重新绑定：已保留持久访问权限"
        status.startsWith("Video re-associated") -> "视频已重新绑定"
        status.startsWith("Could not import video") -> "视频导入失败：${status.substringAfter(": ", "未知原因")}"
        status.startsWith("Preparing") -> "正在准备本地翻译模型…"
        status.startsWith("Translated") -> "中文翻译完成：${status.substringAfter("Translated ").substringBefore(" captions") } 条"
        status.startsWith("Translation") -> "翻译状态：${status.substringAfter(": ", status)}"
        status.startsWith("Created") -> "字幕已生成：${status.substringAfter("Created ").substringBefore(" lyric captions")} 条"
        status.startsWith("Rendering") -> "正在渲染双语字幕…"
        status.startsWith("Export saved") -> "视频导出完成"
        status.startsWith("Video export") -> "导出状态：${status.substringAfter(": ", status)}"
        status.startsWith("ASR") -> "识别状态：${status.substringAfter(": ", status)}"
        status.startsWith("Project restored; video access is session-only") -> "项目已恢复：视频仅本次会话可用"
        status.startsWith("Project restored with persistent") -> "项目已恢复：视频持久访问有效"
        status.startsWith("Project restored; video is unavailable") -> "项目已恢复：视频不可用，请重新绑定视频"
        status.startsWith("Project restored without a video") -> "项目已恢复：没有视频，请先绑定视频"
        status.startsWith("Project restored") -> "项目已恢复：${status.substringAfter(": ", status)}"
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
    captionLayout: CaptionLayout,
    defaultCaptionStyle: DefaultCaptionStyle,
    status: String,
    isWorking: Boolean,
    exportState: ExportState,
    mediaRevision: Long,
    directEditMode: Boolean,
    onSelectCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onPositionCommitted: (String, Float, Float) -> Unit,
    onWidthCommitted: (String, Float) -> Unit,
    onFontSizeCommitted: (String, Float) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
    sharedPlayer: ExoPlayer? = null,
    selectionSeekGuard: SelectionSeekGuard? = null,
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
                    .height(if (directEditMode) 360.dp else 220.dp)
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
                        captionLayout = captionLayout,
                        defaultCaptionStyle = defaultCaptionStyle,
                        directEditMode = directEditMode,
                        onSelectCue = onSelectCue,
                        onDeleteCue = onDeleteCue,
                        onPositionCommitted = onPositionCommitted,
                        onWidthCommitted = onWidthCommitted,
                        onFontSizeCommitted = onFontSizeCommitted,
                        onPlaybackPositionChanged = onPlaybackPositionChanged,
                        sharedPlayer = sharedPlayer,
                        selectionSeekGuard = selectionSeekGuard,
                    )
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .clearAndSetSemantics {
                                contentDescription = "video_media_revision_$mediaRevision"
                            },
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
                    modifier = Modifier.semantics {
                        if (exportState == ExportState.SUCCEEDED) {
                            contentDescription = status
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD5DAE3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (exportState == ExportState.SUCCEEDED) {
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .clearAndSetSemantics {
                                contentDescription = "export_complete"
                            },
                    )
                }
            }
        }
    }
}

private class SelectionSeekGuard(var captionId: String? = null)

@Composable
private fun VideoPlayer(
    uri: Uri,
    captions: List<CaptionCue>,
    selectedCaptionId: String?,
    captionLayout: CaptionLayout,
    defaultCaptionStyle: DefaultCaptionStyle,
    directEditMode: Boolean,
    onSelectCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onPositionCommitted: (String, Float, Float) -> Unit,
    onWidthCommitted: (String, Float) -> Unit,
    onFontSizeCommitted: (String, Float) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
    sharedPlayer: ExoPlayer?,
    selectionSeekGuard: SelectionSeekGuard?,
) {
    val context = LocalContext.current
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var fullscreen by remember(uri) { mutableStateOf(false) }
    var sourceVideoSize by remember(uri) { mutableStateOf<SourceVideoSize?>(null) }
    var visibleSelectionId by remember(uri) { mutableStateOf(selectedCaptionId) }
    val timeline = remember(captions) { CaptionTimeline(captions) }
    val currentCue = timeline.cueAt(positionMs)
    val currentRender = currentCue?.let { CaptionRenderResolver.resolve(it, captionLayout, defaultCaptionStyle) }
    val ownedPlayer = remember(uri, sharedPlayer) {
        if (sharedPlayer == null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        } else {
            null
        }
    }
    val player = sharedPlayer ?: requireNotNull(ownedPlayer)
    val localSelectionSeekGuard = remember(uri) { SelectionSeekGuard() }
    val effectiveSelectionSeekGuard = selectionSeekGuard ?: localSelectionSeekGuard

    DisposableEffect(player) {
        player.videoSize.takeIf { it.width > 0 && it.height > 0 }?.let { videoSize ->
            sourceVideoSize = SourceVideoSize(
                width = videoSize.width,
                height = videoSize.height,
                pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
            )
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: Media3VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    sourceVideoSize = SourceVideoSize(
                        width = videoSize.width,
                        height = videoSize.height,
                        pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
                    )
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (sharedPlayer == null) player.release()
        }
    }

    LaunchedEffect(selectedCaptionId) {
        if (effectiveSelectionSeekGuard.captionId != selectedCaptionId) {
            val selectedCue = captions.firstOrNull { it.id == selectedCaptionId }
            if (selectedCue != null) {
                player.seekTo(selectedCue.startMs)
            }
            effectiveSelectionSeekGuard.captionId = selectedCaptionId
        }
        visibleSelectionId = selectedCaptionId
    }

    LaunchedEffect(player) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            onPlaybackPositionChanged(positionMs)
            delay(100L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (!fullscreen) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    update = { playerView -> playerView.player = player },
                )
                if (directEditMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "取消字幕选择" }
                            .pointerInput(player, currentCue?.id) {
                                detectTapGestures {
                                    visibleSelectionId = null
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            },
                    )
                }
                if (currentRender != null && sourceVideoSize != null) {
                    SubtitlePreviewOverlay(
                        render = currentRender,
                        sourceVideoSize = sourceVideoSize,
                        directEditMode = directEditMode,
                        selected = visibleSelectionId == currentRender.caption.id,
                        onSelect = {
                            player.pause()
                            visibleSelectionId = currentRender.caption.id
                            onSelectCue(currentRender.caption.id)
                        },
                        onDelete = {
                            visibleSelectionId = null
                            onDeleteCue(currentRender.caption.id)
                        },
                        onPositionCommitted = onPositionCommitted,
                        onWidthCommitted = onWidthCommitted,
                        onFontSizeCommitted = onFontSizeCommitted,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                TextButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "preview_fullscreen" },
                    onClick = { fullscreen = true },
                ) { Text("全屏", color = Color.White) }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("正在全屏预览", color = Color.White) }
            }
        }
        PlayerControlRow(
            player = player,
            positionMs = positionMs,
            modifier = Modifier.fillMaxWidth(),
        )
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .semantics { contentDescription = "preview_fullscreen_dialog" },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                }
                            },
                            update = { playerView -> playerView.player = player },
                        )
                        if (currentRender != null && sourceVideoSize != null) {
                            SubtitlePreviewOverlay(
                                render = currentRender,
                                sourceVideoSize = sourceVideoSize,
                                directEditMode = false,
                                selected = false,
                                onSelect = {},
                                onDelete = {},
                                onPositionCommitted = { _, _, _ -> },
                                onWidthCommitted = { _, _ -> },
                                onFontSizeCommitted = { _, _ -> },
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
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "preview_exit" },
                                onClick = { fullscreen = false },
                            ) {
                                Text("退出", color = Color.White)
                            }
                        }
                    }
                    PlayerControlRow(
                        player = player,
                        positionMs = positionMs,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlRow(
    player: Player,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val durationMs = player.duration.takeIf { it > 0L } ?: 0L
    val boundedPositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    Row(
        modifier = modifier
            .background(Color(0xFF111318))
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = if (player.isPlaying) "暂停预览" else "播放预览" },
            onClick = { if (player.isPlaying) player.pause() else player.play() },
        ) {
            Text(if (player.isPlaying) "Ⅱ" else "▶", color = Color.White)
        }
        Text(
            text = formatPlaybackTime(boundedPositionMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
        Slider(
            value = if (durationMs > 0L) boundedPositionMs.toFloat() else 0f,
            onValueChange = { value -> if (durationMs > 0L) player.seekTo(value.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0L,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "预览进度条" },
        )
        Text(
            text = formatPlaybackTime(durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SubtitlePreviewOverlay(
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

private val DirectEditTouchTarget = 48.dp

@Composable
private fun DirectEditDeleteHandle(
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
private fun DirectEditWidthHandle(
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
private fun DirectEditFontSizeHandle(
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
private fun CaptionOutlinedText(
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

private fun CaptionAlignment.toTextAlign(): TextAlign = when (this) {
    CaptionAlignment.LEFT -> TextAlign.Start
    CaptionAlignment.CENTER -> TextAlign.Center
    CaptionAlignment.RIGHT -> TextAlign.End
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

private enum class DirectEditPanelTab(val label: String) {
    KEYBOARD("键盘"),
    STYLE("样式"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectCaptionEditPanel(
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
private fun DirectStyleGroupTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

private val directBasicStyles = listOf(
    CaptionBasicStylePreset.PLAIN_TEXT to "纯文字",
    CaptionBasicStylePreset.DARK_OUTLINE to "黑描边",
    CaptionBasicStylePreset.LIGHT_OUTLINE to "白描边",
    CaptionBasicStylePreset.LIGHT_BACKGROUND to "浅色底",
    CaptionBasicStylePreset.DARK_BACKGROUND to "深色底",
    CaptionBasicStylePreset.GRAY_BACKGROUND to "灰色底",
)

private val directTextColors = listOf(
    "#FFFFFF" to "白色",
    "#000000" to "黑色",
    "#EF4444" to "红色",
    "#FB923C" to "橙色",
    "#FACC15" to "黄色",
    "#6CCB5F" to "绿色",
    "#5EC9B8" to "青色",
    "#3B82F6" to "蓝色",
)

@Composable
private fun CaptionList(
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
private fun CaptionCard(
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
    onSplit: (String, String, String, String) -> Unit = { _, _, _, _ -> },
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
    var styleExpanded by rememberSaveable(cue.id) { mutableStateOf(false) }
    var splitExpanded by rememberSaveable(cue.id) { mutableStateOf(false) }
    var firstEnglish by remember(cue.id) { mutableStateOf("") }
    var firstChinese by remember(cue.id) { mutableStateOf("") }
    var secondEnglish by remember(cue.id) { mutableStateOf("") }
    var secondChinese by remember(cue.id) { mutableStateOf("") }
    val readabilityIssues = remember(cue.english, cue.chinese, cue.startMs, cue.endMs) {
        CaptionReadability.issues(cue)
    }
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
            if (readabilityIssues.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "cue_readability_warning:${cue.id}" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    readabilityIssues.forEach { issue ->
                        Text(
                            text = readabilityIssueLabel(issue),
                            color = Color(0xFFFFC857),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
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
            TextButton(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "cue_split_toggle:${cue.id}" },
                enabled = enabled,
                onClick = {
                    if (!splitExpanded) {
                        val englishParts = CaptionCueSplitPolicy.suggestTextParts(cue.english)
                        val chineseParts = CaptionCueSplitPolicy.suggestTextParts(cue.chinese)
                        firstEnglish = englishParts.first
                        secondEnglish = englishParts.second
                        firstChinese = chineseParts.first
                        secondChinese = chineseParts.second
                    }
                    splitExpanded = !splitExpanded
                },
            ) {
                Text(if (splitExpanded) "取消拆分" else "拆分字幕")
            }
            if (splitExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "cue_split_editor:${cue.id}" },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("第一段", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = firstEnglish,
                        onValueChange = { firstEnglish = it },
                        label = { Text("第一段英文") },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = firstChinese,
                        onValueChange = { firstChinese = it },
                        label = { Text("第一段中文") },
                    )
                    Text("第二段", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = secondEnglish,
                        onValueChange = { secondEnglish = it },
                        label = { Text("第二段英文") },
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = secondChinese,
                        onValueChange = { secondChinese = it },
                        label = { Text("第二段中文") },
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "cue_split_confirm:${cue.id}" },
                        enabled = enabled && listOf(firstEnglish, firstChinese, secondEnglish, secondChinese).all { it.isNotBlank() },
                        onClick = {
                            onSplit(firstEnglish, firstChinese, secondEnglish, secondChinese)
                            splitExpanded = false
                        },
                    ) {
                        Text("确认拆分为两个 cue")
                    }
                }
            }
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

private fun readabilityIssueLabel(issue: CaptionReadabilityIssue): String = when (issue) {
    CaptionReadabilityIssue.ENGLISH_LINE_TOO_LONG -> "英文超过 42 字符"
    CaptionReadabilityIssue.CHINESE_LINE_TOO_LONG -> "中文超过 16 字"
    CaptionReadabilityIssue.ENGLISH_READING_TOO_FAST -> "英文阅读速度过快"
    CaptionReadabilityIssue.CHINESE_READING_TOO_FAST -> "中文阅读速度过快"
    CaptionReadabilityIssue.DURATION_TOO_SHORT -> "时长少于 0.833 秒"
    CaptionReadabilityIssue.DURATION_TOO_LONG -> "时长超过 7 秒"
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
