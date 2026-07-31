package com.example.lyriccaptioner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.captions.CaptionTimeline
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.SubtitleStyle
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
            title = { Text("Paste English lyrics") },
            text = {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = pastedLyrics,
                    onValueChange = { pastedLyrics = it },
                    label = { Text("One line per caption") },
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
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteLyrics = false }) { Text("Cancel") }
            },
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                detail = state.modelState.speechRuntimeDetail,
            )
                TranslationRuntimeStatus(state.modelState.translationModelState)
                FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    enabled = !state.isWorking,
                    onClick = {
                        videoImportMode = if (state.mediaState == MediaState.UNAVAILABLE) {
                            VideoImportMode.RELINK
                        } else {
                            VideoImportMode.NEW_VIDEO
                        }
                        videoPicker.launch(arrayOf("video/*"))
                    },
                ) {
                    Text(if (state.mediaState == com.example.lyriccaptioner.model.MediaState.UNAVAILABLE) "Relink Video" else "Import")
                }
                TextButton(
                    enabled = !state.isWorking,
                    onClick = { srtPicker.launch("*/*") },
                ) {
                    Text("Import SRT")
                }
                TextButton(
                    enabled = !state.isWorking,
                    onClick = { projectPicker.launch(arrayOf("application/octet-stream", "text/plain")) },
                ) {
                    Text("Open Project")
                }
                TextButton(
                    enabled = !state.isWorking,
                    onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                ) {
                    Text("Whisper Model")
                }
                TextButton(
                    enabled = state.captions.isNotEmpty() && !state.isWorking,
                    onClick = { lyricPicker.launch("text/*") },
                ) {
                    Text("Lyrics")
                }
                TextButton(
                    enabled = state.videoUri != null && !state.isWorking,
                    onClick = { showPasteLyrics = true },
                ) {
                    Text("Paste Lyrics")
                }
                TextButton(
                    enabled = state.captions.any { it.english.isNotBlank() && it.chinese.isBlank() } && !state.isWorking,
                    onClick = viewModel::translateMissingChinese,
                ) {
                    Text("Translate")
                }
                if (state.translationRunning) {
                    TextButton(onClick = viewModel::cancelTranslation) {
                        Text("Cancel Translation")
                    }
                }
                TextButton(
                    enabled = state.videoUri != null && !state.isWorking,
                    onClick = viewModel::addCaption,
                ) {
                    Text("Add Caption")
                }
                Button(
                    enabled = state.videoUri != null && !state.isWorking && state.modelState.speechMode == SpeechMode.LOCAL,
                    onClick = viewModel::generateCaptions,
                ) {
                    Text(
                        if (state.modelState.speechMode == SpeechMode.LOCAL) {
                            "Generate Local"
                        } else {
                            "ASR Unavailable"
                        },
                    )
                }
                if (state.asrRunning) {
                    TextButton(onClick = viewModel::cancelGenerateCaptions) {
                        Text("Cancel ASR")
                    }
                }
                Button(
                    enabled = state.videoUri != null && state.captions.isNotEmpty() && !state.isWorking,
                    onClick = {
                        videoCreator.launch(state.exportProfile.outputName)
                    },
                ) {
                    Text("Export")
                }
                TextButton(
                    enabled = state.isWorking && !state.translationRunning && !state.asrRunning,
                    onClick = viewModel::cancelExport,
                ) {
                    Text("Cancel")
                }
                TextButton(
                    enabled = state.exportUri != null && !state.isWorking,
                    onClick = {
                        state.exportUri?.let { shareExportedVideo(context, it) }
                    },
                ) {
                    Text("Share")
                }
                TextButton(
                    enabled = state.captions.isNotEmpty() && !state.isWorking,
                    onClick = viewModel::exportSidecarSrt,
                ) {
                    Text("SRT")
                }
                TextButton(
                    enabled = state.captions.isNotEmpty() && !state.isWorking,
                    onClick = { projectCreator.launch("lyric-captioner-project.lcp") },
                ) {
                    Text("Save Project")
                }
            }
                SubtitleStyleControls(
                fontSizeSp = state.exportProfile.subtitleStyle.fontSizeSp,
                bottomMarginPercent = state.exportProfile.subtitleStyle.bottomMarginPercent,
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
            )
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
                modifier = Modifier.weight(0.45f),
            )
        }
    }
}

@Composable
private fun TranslationRuntimeStatus(state: TranslationModelState) {
    Text(
        text = "Translation model: ${state.name}",
        style = MaterialTheme.typography.labelMedium,
        color = when (state) {
            TranslationModelState.READY -> Color(0xFF176B3A)
            TranslationModelState.PREPARING -> MaterialTheme.colorScheme.primary
            TranslationModelState.NEEDS_INSTALL, TranslationModelState.FAILED ->
                MaterialTheme.colorScheme.error
        },
    )
}

@Composable
private fun SpeechRuntimeStatus(
    modelInstalled: Boolean,
    nativeReady: Boolean,
    mode: SpeechMode,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ASR: ${mode.name}",
            style = MaterialTheme.typography.labelMedium,
            color = if (mode == SpeechMode.LOCAL) {
                Color(0xFF176B3A)
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = if (modelInstalled) "Model: ready" else "Model: missing",
            style = MaterialTheme.typography.labelMedium,
            color = if (modelInstalled) Color(0xFF176B3A) else MaterialTheme.colorScheme.error,
        )
        Text(
            text = if (nativeReady) "JNI: ready" else "JNI: missing",
            style = MaterialTheme.typography.labelMedium,
            color = if (nativeReady) Color(0xFF176B3A) else MaterialTheme.colorScheme.error,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleStyleControls(
    fontSizeSp: Int,
    bottomMarginPercent: Int,
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
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Text("Subtitle style", fontWeight = FontWeight.SemiBold)
                Text("Font ${fontSizeSp}sp / Bottom ${bottomMarginPercent}%")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = onFontSmaller) { Text("A-") }
                TextButton(onClick = onFontLarger) { Text("A+") }
                TextButton(onClick = onMarginLower) { Text("Lower") }
                TextButton(onClick = onMarginHigher) { Text("Higher") }
            }
            SubtitleColorPalette(
                label = "English",
                selectedColorHex = primaryColorHex,
                onColorSelected = onEnglishColorChanged,
            )
            SubtitleColorPalette(
                label = "Chinese",
                selectedColorHex = secondaryColorHex,
                onColorSelected = onChineseColorChanged,
            )
            SubtitleColorPalette(
                label = "Outline",
                selectedColorHex = outlineColorHex,
                onColorSelected = onOutlineColorChanged,
            )
        }
    }
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
    context.startActivity(Intent.createChooser(shareIntent, "Share captioned video"))
}


@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "LyricCaptioner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Local-first bilingual subtitle editor for short videos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF171A1F)),
                contentAlignment = Alignment.Center,
            ) {
                if (videoUri == null) {
                    Text(text = "No video selected", color = Color.White)
                } else if (isWorking) {
                    // Removing PlayerView releases its decoder and Surface before Media3 starts exporting.
                    Text(text = "Preview paused while processing", color = Color.White)
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
                Text(text = status, style = MaterialTheme.typography.bodyMedium)
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
        if (currentCue != null) {
            SubtitlePreviewOverlay(
                cue = currentCue,
                style = subtitleStyle,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SubtitlePreviewOverlay(
    cue: CaptionCue,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val bottomPadding = (190 * style.bottomMarginPercent.coerceIn(4, 28) / 100).dp
    val shadow = Shadow(
        color = parseComposeColor(style.outlineColorHex, Color.Black),
        offset = Offset(0f, 2f),
        blurRadius = 4f,
    )

    Column(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .padding(bottom = bottomPadding)
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
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(shadow = shadow),
            )
        }
    }
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
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Generated bilingual subtitles will appear here.")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
        cue.confirmed -> Color(0xFFEAF7EF)
        cue.needsReview -> Color(0xFFFFF4D8)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
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
                    text = "Confidence ${(cue.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TimingControl(
                    label = "Start",
                    enabled = enabled,
                    onEarlier = { onShiftStart(-100L) },
                    onLater = { onShiftStart(100L) },
                )
                TimingControl(
                    label = "End",
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
                label = { Text("English") },
                singleLine = false,
            )
            if (cue.correctionCandidates.isNotEmpty()) {
                Text(
                    text = "Correction candidates",
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
                label = { Text("Chinese") },
                singleLine = false,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = when {
                        cue.confirmed -> "Confirmed"
                        cue.needsReview -> "Review suggested"
                        else -> "Needs confirmation"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(
                    enabled = enabled && cue.canConfirm && !cue.confirmed,
                    onClick = onConfirm,
                ) {
                    Text(if (cue.confirmed) "Confirmed" else "Confirm")
                }
                TextButton(enabled = enabled, onClick = onDelete) {
                    Text("Delete")
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
            modifier = Modifier.width(44.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(enabled = enabled, onClick = onEarlier) { Text("-0.1s") }
        TextButton(enabled = enabled, onClick = onLater) { Text("+0.1s") }
    }
}
