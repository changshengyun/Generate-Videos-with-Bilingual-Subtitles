package com.example.lyriccaptioner.processing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRuntimePathContractTest {
    @Test
    fun productionFactoryRegistersWhisperFfmpegAndNoAlternativeExportEngine() {
        val mainRoot = mainSourceRoot()
        val factory = File(mainRoot, "processing/AppPipelineFactory.kt").readText()
        val productionSources = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        assertTrue(factory.contains("CaptionPipeline(exportEngine = FfmpegKitSubtitleExporter(context))"))
        assertTrue(factory.contains("return WhisperAsrModule("))
        assertEquals(
            listOf("FfmpegKitSubtitleExporter.kt"),
            productionSources.filter { it.readText().contains(") : ExportEngine") }.map(File::getName),
        )
    }

    @Test
    fun viewModelHasOneGalleryVideoExportAndNoBypassGenerationOrSidecarExport() {
        val source = File(mainSourceRoot(), "MainViewModel.kt").readText()

        assertTrue(source.contains("galleryGateway.begin(taskId, sourceUri = uri)"))
        assertTrue(source.contains("pipeline.export("))
        assertTrue(source.contains("enhancementService.enhance("))
        assertTrue(source.contains("AppPipelineFactory.createAsrDefault(appContext)"))
        assertFalse(source.contains("fun createCaptionsFromLyrics("))
        assertFalse(source.contains("fun translateMissingChinese("))
        assertFalse(source.contains("fun exportSidecarSrt("))
        assertFalse(source.contains("fun saveSidecarSrt("))
    }

    @Test
    fun viewModelPublishesExplicitExportStatesAndAdvancesAppliedMediaRevision() {
        val source = File(mainSourceRoot(), "MainViewModel.kt").readText()

        assertTrue(source.contains("exportState = ExportState.RUNNING"))
        assertTrue(source.contains("exportState = ExportState.SUCCEEDED"))
        assertTrue(source.contains("exportState = ExportState.CANCELLED"))
        assertTrue(source.contains("exportState = ExportState.FAILED"))
        assertTrue(source.contains("mediaRevision = it.mediaRevision + 1L"))
    }

    private fun mainSourceRoot(): File {
        val root = File(System.getProperty("user.dir") ?: ".")
        return sequenceOf(root, root.parentFile, root.parentFile?.parentFile)
            .filterNotNull()
            .map { File(it, "app/src/main/java/com/example/lyriccaptioner") }
            .firstOrNull(File::isDirectory)
            ?: error("Main source root not found from ${root.absolutePath}")
    }
}
