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
    fun eachCueOwnsTextTimingStyleFontAndSplitControls() {
        val source = source()
        val card = source.substringAfter("private fun CaptionCard")
            .substringBefore("private fun readabilityIssueLabel")

        assertTrue(card.contains("cue_style_toggle"))
        assertTrue(card.contains("styleExpanded = !styleExpanded"))
        assertTrue(card.contains("CueStyleControls("))
        assertTrue(card.contains("cue_split_toggle"))
        assertTrue(card.contains("cue_split_confirm"))
        assertTrue(card.contains("英文字幕"))
        assertTrue(card.contains("中文字幕"))
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
            "viewModel.splitCaption(",
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
