package com.example.lyriccaptioner.processing

import android.content.Context
import android.net.Uri
import com.example.lyriccaptioner.model.SpeechMode
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
        return WhisperRuntimeStatusResolver.resolve(installed, nativeReady)
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
    val mode: SpeechMode,
    val detail: String,
)

object WhisperRuntimeStatusResolver {
    fun resolve(
        modelInstalled: Boolean,
        nativeLibraryReady: Boolean,
        demoAvailable: Boolean = true,
    ): WhisperRuntimeStatus {
        val localReady = modelInstalled && nativeLibraryReady
        val reason = when {
            !modelInstalled && !nativeLibraryReady -> "Whisper model and JNI library are missing."
            !modelInstalled -> "A compatible Whisper model has not been imported."
            else -> "Whisper JNI library is missing; build with enableWhisperNative."
        }
        val mode = when {
            localReady -> SpeechMode.LOCAL
            demoAvailable -> SpeechMode.DEMO
            else -> SpeechMode.UNAVAILABLE
        }
        val detail = when (mode) {
            SpeechMode.LOCAL -> "Local Whisper JNI is ready."
            SpeechMode.DEMO -> "Demo ASR is active. Local unavailable: $reason"
            SpeechMode.UNAVAILABLE -> "ASR unavailable: $reason"
        }
        return WhisperRuntimeStatus(
            modelInstalled = modelInstalled,
            nativeLibraryReady = nativeLibraryReady,
            localRecognitionReady = localReady,
            mode = mode,
            detail = detail,
        )
    }
}
