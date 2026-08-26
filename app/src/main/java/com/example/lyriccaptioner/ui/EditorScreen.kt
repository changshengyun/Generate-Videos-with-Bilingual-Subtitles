package com.example.lyriccaptioner.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.model.CaptionWorkflowStage
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.VideoImportMode

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
        Column(
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
            if (showsCaptionList(activeSection)) {
                if (activeSection != EditorSection.CAPTIONS.index) {
                    CaptionList(
                    captions = state.captions,
                    selectedId = state.selectedCaptionId,
                    defaultStyle = state.defaultCaptionStyle,
                    captionLayout = state.captionLayout,
                    onSelect = viewModel::selectCue,
                    onEnglishChanged = viewModel::updateEnglishText,
                    onChineseChanged = viewModel::updateChineseText,
                    onApplyCandidate = viewModel::applyCorrectionCandidate,
                    onShiftStart = viewModel::shiftCueStart,
                    onShiftEnd = viewModel::shiftCueEnd,
                    onDelete = viewModel::deleteCaption,
                    onConfirm = viewModel::confirmCue,
                    onFontSmaller = { cueId, delta -> viewModel.updateCueFontSize(cueId, delta) },
                    onFontLarger = { cueId, delta -> viewModel.updateCueFontSize(cueId, delta) },
                    onEnglishColorChanged = viewModel::updateCueEnglishColor,
                    onChineseColorChanged = viewModel::updateCueChineseColor,
                    onOutlineColorChanged = viewModel::updateCueOutlineColor,
                    onFontFamilyChanged = viewModel::updateCueFontFamily,
                    onToggleBold = { cueId -> viewModel.toggleCueBold(cueId) },
                    onToggleItalic = { cueId -> viewModel.toggleCueItalic(cueId) },
                    onAlignmentChanged = viewModel::updateCueAlignment,
                    onPositionChanged = viewModel::updateCuePosition,
                    onClearOverride = viewModel::clearCueStyleOverride,
                    enabled = !state.isWorking,
                    editorSnapshot = editorSnapshot,
                        modifier = Modifier.weight(0.28f),
                    )
                } else {
                    DirectCaptionEditPanel(
                        cue = state.captions.firstOrNull { it.id == state.selectedCaptionId },
                        defaultStyle = state.defaultCaptionStyle,
                        enabled = !state.isWorking,
                        onEnglishChanged = viewModel::updateEnglishText,
                        onChineseChanged = viewModel::updateChineseText,
                        onApplyBasicStyle = viewModel::applyCueBasicStyle,
                        onUnifiedColorChanged = viewModel::updateCueUnifiedTextColor,
                        onAlignmentChanged = viewModel::updateCueAlignment,
                        modifier = Modifier.weight(0.38f),
                    )
                }
            }
        }
    }
}
