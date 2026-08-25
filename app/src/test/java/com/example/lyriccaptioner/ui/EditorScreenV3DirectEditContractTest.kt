package com.example.lyriccaptioner.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorScreenV3DirectEditContractTest {
    @Test
    fun captionEditorUsesOneMainLazyColumnInsteadOfTheDetachedPanel() {
        val source = source()
        val editor = source.substringAfter("private fun CaptionEditorPage")
            .substringBefore("private fun CaptionResultSourceBanner")

        assertTrue(editor.contains("LazyColumn("))
        assertTrue(editor.contains("items(orderedCaptions"))
        assertTrue(editor.contains("CaptionCard("))
        assertTrue(source.contains("if (activeSection == EditorSection.CAPTIONS.index)"))
        assertEquals(1, Regex("DirectCaptionEditPanel\\(").findAll(source).count())
    }

    @Test
    fun eachCueOwnsTextTimingPersistentStyleSplitMergeAndAiControls() {
        val source = source()
        val card = source.substringAfter("private fun CaptionCard")
            .substringBefore("private fun readabilityIssueLabel")
        val fixedPanel = source.substringAfter("private fun CueStylePanel")
            .substringBefore("private fun MergeCaptionDialog")

        assertTrue(card.contains("cue_style_toggle"))
        assertTrue(card.contains("onClick = onOpenStyle"))
        assertTrue(card.contains("cue_split:"))
        assertTrue(card.contains("cue_merge:"))
        assertTrue(card.contains("cue_ai_enhance:"))
        assertTrue(card.contains("英文字幕"))
        assertTrue(card.contains("中文字幕"))
        assertTrue(source.contains("cue_style_fixed_panel:"))
        assertTrue(source.contains("style_panel_collapse"))
        assertTrue(source.contains("style_panel_drag_handle"))
        assertTrue(source.contains("style_lock:locked"))
        assertTrue(source.contains("layout_lock:locked"))
        assertTrue(source.contains("fullscreen_layout_lock:locked"))
        assertTrue(source.contains("MergeCaptionDialog("))
        assertTrue(source.contains("merge_with_previous:"))
        assertTrue(source.contains("merge_with_next:"))
        assertTrue(source.contains("BackHandler(enabled = styleCueId != null) { Unit }"))
        assertTrue(source.contains("coerceIn(0.33f, 0.50f)"))
        assertTrue(source.contains("bottom = if (styleCueId != null) panelHeight + 12.dp else 8.dp"))
        assertTrue(source.contains("onToggleLayoutEditLocked = onToggleLayoutEditLocked"))
        assertTrue(source.contains("onToggleStyleLock = viewModel::toggleStyleEditLocked"))
        assertTrue(source.contains("onPrevious ="))
        assertTrue(source.contains("onNext ="))
        assertTrue(source.contains("Surface("))
        assertTrue(source.contains(".align(Alignment.BottomCenter)"))
        assertTrue(source.contains(".height(panelHeight)"))
        assertTrue(source.contains("contentPadding = PaddingValues("))
        assertTrue(source.contains("onHeightDrag(dragAmount.y)"))
        assertTrue(fixedPanel.contains(".height(48.dp)"))
        assertTrue(fixedPanel.contains(".imePadding()"))
        assertTrue(
            "IME padding must wrap the fixed panel height instead of consuming its content height",
            fixedPanel.indexOf(".imePadding()") < fixedPanel.indexOf(".height(panelHeight)"),
        )
        assertTrue(source.contains("onCollapse = { styleCueId = null }"))
        assertTrue(source.contains("if (styleEditLocked) \"🔒 全部\" else \"🔓 单条\""))
        assertTrue(source.contains("if (layoutEditLocked) \"🔒 全部布局\" else \"🔓 单条布局\""))
        assertTrue(source.contains("viewModel.mergeCaption(cueId, CaptionMergeDirection.PREVIOUS)"))
        assertTrue(source.contains("viewModel.mergeCaption(cueId, CaptionMergeDirection.NEXT)"))
        assertTrue(source.contains("globalMode = styleEditLocked"))
        assertTrue(source.contains("globalHasOverride = hasAnyOverride"))
        assertTrue(source.contains("清除全部字幕覆盖"))
        assertTrue(source.contains("基础样式"))
        assertTrue(source.contains("整体样式模式"))
        assertTrue(source.contains("单条样式模式"))
        assertTrue(!source.contains("ModalBottomSheet("))
        assertTrue(source.contains("CueSuggestionDialog("))
        assertTrue(source.contains("Text(\"字体\""))
        assertTrue(source.contains("Text(\"A-\""))
        assertTrue(source.contains("Text(\"A+\""))
    }

    @Test
    fun overlayAndInlineEditorUseTheSameStableCueApis() {
        val source = source()
        listOf(
            "viewModel::updateCueDirectPosition",
            "viewModel::updateCueDirectWidth",
            "viewModel::updateCueDirectFontSize",
            "viewModel.splitCaptionDraft(",
            "viewModel.requestCueSuggestion(",
            "viewModel.updateEnglishText(",
            "viewModel.updateCueFontFamily(",
        ).forEach { call -> assertTrue("missing integrated call: $call", source.contains(call)) }
        assertTrue(source.contains("DirectEditTouchTarget = 48.dp"))
        assertTrue(source.contains("contentDescription = \"删除当前字幕\""))
        assertTrue(source.contains("contentDescription = \"左右拉伸字幕宽度\""))
        assertTrue(source.contains("contentDescription = \"缩放字幕字号\""))
    }

    private fun source(): String {
        val root = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(root, root.parentFile, root.parentFile?.parentFile)
            .filterNotNull()
            .map { File(it, "app/src/main/java/com/example/lyriccaptioner/ui/EditorScreen.kt") }
            .firstOrNull(File::exists)
            ?.readText()
            ?: error("EditorScreen.kt not found from ${root.absolutePath}")
    }
}
