package com.example.lyriccaptioner.processing

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class MlKitLocalTranslator : LocalTranslator {
    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.CHINESE)
        .build()
    private val translator = Translation.getClient(options)
    private var prepared = false

    override suspend fun isModelReady(): Boolean {
        val downloaded = RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .await()
        return downloaded.contains(englishModel) && downloaded.contains(chineseModel)
    }

    override suspend fun prepareBatch() {
        if (prepared) return
        try {
            withTimeout(MODEL_DOWNLOAD_TIMEOUT_MS) {
                translator.downloadModelIfNeeded(downloadConditions()).await()
            }
            prepared = true
        } catch (error: TimeoutCancellationException) {
            throw IllegalStateException(
                "Chinese translation model preparation timed out. Check the network and retry.",
                error,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(
                "Chinese translation model preparation failed. Check the network and retry.",
                error,
            )
        }
    }

    override suspend fun translateEnglishToChinese(text: String): String {
        if (text.isBlank()) return ""
        prepareBatch()
        return translator.translate(text).await()
    }

    private fun downloadConditions(): DownloadConditions {
        return DownloadConditions.Builder().build()
    }

    private companion object {
        const val MODEL_DOWNLOAD_TIMEOUT_MS = 90_000L
        val englishModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        val chineseModel = TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build()
    }
}

class MlKitTranslationModelStatus : LocalModelManager {
    private val modelManager = RemoteModelManager.getInstance()
    private val englishModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
    private val chineseModel = TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build()

    override suspend fun status(): List<LocalModelStatus> {
        val downloaded = modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        return listOf(
            LocalModelStatus(
                name = "ML Kit English model",
                ready = downloaded.contains(englishModel),
                detail = "Required for offline source-language handling.",
            ),
            LocalModelStatus(
                name = "ML Kit Chinese model",
                ready = downloaded.contains(chineseModel),
                detail = "Required for offline English-to-Chinese subtitles.",
            ),
        )
    }
}
