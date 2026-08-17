package com.example.lyriccaptioner.processing

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTranslationModelCatalogTest {
    @Test
    fun catalogAcceptsOnlyApprovedTranslationArtifacts() {
        assertEquals(
            setOf(
                "encoder_model_quantized.onnx",
                "decoder_model_merged_quantized.onnx",
                "source.spm",
                "target.spm",
                "tokenizer.json",
                "config.json",
                "generation_config.json",
            ),
            LocalTranslationModelCatalog.artifacts.map { it.fileName }.toSet(),
        )
        assertEquals(null, LocalTranslationModelCatalog.find("unknown.onnx"))
    }

    @Test
    fun suppliedToolsDirectoryContainsValidApprovedTranslationArtifacts() {
        val directory = suppliedModelDirectory()

        LocalTranslationModelCatalog.artifacts.forEach { artifact ->
            val file = File(directory, artifact.fileName)
            assertTrue("missing OPUS-MT artifact: ${artifact.fileName}", file.isFile)
            assertEquals("size mismatch: ${artifact.fileName}", artifact.byteCount, file.length())
            assertTrue("digest mismatch: ${artifact.fileName}", LocalTranslationModelValidator.isValid(file, artifact))
        }
    }

    @Test
    fun damagedArtifactIsRejected() {
        val artifact = artifactFor("encoder_model_quantized.onnx", "approved")
        val file = kotlin.io.path.createTempFile("translation-model-damaged", ".onnx").toFile()
        try {
            file.writeText("corrupt")

            assertFalse(LocalTranslationModelValidator.isValid(file, artifact))
        } finally {
            file.delete()
        }
    }

    @Test
    fun validImportIsAtomicAndRemovesTemporaryFile() {
        val artifact = artifactFor("encoder_model_quantized.onnx", "approved")
        val directory = kotlin.io.path.createTempDirectory("translation-model-import").toFile()
        val target = File(directory, artifact.fileName).apply { writeText("old") }
        try {
            LocalTranslationModelImporter.install(ByteArrayInputStream("approved".toByteArray()), target, artifact)

            assertEquals("approved", target.readText())
            assertFalse(File(directory, "${artifact.fileName}.importing").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidImportKeepsExistingArtifactAndCleansTemporaryFile() {
        val artifact = artifactFor("encoder_model_quantized.onnx", "approved")
        val directory = kotlin.io.path.createTempDirectory("translation-model-invalid-import").toFile()
        val target = File(directory, artifact.fileName).apply { writeText("old") }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                LocalTranslationModelImporter.install(ByteArrayInputStream("corrupt".toByteArray()), target, artifact)
            }

            assertEquals("old", target.readText())
            assertFalse(File(directory, "${artifact.fileName}.importing").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun importRejectsUnapprovedTargetName() {
        val artifact = artifactFor("encoder_model_quantized.onnx", "approved")
        val directory = kotlin.io.path.createTempDirectory("translation-model-name-reject").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                LocalTranslationModelImporter.install(
                    ByteArrayInputStream("approved".toByteArray()),
                    File(directory, "decoder_model_merged_quantized.onnx"),
                    artifact,
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun suppliedModelDirectory(): File {
        var directory = File(System.getProperty("user.dir")).canonicalFile
        while (directory.parentFile != null) {
            val candidate = File(directory, "tools/opus-mt-en-zh")
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile
        }
        return File(System.getProperty("user.dir"), "tools/opus-mt-en-zh")
    }

    private fun artifactFor(fileName: String, content: String): LocalTranslationArtifact {
        val bytes = content.toByteArray()
        return LocalTranslationArtifact(
            fileName = fileName,
            byteCount = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
