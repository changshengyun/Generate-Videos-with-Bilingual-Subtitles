package com.example.lyriccaptioner.processing

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class LocalTranslationArtifact(
    val fileName: String,
    val byteCount: Long,
    val sha256: String,
)

object LocalTranslationModelCatalog {
    const val ASSET_DIRECTORY = "local_models/opus-mt-en-zh"
    const val PRIVATE_DIRECTORY = "models/opus-mt-en-zh"

    val artifacts = listOf(
        LocalTranslationArtifact(
            fileName = "encoder_model_quantized.onnx",
            byteCount = 52_899_742L,
            sha256 = "d3b7912bf6a9bd27e4c074c2df91d4ff3d5b4bc5f7f6c8d7cc9c805c98fbafee",
        ),
        LocalTranslationArtifact(
            fileName = "decoder_model_merged_quantized.onnx",
            byteCount = 60_212_804L,
            sha256 = "023be4f841f4c47cd65fffcbaa81c0d99d7f7e0138f7ba0e03fa220a4e688aff",
        ),
        LocalTranslationArtifact(
            fileName = "source.spm",
            byteCount = 806_435L,
            sha256 = "5775ddc9e3ff2fae91554da56468ad35ff56edaba870fea74447bc7234bfdaa8",
        ),
        LocalTranslationArtifact(
            fileName = "target.spm",
            byteCount = 804_600L,
            sha256 = "81dc94efa84e4025ef38d25d5d07429fe41e3eb29d44003f1db6fe98487b0052",
        ),
        LocalTranslationArtifact(
            fileName = "tokenizer.json",
            byteCount = 6_380_952L,
            sha256 = "d0c7da27056e8f42adce9e76d8e792e5daa64e15f5acd2e7aabf0121877dd4c1",
        ),
        LocalTranslationArtifact(
            fileName = "config.json",
            byteCount = 1_503L,
            sha256 = "4727d1229a04f95bf6f39abf949d8080615433d99d6ebd85f81c09edd247d5fa",
        ),
        LocalTranslationArtifact(
            fileName = "generation_config.json",
            byteCount = 293L,
            sha256 = "b743baabb7da4c1a2f19fe558bd6b4c0c7c3b0762fcb5ca7a48fe5a2c2219803",
        ),
    )

    fun find(fileName: String): LocalTranslationArtifact? = artifacts.firstOrNull { it.fileName == fileName }
}

object LocalTranslationModelValidator {
    fun isValid(file: File, artifact: LocalTranslationArtifact): Boolean {
        if (!file.isFile || file.length() != artifact.byteCount) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == artifact.sha256
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

object LocalTranslationModelImporter {
    fun install(input: InputStream, target: File, artifact: LocalTranslationArtifact) {
        require(target.name == artifact.fileName) {
            "The target filename does not match the approved translation artifact."
        }
        val directory = requireNotNull(target.parentFile)
        check(directory.exists() || directory.mkdirs()) { "Could not create the translation model directory." }
        val temporary = File(directory, "${target.name}.importing")
        temporary.delete()
        try {
            input.use { source ->
                temporary.outputStream().buffered().use { output -> source.copyTo(output) }
            }
            require(LocalTranslationModelValidator.isValid(temporary, artifact)) {
                "The translation artifact failed size or SHA-256 validation: ${artifact.fileName}"
            }
            moveReplacing(temporary, target)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
