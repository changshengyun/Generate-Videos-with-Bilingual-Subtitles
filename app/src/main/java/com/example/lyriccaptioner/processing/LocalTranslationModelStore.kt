package com.example.lyriccaptioner.processing

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalTranslationModelStore(context: Context) {
    private val appContext = context.applicationContext
    val modelDirectory: File
        get() = File(appContext.filesDir, LocalTranslationModelCatalog.PRIVATE_DIRECTORY)

    fun isReady(): Boolean = LocalTranslationModelCatalog.artifacts.all { artifact ->
        LocalTranslationModelValidator.isValid(File(modelDirectory, artifact.fileName), artifact)
    }

    fun file(fileName: String): File {
        require(LocalTranslationModelCatalog.find(fileName) != null) {
            "The translation artifact is not approved: $fileName"
        }
        return File(modelDirectory, fileName)
    }

    suspend fun prepare() = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext
        LocalTranslationModelCatalog.artifacts.forEach { artifact ->
            val target = File(modelDirectory, artifact.fileName)
            if (!LocalTranslationModelValidator.isValid(target, artifact)) {
                val assetPath = "${LocalTranslationModelCatalog.ASSET_DIRECTORY}/${artifact.fileName}"
                appContext.assets.open(assetPath).use { input ->
                    LocalTranslationModelImporter.install(input, target, artifact)
                }
            }
        }
        check(isReady()) { "The local OPUS-MT model is incomplete after installation." }
    }
}
