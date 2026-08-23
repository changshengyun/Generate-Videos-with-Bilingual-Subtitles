package com.example.lyriccaptioner.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused source contracts for the V3 UI shell. The project does not include
 * the Compose UI test artifact in the JVM test configuration, so these checks
 * keep the product-level semantics executable without starting an Android UI.
 * They intentionally fail if a future UI reintroduces app chrome or private
 * media details into accessibility output.
 */
class EditorScreenV3UiContractTest {
    @Test
    fun shellUsesSystemInsetsAndHasNoAppBarOrDevelopmentBadge() {
        val source = editorScreenSource()

        assertTrue(source.contains(".statusBarsPadding()"))
        assertTrue(source.contains(".navigationBarsPadding()"))
        assertFalse("the editor must not add a second app top bar", source.contains("TopAppBar"))
        assertFalse("the editor must not show a development badge", source.contains("Text(\"V2\""))
        assertFalse("the editor must not call the removed app header", source.contains("Header()"))
    }

    @Test
    fun completeCaptionFlowAutomaticallyEntersTheEditor() {
        val source = editorScreenSource()

        assertTrue(source.contains("CaptionWorkflowStage.READY_FOR_EDIT"))
        assertTrue(source.contains("activeSection = EditorSection.CAPTIONS.index"))
        assertTrue(source.contains("when (activeSection)"))
    }

    @Test
    fun captionListIsGatedToTheCaptionEditorSection() {
        val source = editorScreenSource()

        assertTrue(source.contains("if (showsCaptionList(activeSection))"))
        assertTrue(source.contains("contentDescription = \"caption_list\""))
    }

    @Test
    fun stableSemanticsCoverNavigationAndPrimaryTouchTargets() {
        val source = editorScreenSource()
        listOf(
            "workbench_import",
            "workbench_asr",
            "workbench_subtitles",
            "workbench_export",
            "import_video",
            "generate_captions",
            "export_video",
            "caption_list",
        ).forEach { id ->
            assertTrue("missing semantics id: $id", source.contains(id))
        }
        assertTrue(source.contains("heightIn(min = 52.dp)"))
    }

    @Test
    fun narrowScreenContentRemainsScrollableAndDoesNotExposeRawExportUri() {
        val source = editorScreenSource()

        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("LazyColumn("))
        assertFalse("accessibility must not expose a private content URI", source.contains("export_uri:"))
        assertFalse("debug snapshots must not be rendered as UI semantics", source.contains("debug_snapshot"))
    }

    @Test
    fun productUiExposesOneCompleteCaptionWorkflowAndGalleryVideoExport() {
        val source = editorScreenSource()

        assertTrue(source.contains("PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)"))
        assertTrue(source.contains("onClick = viewModel::generateCompleteCaptions"))
        assertTrue(source.contains("onClick = viewModel::cancelCaptionWorkflow"))
        assertFalse(source.contains("onClick = viewModel::enhanceCaptions"))
        assertFalse(source.contains("accessibilityId = \"enhance_captions\""))
        assertTrue(source.contains("onClick = { projectPicker.launch"))
        assertTrue(source.contains("modelPicker.launch"))
        assertTrue(source.contains("viewModel.exportVideo()"))
        assertFalse(source.contains("viewModel::translateMissingChinese"))
        assertFalse(source.contains("createCaptionsFromLyrics"))
        assertFalse(source.contains("exportSidecarSrt"))
        assertFalse(source.contains("CreateDocument(\"application/x-subrip\")"))
    }

    @Test
    fun addCaptionUsesTheCurrentPlayerPosition() {
        val source = editorScreenSource()

        assertTrue(source.contains("onPlaybackPositionChanged = { playbackPositionMs = it }"))
        assertTrue(source.contains("viewModel.addCaptionAt(playbackPositionMs)"))
        assertFalse(source.contains("viewModel.addCaption()"))
    }

    @Test
    fun normalAndFullscreenPlayersUseASeparateAccessibleControlRow() {
        val source = editorScreenSource()

        assertEquals(2, Regex("useController = false").findAll(source).count())
        assertFalse(source.contains("useController = true"))
        assertEquals(2, Regex("PlayerControlRow\\(").findAll(source).count() - 1)
        assertTrue(source.contains("private fun PlayerControlRow"))
        assertTrue(source.contains("contentDescription = \"预览进度条\""))
        assertTrue(source.contains(".size(48.dp)"))
        assertTrue(source.contains(".heightIn(min = 48.dp)"))
        assertTrue(source.contains(".imePadding()"))
    }

    @Test
    fun exportSemanticsAndCancellationUseExplicitExportState() {
        val source = editorScreenSource()

        assertTrue(source.contains("state.exportState == ExportState.RUNNING"))
        assertTrue(source.contains("exportState == ExportState.SUCCEEDED"))
        assertTrue(source.contains("contentDescription = \"export_complete\""))
        assertFalse(source.contains("status.startsWith(\"Export complete\")"))
        assertTrue(source.contains("contentDescription = \"video_media_revision_${'$'}mediaRevision\""))
    }

    @Test
    fun cleanMediaAcceptanceWaitsForNewAppliedMediaBeforeProductPlayback() {
        val source = localAiInstrumentationSource()
        val cleanFlow = source.substringAfter("private fun runCleanMediaAcceptance")
            .substringBefore("private fun runIllegalMediaAcceptance")
        val revisionWait = cleanFlow.indexOf("val reimportedMedia = waitForNode")
        val productFullscreen = cleanFlow.indexOf(
            "clickNode(waitForContentDescription(\"preview_fullscreen\"",
            startIndex = revisionWait,
        )

        assertTrue(cleanFlow.contains("initialMediaRevision"))
        assertTrue(cleanFlow.contains("it.contentDescription?.toString() != initialMediaRevision"))
        assertTrue(revisionWait >= 0)
        assertTrue(productFullscreen > revisionWait)
    }

    private fun editorScreenSource(): String {
        val root = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = sequenceOf(
            root,
            root.parentFile,
            root.parentFile?.parentFile,
        ).filterNotNull()
            .map { File(it, "app/src/main/java/com/example/lyriccaptioner/ui/EditorScreen.kt") }
            .firstOrNull(File::exists)
            ?: error("EditorScreen.kt not found from ${root.absolutePath}")
        return sourceFile.readText()
    }

    private fun localAiInstrumentationSource(): String {
        val root = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = sequenceOf(root, root.parentFile, root.parentFile?.parentFile)
            .filterNotNull()
            .map { File(it, "app/src/androidTest/java/com/example/lyriccaptioner/LocalAiInstrumentation.kt") }
            .firstOrNull(File::exists)
            ?: error("LocalAiInstrumentation.kt not found from ${root.absolutePath}")
        return sourceFile.readText()
    }
}
