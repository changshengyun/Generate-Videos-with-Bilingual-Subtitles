package com.example.lyriccaptioner.processing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.lyriccaptioner.model.SpeechMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperModelStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    val modelDirectory: File
        get() = File(appContext.filesDir, MODEL_DIRECTORY)

    val modelFile: File
        get() = activeModel()?.let(::fileFor) ?: fileFor(WhisperModelCatalog.baseline)

    val selectedModel: ApprovedWhisperModel?
        get() = activeModel()

    fun status(): WhisperRuntimeStatus {
        val model = activeModel()
        val installed = model != null
        val nativeReady = WhisperNativeSessionBridge.isAvailable
        return WhisperRuntimeStatusResolver.resolve(
            modelInstalled = installed,
            nativeLibraryReady = nativeReady,
            modelFileName = model?.fileName,
        )
    }

    fun ensureBundledModel() {
        val model = WhisperModelCatalog.smallEnQ5_1
        if (!WhisperModelValidator.isValid(fileFor(model), model)) {
            appContext.assets.open("models/${model.fileName}").use { input ->
                WhisperModelImporter.install(input, fileFor(model), model)
            }
        }
        select(model.fileName)
    }

    suspend fun install(sourceUri: Uri): WhisperRuntimeStatus = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(sourceUri)
            ?: throw IllegalArgumentException("The selected file has no model filename.")
        val model = WhisperModelCatalog.find(fileName)
            ?: throw IllegalArgumentException("The selected Whisper model is not approved: $fileName")
        val input = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Could not read the selected model file.")
        WhisperModelImporter.install(input, fileFor(model), model)
        select(model.fileName)
        status()
    }

    fun select(fileName: String): WhisperRuntimeStatus {
        val model = WhisperModelSelector.requireInstalled(fileName, installedFileNames())
        writeSelection(model.fileName)
        return status()
    }

    fun installedModels(): List<ApprovedWhisperModel> =
        WhisperModelCatalog.approved.filter { WhisperModelValidator.isValid(fileFor(it), it) }

    companion object {
        const val MODEL_DIRECTORY = "models"
        const val MODEL_RELATIVE_PATH = "models/ggml-base.bin"
        private const val SELECTED_MODEL_FILE_NAME = ".selected-model"
    }

    private fun activeModel(): ApprovedWhisperModel? {
        val selected = selectionFile().takeIf(File::isFile)?.readText()?.trim()
            ?.let(WhisperModelCatalog::find)
        return selected?.takeIf { WhisperModelValidator.isValid(fileFor(it), it) }
            ?: WhisperModelSelector.defaultInstalled(installedFileNames())
    }

    private fun installedFileNames(): Set<String> =
        WhisperModelCatalog.approved
            .filter { WhisperModelValidator.isValid(fileFor(it), it) }
            .mapTo(mutableSetOf()) { it.fileName }

    private fun fileFor(model: ApprovedWhisperModel): File = File(modelDirectory, model.fileName)

    private fun selectionFile(): File = File(modelDirectory, SELECTED_MODEL_FILE_NAME)

    private fun writeSelection(fileName: String) {
        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Could not create the local model directory."
        }
        val temporary = File(modelDirectory, "$SELECTED_MODEL_FILE_NAME.importing")
        try {
            temporary.writeText(fileName)
            moveReplacing(temporary, selectionFile())
        } finally {
            temporary.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

data class WhisperRuntimeStatus(
    val modelInstalled: Boolean,
    val nativeLibraryReady: Boolean,
    val localRecognitionReady: Boolean,
    val mode: SpeechMode,
    val detail: String,
    val modelFileName: String? = null,
)

object WhisperRuntimeStatusResolver {
    fun resolve(
        modelInstalled: Boolean,
        nativeLibraryReady: Boolean,
        modelFileName: String? = null,
    ): WhisperRuntimeStatus {
        val localReady = modelInstalled && nativeLibraryReady
        val reason = when {
            !modelInstalled && !nativeLibraryReady -> "Whisper model and JNI library are missing."
            !modelInstalled -> "A compatible Whisper model has not been imported."
            else -> "Whisper JNI library is missing; build with enableWhisperNative."
        }
        val mode = if (localReady) SpeechMode.LOCAL else SpeechMode.UNAVAILABLE
        val detail = when (mode) {
            SpeechMode.LOCAL -> "Local Whisper JNI is ready (${modelFileName ?: "approved model"})."
            SpeechMode.UNAVAILABLE -> "ASR unavailable: $reason"
        }
        return WhisperRuntimeStatus(
            modelInstalled = modelInstalled,
            nativeLibraryReady = nativeLibraryReady,
            localRecognitionReady = localReady,
            mode = mode,
            detail = detail,
            modelFileName = modelFileName,
        )
    }
}
