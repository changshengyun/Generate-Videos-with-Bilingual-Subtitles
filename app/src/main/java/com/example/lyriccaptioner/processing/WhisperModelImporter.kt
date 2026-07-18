package com.example.lyriccaptioner.processing

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object WhisperModelImporter {
    fun install(input: InputStream, target: File) {
        val directory = requireNotNull(target.parentFile)
        check(directory.exists() || directory.mkdirs()) {
            "Could not create the local model directory."
        }
        val temporary = File(directory, "${target.name}.importing")
        temporary.delete()

        try {
            input.use { source ->
                temporary.outputStream().buffered().use { output -> source.copyTo(output) }
            }
            require(WhisperModelValidator.isValid(temporary)) {
                "The selected file is not the approved ggml-base.bin model."
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
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
