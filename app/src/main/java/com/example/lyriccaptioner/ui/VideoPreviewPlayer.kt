package com.example.lyriccaptioner.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.lyriccaptioner.captions.CaptionTimeline
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.SourceVideoSize
import com.example.lyriccaptioner.processing.CaptionRenderResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun VideoPreview(
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
    layoutEditLocked: Boolean = false,
    onToggleLayoutEditLocked: () -> Unit = {},
    onSelectCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onPositionCommitted: (String, Float, Float) -> Unit,
    onWidthCommitted: (String, Float) -> Unit,
    onFontSizeCommitted: (String, Float) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF171A1F),
            contentColor = Color(0xFFF4F5F7),
        ),
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
                if (directEditMode) {
                    TextButton(
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = if (layoutEditLocked) {
                                "layout_lock:locked"
                            } else {
                                "layout_lock:unlocked"
                            }
                        },
                        onClick = onToggleLayoutEditLocked,
                    ) { Text(if (layoutEditLocked) "🔒 全部布局" else "🔓 单条布局") }
                } else {
                    Text("视频预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
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
                        layoutEditLocked = layoutEditLocked,
                        onToggleLayoutEditLocked = onToggleLayoutEditLocked,
                        onSelectCue = onSelectCue,
                        onDeleteCue = onDeleteCue,
                        onPositionCommitted = onPositionCommitted,
                        onWidthCommitted = onWidthCommitted,
                        onFontSizeCommitted = onFontSizeCommitted,
                        onPlaybackPositionChanged = onPlaybackPositionChanged,
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

@Composable
internal fun VideoPlayer(
    uri: Uri,
    captions: List<CaptionCue>,
    selectedCaptionId: String?,
    captionLayout: CaptionLayout,
    defaultCaptionStyle: DefaultCaptionStyle,
    directEditMode: Boolean,
    layoutEditLocked: Boolean = false,
    onToggleLayoutEditLocked: () -> Unit = {},
    onSelectCue: (String) -> Unit,
    onDeleteCue: (String) -> Unit,
    onPositionCommitted: (String, Float, Float) -> Unit,
    onWidthCommitted: (String, Float) -> Unit,
    onFontSizeCommitted: (String, Float) -> Unit,
    onPlaybackPositionChanged: (Long) -> Unit,
) {
    val context = LocalContext.current
    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var fullscreen by remember(uri) { mutableStateOf(false) }
    var sourceVideoSize by remember(uri) { mutableStateOf<SourceVideoSize?>(null) }
    var visibleSelectionId by remember(uri) { mutableStateOf(selectedCaptionId) }
    val timeline = remember(captions) { CaptionTimeline(captions) }
    val currentCue = timeline.cueAt(positionMs)
    val currentRender = currentCue?.let { CaptionRenderResolver.resolve(it, captionLayout, defaultCaptionStyle) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(player) {
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
            player.release()
        }
    }

    LaunchedEffect(selectedCaptionId, captions) {
        val selectedCue = captions.firstOrNull { it.id == selectedCaptionId }
        if (selectedCue != null) {
            player.seekTo(selectedCue.startMs)
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
                contentColor = Color.White,
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
                        if (directEditMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics { contentDescription = "全屏取消字幕选择" }
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
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (directEditMode) {
                                TextButton(
                                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                                        contentDescription = if (layoutEditLocked) {
                                            "fullscreen_layout_lock:locked"
                                        } else {
                                            "fullscreen_layout_lock:unlocked"
                                        }
                                    },
                                    onClick = onToggleLayoutEditLocked,
                                ) { Text(if (layoutEditLocked) "🔒 全部布局" else "🔓 单条布局", color = Color.White) }
                            } else {
                                Text("全屏预览", color = Color.White, fontWeight = FontWeight.Bold)
                            }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 56.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerControlRow(
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

internal fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
