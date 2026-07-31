package com.example.lyriccaptioner.processing

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WhisperModelImporterTest {
    private val testModel = ApprovedWhisperModel(
        fileName = "ggml-base.en.bin",
        sha1 = MessageDigest.getInstance("SHA-1").digest("approved".toByteArray()).toHex(),
    )

    @Test
    fun validImportUsesApprovedSpecAndAtomicallySwitchesTarget() {
        val directory = kotlin.io.path.createTempDirectory("model-import-valid").toFile()
        val target = File(directory, testModel.fileName).apply { writeText("old") }
        try {
            WhisperModelImporter.install(ByteArrayInputStream("approved".toByteArray()), target, testModel)
            assertEquals("approved", target.readText())
            assertFalse(File(directory, "${testModel.fileName}.importing").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun unknownModelIsRejectedBeforeImport() {
        val directory = kotlin.io.path.createTempDirectory("model-import-unknown").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                WhisperModelImporter.install(ByteArrayInputStream("approved".toByteArray()), File(directory, "unknown.bin"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
