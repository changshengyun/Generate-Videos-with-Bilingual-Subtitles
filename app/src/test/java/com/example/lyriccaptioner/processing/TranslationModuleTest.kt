package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TranslationModuleTest {
    @Test
    fun refreshReportsNeedsInstallAndReadyFromInstalledModelState() = runBlocking {
        val translator = FakeTranslator(modelReady = false)
        val module = TranslationModule(translator)

        assertEquals(TranslationModelState.NEEDS_INSTALL, module.refreshModelState())
        translator.modelReady = true
        assertEquals(TranslationModelState.READY, module.refreshModelState())
    }

    @Test
    fun firstPreparationTransitionsToReadyAndTranslatesAtomically() = runBlocking {
        val translator = FakeTranslator(
            translations = mapOf("One" to "一", "Two" to "二"),
        )
        val module = TranslationModule(translator)
        val states = mutableListOf<TranslationModelState>()
        val source = listOf(cue("a", 0L, 1_000L, "One"), cue("b", 1_000L, 2_000L, "Two"))

        val result = module.translateMissingChinese(source, states::add)

        assertEquals(listOf(TranslationModelState.PREPARING, TranslationModelState.READY), states)
        assertEquals(1, translator.prepareCalls)
        assertEquals(listOf("一", "二"), result.captions.map { it.chinese })
        assertTrue(result.captions.none { it.confirmed })
        assertEquals(TranslationModelState.READY, module.modelState)
    }

    @Test
    fun offlineReuseStillUsesPreparationApiAndKeepsReadyState() = runBlocking {
        val translator = FakeTranslator(
            modelReady = true,
            translations = mapOf("Offline" to "离线"),
        )
        val module = TranslationModule(translator)
        assertEquals(TranslationModelState.READY, module.refreshModelState())

        val result = module.translateMissingChinese(listOf(cue("a", 0L, 1_000L, "Offline")))

        assertEquals("离线", result.captions.single().chinese)
        assertEquals(1, translator.prepareCalls)
        assertEquals(TranslationModelState.READY, module.modelState)
    }

    @Test
    fun skipsBlankEnglishAndExistingManualChinese() = runBlocking {
        val translator = FakeTranslator(translations = mapOf("Translate" to "翻译"))
        val source = listOf(
            cue("blank", 0L, 1_000L, ""),
            cue("manual", 1_000L, 2_000L, "Keep", chinese = "人工中文", confirmed = true),
            cue("target", 2_000L, 3_000L, "Translate"),
        )

        val result = TranslationModule(translator).translateMissingChinese(source)

        assertEquals("", result.captions[0].chinese)
        assertEquals(source[1], result.captions[1])
        assertEquals("翻译", result.captions[2].chinese)
        assertEquals(1, result.translatedCount)
    }

    @Test
    fun noTargetsDoesNotPrepareOrReplaceInputList() = runBlocking {
        val translator = FakeTranslator()
        val source = listOf(cue("manual", 0L, 1_000L, "Keep", chinese = "保留"))

        val result = TranslationModule(translator).translateMissingChinese(source)

        assertSame(source, result.captions)
        assertEquals(0, result.translatedCount)
        assertEquals(0, translator.prepareCalls)
    }

    @Test
    fun preparationFailureWritesNothingAndMovesModelToFailed() = runBlocking {
        val translator = FakeTranslator(prepareFailuresRemaining = 1)
        val module = TranslationModule(translator)
        val source = listOf(cue("a", 0L, 1_000L, "One"))

        val error = expectBatchFailure { module.translateMissingChinese(source) }

        assertEquals(TranslationStage.MODEL_PREPARATION, error.stage)
        assertEquals(TranslationModelState.FAILED, module.modelState)
        assertEquals("", source.single().chinese)
    }

    @Test
    fun translationFailureAfterEarlierResultStillWritesNothing() = runBlocking {
        val translator = FakeTranslator(
            translations = mapOf("One" to "一"),
            failOnText = "Two",
        )
        val module = TranslationModule(translator)
        val source = listOf(cue("a", 0L, 1_000L, "One"), cue("b", 1_000L, 2_000L, "Two"))

        val error = expectBatchFailure { module.translateMissingChinese(source) }

        assertEquals(TranslationStage.TRANSLATING, error.stage)
        assertEquals(TranslationModelState.READY, module.modelState)
        assertTrue(source.all { it.chinese.isBlank() })
    }

    @Test
    fun cancellationAfterEarlierResultWritesNothingAndCanRetry() = runBlocking {
        val blocker = CompletableDeferred<Unit>()
        val translator = FakeTranslator(
            translations = mapOf("One" to "一", "Two" to "二"),
            blockOnText = "Two",
            blocker = blocker,
        )
        val module = TranslationModule(translator)
        val source = listOf(cue("a", 0L, 1_000L, "One"), cue("b", 1_000L, 2_000L, "Two"))
        var result: TranslationBatchResult? = null
        var cancelled = false
        val job = launch {
            try {
                result = module.translateMissingChinese(source)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        while (translator.translateCalls < 2) yield()

        job.cancelAndJoin()

        assertTrue(cancelled)
        assertEquals(null, result)
        assertTrue(source.all { it.chinese.isBlank() })
        assertEquals(TranslationModelState.READY, module.modelState)

        translator.blockOnText = null
        val retry = module.translateMissingChinese(source)
        assertEquals(listOf("一", "二"), retry.captions.map { it.chinese })
    }

    @Test
    fun failedPreparationCanBeRetriedSuccessfully() = runBlocking {
        val translator = FakeTranslator(
            translations = mapOf("Retry" to "重试"),
            prepareFailuresRemaining = 1,
        )
        val module = TranslationModule(translator)
        val source = listOf(cue("a", 0L, 1_000L, "Retry"))
        expectBatchFailure { module.translateMissingChinese(source) }

        val retry = module.translateMissingChinese(source)

        assertEquals("重试", retry.captions.single().chinese)
        assertEquals(2, translator.prepareCalls)
        assertEquals(TranslationModelState.READY, module.modelState)
    }

    @Test
    fun outputPreservesIdsOrderAndTimestamps() = runBlocking {
        val translator = FakeTranslator(translations = mapOf("One" to "一", "Two" to "二"))
        val source = listOf(
            cue("second-id", 500L, 900L, "One"),
            cue("first-id", 1_200L, 2_500L, "Two"),
        )

        val result = TranslationModule(translator).translateMissingChinese(source)

        assertEquals(source.map { it.id }, result.captions.map { it.id })
        assertEquals(source.map { it.startMs }, result.captions.map { it.startMs })
        assertEquals(source.map { it.endMs }, result.captions.map { it.endMs })
    }

    @Test
    fun emptyTranslatedTextFailsWholeBatch() = runBlocking {
        val translator = FakeTranslator(translations = mapOf("One" to " "))
        val source = listOf(cue("a", 0L, 1_000L, "One"))

        val error = expectBatchFailure {
            TranslationModule(translator).translateMissingChinese(source)
        }

        assertEquals(TranslationStage.TRANSLATING, error.stage)
        assertFalse(source.single().confirmed)
        assertEquals("", source.single().chinese)
    }

    private suspend fun expectBatchFailure(block: suspend () -> Unit): TranslationBatchException {
        try {
            block()
            fail("Expected TranslationBatchException")
        } catch (error: TranslationBatchException) {
            return error
        }
        error("unreachable")
    }

    private fun cue(
        id: String,
        startMs: Long,
        endMs: Long,
        english: String,
        chinese: String = "",
        confirmed: Boolean = false,
    ) = CaptionCue(
        id = id,
        startMs = startMs,
        endMs = endMs,
        english = english,
        chinese = chinese,
        confidence = 0.9f,
        confirmed = confirmed,
    )

    private class FakeTranslator(
        var modelReady: Boolean = false,
        private val translations: Map<String, String> = emptyMap(),
        var prepareFailuresRemaining: Int = 0,
        private val failOnText: String? = null,
        var blockOnText: String? = null,
        private val blocker: CompletableDeferred<Unit>? = null,
    ) : LocalTranslator {
        var prepareCalls: Int = 0
        var translateCalls: Int = 0

        override suspend fun isModelReady(): Boolean = modelReady

        override suspend fun prepareBatch() {
            prepareCalls += 1
            if (prepareFailuresRemaining > 0) {
                prepareFailuresRemaining -= 1
                throw IllegalStateException("model unavailable")
            }
            modelReady = true
        }

        override suspend fun translateEnglishToChinese(text: String): String {
            translateCalls += 1
            if (text == failOnText) throw IllegalStateException("translation unavailable")
            if (text == blockOnText) blocker?.await()
            return translations[text] ?: "中:$text"
        }
    }
}
