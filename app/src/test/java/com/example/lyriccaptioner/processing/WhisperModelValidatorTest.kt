package com.example.lyriccaptioner.processing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WhisperModelValidatorTest {
    private fun officialModel(fileName: String): File {
        var directory = File(System.getProperty("user.dir")).canonicalFile
        while (directory.parentFile != null) {
            val candidate = File(directory, ".tool-downloads/models/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return File(System.getProperty("user.dir"), ".tool-downloads/models/$fileName")
    }

    @Test
    fun acceptsApprovedOfficialModel() {
        val model = officialModel(WhisperModelCatalog.BASELINE_FILE_NAME)

        assertTrue("approved model fixture is required", model.isFile)
        assertEquals(WhisperModelValidator.EXPECTED_MODEL_BYTES, model.length())
        assertTrue(WhisperModelValidator.isValid(model))
    }

    @Test
    fun acceptsAllDownloadedApprovedModelSummaries() {
        WhisperModelCatalog.approved.forEach { spec ->
            val model = officialModel(spec.fileName)
            assertTrue("approved model fixture is required: ${spec.fileName}", model.isFile)
            assertTrue("summary mismatch: ${spec.fileName}", WhisperModelValidator.isValid(model, spec))
        }
    }

    @Test
    fun catalogContainsOnlyApprovedModelsAndRejectsUnknownNames() {
        assertEquals(
            setOf("ggml-base.bin", "ggml-base.en.bin", "ggml-small.en-q5_1.bin"),
            WhisperModelCatalog.approved.map { it.fileName }.toSet(),
        )
        assertTrue(WhisperModelCatalog.find("ggml-base.en.bin") != null)
        assertFalse(WhisperModelValidator.isValid(File("ggml-unknown.bin")))
    }

    @Test
    fun modelSwitchingAcceptsOnlyInstalledApprovedModel() {
        val installed = setOf("ggml-base.bin", "ggml-base.en.bin")

        assertEquals(
            "ggml-base.en.bin",
            WhisperModelSelector.requireInstalled("ggml-base.en.bin", installed).fileName,
        )
        assertEquals(
            "ggml-base.bin",
            WhisperModelSelector.defaultInstalled(installed)?.fileName,
        )
        assertThrows(IllegalArgumentException::class.java) {
            WhisperModelSelector.requireInstalled("ggml-small.en-q5_1.bin", installed)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WhisperModelSelector.requireInstalled("ggml-unknown.bin", installed)
        }
    }

    @Test
    fun rejectsLargeFileWithWrongDigest() {
        val file = kotlin.io.path.createTempFile("wrong-model", ".bin").toFile()
        try {
            file.outputStream().use { it.write(ByteArray(1_000_001)) }
            assertFalse(WhisperModelValidator.isValid(file))
        } finally {
            file.delete()
        }
    }
}
