package com.example.lyriccaptioner.processing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelValidatorTest {
    private fun officialModel(): File {
        var directory = File(System.getProperty("user.dir")).canonicalFile
        while (directory.parentFile != null) {
            val candidate = File(directory, ".tool-downloads/models/ggml-base.bin")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return File(System.getProperty("user.dir"), ".tool-downloads/models/ggml-base.bin")
    }

    @Test
    fun acceptsApprovedOfficialModel() {
        val model = officialModel()

        assertTrue("approved model fixture is required", model.isFile)
        assertEquals(WhisperModelValidator.EXPECTED_MODEL_BYTES, model.length())
        assertTrue(WhisperModelValidator.isValid(model))
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
