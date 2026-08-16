package com.example.lyriccaptioner.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorScreenV3DirectEditContractTest {
    @Test
    fun directEditorUsesOnlyKeyboardAndStyleTabs() {
        val source = source()
        val tabContract = source.substringAfter("private enum class DirectEditPanelTab")
            .substringBefore("@OptIn(ExperimentalLayoutApi::class)")
        val panel = source.substringAfter("private fun DirectCaptionEditPanel")
            .substringBefore("private fun DirectStyleGroupTitle")

        assertTrue(tabContract.contains("KEYBOARD(\"键盘\")"))
        assertTrue(tabContract.contains("STYLE(\"样式\")"))
        assertFalse(panel.contains("模板"))
        assertFalse(panel.contains("字体"))
        assertFalse(panel.contains("读文字"))
        assertTrue(panel.contains("DirectStyleGroupTitle(\"基础样式\")"))
        assertTrue(panel.contains("DirectStyleGroupTitle(\"文字颜色\")"))
        assertTrue(panel.contains("DirectStyleGroupTitle(\"对齐方式\")"))
    }

    @Test
    fun directEditorCallsFrozenViewModelApis() {
        val source = source()
        listOf(
            "viewModel::updateCueDirectPosition",
            "viewModel::updateCueDirectWidth",
            "viewModel::updateCueDirectFontSize",
            "viewModel::applyCueBasicStyle",
            "viewModel::updateCueUnifiedTextColor",
        ).forEach { call -> assertTrue("missing frozen call: $call", source.contains(call)) }
    }

    @Test
    fun overlayProvidesStableSelectionAndAccessibleHandles() {
        val source = source()

        assertTrue(source.contains("DirectEditTouchTarget = 48.dp"))
        assertTrue(source.contains("contentDescription = \"删除当前字幕\""))
        assertTrue(source.contains("contentDescription = \"左右拉伸字幕宽度\""))
        assertTrue(source.contains("contentDescription = \"缩放字幕字号\""))
        assertTrue(source.contains("player.pause()"))
        assertTrue(source.contains("onSelectCue(currentRender.caption.id)"))
        assertTrue(source.contains("onDeleteCue(currentRender.caption.id)"))
        assertTrue(source.contains("movedToDirectEditPosition"))
        assertTrue(source.contains("withDirectEditWidth"))
        assertTrue(source.contains("canonicalCaptionFontSizeRatio"))
    }

    @Test
    fun editSectionHidesAiConfigurationAndConsumesSharedBackgroundPlan() {
        val source = source()

        assertTrue(source.contains("if (activeSection != EditorSection.CAPTIONS.index)"))
        assertTrue(source.contains("val backgroundModifier = plan.background?.let"))
        assertTrue(source.contains("background.boxPaddingPx"))
        assertTrue(source.contains("repeat(result.lineCount)"))
        assertTrue(source.contains("onTextLayout = { textLayout = it }"))
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
