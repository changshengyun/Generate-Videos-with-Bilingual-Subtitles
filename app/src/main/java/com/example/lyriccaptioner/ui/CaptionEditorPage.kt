package com.example.lyriccaptioner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lyriccaptioner.MainViewModel
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.MediaState

private const val CAPTION_EDITOR_HEADER_ITEM_COUNT = 4

@Composable
internal fun CaptionEditorPage(
    state: EditorState,
    editorSnapshot: String,
    playbackPositionMs: Long,
    onPlaybackPositionChanged: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onSectionSelected: (Int) -> Unit,
    onOpenStylePanel: (String) -> Unit,
    onOpenMerge: (String) -> Unit,
    viewModel: MainViewModel,
    bottomPadding: Dp,
) {
    val cueSuggestion by viewModel.cueSuggestion.collectAsState()
    val orderedCaptions = remember(state.captions) { orderedCaptionEditorItems(state.captions) }
    val captionListState = rememberLazyListState()
    LaunchedEffect(state.selectedCaptionId, orderedCaptions) {
        captionEditorLazyItemIndex(
            orderedCaptions = orderedCaptions,
            selectedCaptionId = state.selectedCaptionId,
            headerItemCount = CAPTION_EDITOR_HEADER_ITEM_COUNT,
        )?.let { targetIndex ->
            val alreadyVisible = captionListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!alreadyVisible) captionListState.animateScrollToItem(targetIndex)
        }
    }
    LazyColumn(
        state = captionListState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
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
                directEditMode = true,
                layoutEditLocked = state.layoutEditLocked,
                onToggleLayoutEditLocked = viewModel::toggleLayoutEditLocked,
                onSelectCue = viewModel::selectCue,
                onDeleteCue = viewModel::deleteCaption,
                onPositionCommitted = viewModel::updateCueDirectPosition,
                onWidthCommitted = viewModel::updateCueDirectWidth,
                onFontSizeCommitted = viewModel::updateCueDirectFontSize,
                onPlaybackPositionChanged = onPlaybackPositionChanged,
            )
        }
        item {
            WorkbenchTabs(activeSection = EditorSection.CAPTIONS.index, onSectionSelected = onSectionSelected)
        }
        item {
            WorkflowPanel(title = "字幕编辑", subtitle = "在视频画面内直接调整字幕") {
                ActionRow {
                    SecondaryAction("添加字幕", state.videoUri != null && !state.isWorking) {
                        viewModel.addCaptionAt(playbackPositionMs)
                    }
                    SecondaryAction("导入歌词", state.captions.isNotEmpty() && !state.isWorking) { onImportLyrics() }
                }
                Text(
                    text = "点击画面中的字幕可移动、删除、拉伸宽度或缩放字号",
                    color = Color(0xFF9EA5B1),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Text("${orderedCaptions.size} 条", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9EA5B1))
                }
            }
        }
        if (orderedCaptions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "caption_list" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171A1F)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("字幕列表将在识别或导入后显示", color = Color(0xFF9EA5B1))
                    }
                }
            }
        } else {
            itemsIndexed(orderedCaptions, key = { _, cue -> cue.id }) { index, cue ->
                CaptionCard(
                    cue = cue,
                    positionLabel = "第 ${index + 1}/${orderedCaptions.size} 段",
                    selected = cue.id == state.selectedCaptionId,
                    enabled = !state.isWorking,
                    defaultStyle = state.defaultCaptionStyle,
                    captionLayout = state.captionLayout,
                    onSelect = { viewModel.selectCue(cue.id) },
                    onEnglishChanged = { viewModel.updateEnglishText(cue.id, it) },
                    onChineseChanged = { viewModel.updateChineseText(cue.id, it) },
                    onApplyCandidate = { viewModel.applyCorrectionCandidate(cue.id, it) },
                    onShiftStart = { viewModel.shiftCueStart(cue.id, it) },
                    onShiftEnd = { viewModel.shiftCueEnd(cue.id, it) },
                    onDelete = { viewModel.deleteCaption(cue.id) },
                    onConfirm = { viewModel.confirmCue(cue.id) },
                    onFontSmaller = { delta -> viewModel.updateCueFontSize(cue.id, delta) },
                    onFontLarger = { delta -> viewModel.updateCueFontSize(cue.id, delta) },
                    onEnglishColorChanged = { viewModel.updateCueEnglishColor(cue.id, it) },
                    onChineseColorChanged = { viewModel.updateCueChineseColor(cue.id, it) },
                    onOutlineColorChanged = { viewModel.updateCueOutlineColor(cue.id, it) },
                    onFontFamilyChanged = { viewModel.updateCueFontFamily(cue.id, it) },
                    onToggleBold = { viewModel.toggleCueBold(cue.id) },
                    onToggleItalic = { viewModel.toggleCueItalic(cue.id) },
                    onAlignmentChanged = { viewModel.updateCueAlignment(cue.id, it) },
                    onPositionChanged = { viewModel.updateCuePosition(cue.id, it) },
                    onClearOverride = { viewModel.clearCueStyleOverride(cue.id) },
                    onOpenStyle = {
                        viewModel.selectCue(cue.id)
                        onOpenStylePanel(cue.id)
                    },
                    onSplitDraft = { viewModel.splitCaptionDraft(cue.id) },
                    onMerge = { onOpenMerge(cue.id) },
                    onEnhance = { viewModel.requestCueSuggestion(cue.id) },
                    aiRunning = cueSuggestion.running && cueSuggestion.cueId == cue.id,
                    aiError = cueSuggestion.error.takeIf { cueSuggestion.cueId == cue.id },
                )
            }
        }
    }
}
