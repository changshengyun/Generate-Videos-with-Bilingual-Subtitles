package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class TranslationModelState {
    NEEDS_INSTALL,
    PREPARING,
    READY,
    FAILED,
}

enum class TranslationStage {
    MODEL_PREPARATION,
    TRANSLATING,
    COMMITTING,
}

data class TranslationBatchResult(
    val captions: List<CaptionCue>,
    val translatedCount: Int,
)

class TranslationBatchException(
    val stage: TranslationStage,
    cause: Throwable,
) : IllegalStateException("Translation failed during ${stage.name.lowercase()}.", cause)

class TranslationModule(
    private val translator: LocalTranslator,
) {
    private val operationMutex = Mutex()

    @Volatile
    var modelState: TranslationModelState = TranslationModelState.NEEDS_INSTALL
        private set

    suspend fun refreshModelState(
        onStateChanged: (TranslationModelState) -> Unit = {},
    ): TranslationModelState = operationMutex.withLock {
        val refreshed = try {
            if (translator.isModelReady()) {
                TranslationModelState.READY
            } else {
                TranslationModelState.NEEDS_INSTALL
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            TranslationModelState.FAILED
        }
        transitionTo(refreshed, onStateChanged)
        refreshed
    }

    suspend fun translateMissingChinese(
        captions: List<CaptionCue>,
        onStateChanged: (TranslationModelState) -> Unit = {},
        onStageChanged: (TranslationStage) -> Unit = {},
    ): TranslationBatchResult = operationMutex.withLock {
        val targetIndexes = captions.indices.filter { index ->
            val cue = captions[index]
            cue.english.isNotBlank() && cue.chinese.isBlank()
        }
        if (targetIndexes.isEmpty()) {
            return@withLock TranslationBatchResult(captions, translatedCount = 0)
        }

        var prepared = false
        var stage = TranslationStage.MODEL_PREPARATION
        try {
            onStageChanged(stage)
            transitionTo(TranslationModelState.PREPARING, onStateChanged)
            translator.prepareBatch()
            prepared = true
            transitionTo(TranslationModelState.READY, onStateChanged)

            stage = TranslationStage.TRANSLATING
            onStageChanged(stage)
            val translatedByIndex = linkedMapOf<Int, String>()
            targetIndexes.forEach { index ->
                currentCoroutineContext().ensureActive()
                val translated = translator.translateEnglishToChinese(captions[index].english).trim()
                check(translated.isNotEmpty()) { "Translator returned an empty result." }
                translatedByIndex[index] = translated
            }

            currentCoroutineContext().ensureActive()
            stage = TranslationStage.COMMITTING
            onStageChanged(stage)
            val updated = captions.mapIndexed { index, cue ->
                translatedByIndex[index]?.let { chinese ->
                    cue.copy(chinese = chinese, confirmed = false)
                } ?: cue
            }
            TranslationBatchResult(updated, translatedByIndex.size)
        } catch (error: CancellationException) {
            transitionTo(
                if (prepared) TranslationModelState.READY else TranslationModelState.NEEDS_INSTALL,
                onStateChanged,
            )
            throw error
        } catch (error: Throwable) {
            transitionTo(
                if (prepared) TranslationModelState.READY else TranslationModelState.FAILED,
                onStateChanged,
            )
            throw TranslationBatchException(stage, error)
        }
    }

    private fun transitionTo(
        state: TranslationModelState,
        onStateChanged: (TranslationModelState) -> Unit,
    ) {
        modelState = state
        onStateChanged(state)
    }
}
