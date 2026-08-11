package com.example.lyriccaptioner.ui

import java.io.File
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
    fun asrSuccessProvidesExplicitEditEntryWithoutImplicitSectionSwitch() {
        val source = editorScreenSource()

        assertTrue(source.contains("contentDescription = \"asr_success_entry\""))
        assertTrue(source.contains("contentDescription = \"edit_captions\""))
        assertTrue(source.contains("onClick = { activeSection = EditorSection.CAPTIONS.index }"))
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
            "edit_captions",
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
}
