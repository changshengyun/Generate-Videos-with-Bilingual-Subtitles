package com.example.lyriccaptioner.processing

import android.content.Context
import android.net.Uri
import com.example.lyriccaptioner.model.SpeechMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperModelStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    val modelFile: File
        get() = File(appContext.filesDir, MODEL_RELATIVE_PATH)

    fun status(): WhisperRuntimeStatus {
        val installed = WhisperModelValidator.isValid(modelFile)
        val nativeReady = WhisperNativeBridge.isAvailable
        return WhisperRuntimeStatusResolver.resolve(installed, nativeReady)
    }

    suspend fun install(sourceUri: Uri): WhisperRuntimeStatus = withContext(Dispatchers.IO) {
        val input = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Could not read the selected model file.")
        WhisperModelImporter.install(input, modelFile)
        status()
    }

    companion object {
        const val MODEL_RELATIVE_PATH = "models/ggml-base.bin"
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
