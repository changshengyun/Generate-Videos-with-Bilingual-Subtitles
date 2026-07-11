package com.example.lyriccaptioner.processing

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperModelStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    val modelFile: File
        get() = File(appContext.filesDir, MODEL_RELATIVE_PATH)

    fun status(): WhisperRuntimeStatus {
        val installed = modelFile.isFile && modelFile.length() >= MIN_MODEL_BYTES
        val nativeReady = WhisperNativeBridge.isAvailable
        val detail = when {
            installed && nativeReady -> "Local Whisper recognition is ready."
            installed -> "Demo recognition active: model installed, but Whisper JNI is missing."
            nativeReady -> "Demo recognition active: import a multilingual Whisper model."
            else -> "Demo recognition active: Whisper model and JNI library are missing."
        }
        return WhisperRuntimeStatus(
            modelInstalled = installed,
            nativeLibraryReady = nativeReady,
            localRecognitionReady = installed && nativeReady,
            detail = detail,
        )
    }

    suspend fun install(sourceUri: Uri): WhisperRuntimeStatus = withContext(Dispatchers.IO) {
        val target = modelFile
        val directory = requireNotNull(target.parentFile)
        check(directory.exists() || directory.mkdirs()) {
            "Could not create the local model directory."
        }
        val temporary = File(directory, "${target.name}.importing")
        temporary.delete()

        try {
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                temporary.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Could not read the selected model file.")

            require(temporary.length() >= MIN_MODEL_BYTES) {
                "The selected file is too small to be a Whisper model."
            }
            moveReplacing(temporary, target)
            status()
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

    companion object {
        const val MODEL_RELATIVE_PATH = "models/ggml-base.bin"
        const val MIN_MODEL_BYTES = 1_000_000L
    }
}

data class WhisperRuntimeStatus(
    val modelInstalled: Boolean,
    val nativeLibraryReady: Boolean,
    val localRecognitionReady: Boolean,
    val detail: String,
)
