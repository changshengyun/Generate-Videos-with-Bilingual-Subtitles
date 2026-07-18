package com.example.lyriccaptioner.processing

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WhisperModelImporterTest {
    @Test
    fun invalidImportKeepsExistingModelAndRemovesTemporaryFile() {
        val directory = kotlin.io.path.createTempDirectory("model-import").toFile()
        val target = File(directory, "ggml-base.bin").apply { writeText("existing-valid-model") }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                WhisperModelImporter.install(ByteArrayInputStream(ByteArray(1_000_001)), target)
            }
            assertEquals("existing-valid-model", target.readText())
            assertFalse(File(directory, "ggml-base.bin.importing").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancelledImportKeepsExistingModelAndRemovesTemporaryFile() {
        val directory = kotlin.io.path.createTempDirectory("model-import-cancel").toFile()
        val target = File(directory, "ggml-base.bin").apply { writeText("existing-valid-model") }
        val cancellingInput = object : InputStream() {
            override fun read(): Int = throw CancellationException("cancelled")
        }
        try {
            assertThrows(CancellationException::class.java) {
                WhisperModelImporter.install(cancellingInput, target)
            }
            assertEquals("existing-valid-model", target.readText())
            assertFalse(File(directory, "ggml-base.bin.importing").exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
