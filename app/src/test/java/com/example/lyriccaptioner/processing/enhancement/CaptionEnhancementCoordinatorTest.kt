package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.LocalTranslator
import com.example.lyriccaptioner.processing.TranslationModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEnhancementCoordinatorTest {
    @Test
    fun cloudSuccessEmitsStateSequenceAndNeverCallsLocalTranslator() = runBlocking {
        val source = rawCues()
        val provider = RecordingProvider { request -> validResponse(request) }
        val translator = RecordingTranslator()
        val states = mutableListOf<CaptionEnhancementState>()

        val outcome = coordinator(provider, translator).enhance("job-001", source, states::add)

        assertEquals(
            listOf(
                CaptionEnhancementState.RAW_ASR_READY,
                CaptionEnhancementState.CLOUD_PENDING,
                CaptionEnhancementState.CLOUD_VALIDATING,
                CaptionEnhancementState.CLOUD_APPLIED,
            ),
            states,
        )
        assertEquals(CaptionResultSource.CLOUD_AI, outcome.source)
        assertEquals(listOf("cloud-a", "cloud-b"), outcome.captions.map { it.english })
        assertEquals(0, translator.translateCalls)
        assertEquals(1, provider.calls)
    }

    @Test
    fun recoverableProviderFailuresUseLocalFallbackExactlyOnce() = runBlocking {
        val recoverableKinds = listOf(
            CaptionEnhancementErrorKind.OFFLINE,
            CaptionEnhancementErrorKind.CONNECTION,
            CaptionEnhancementErrorKind.TIMEOUT,
            CaptionEnhancementErrorKind.RETRYABLE_SERVER,
        )

        recoverableKinds.forEach { kind ->
            val provider = RecordingProvider { throw CaptionEnhancementProviderException(kind, "Provider unavailable.") }
            val translator = RecordingTranslator()

            val outcome = coordinator(provider, translator).enhance("job-$kind", rawCues())

            assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
            assertEquals(CaptionEnhancementState.LOCAL_FALLBACK_APPLIED, outcome.state)
            assertEquals(kind, outcome.errorKind)
            assertEquals(2, translator.translateCalls)
            assertEquals(1, translator.prepareCalls)
        }
    }

    @Test
    fun invalidCloudSchemaFallsBackUsingOriginalWhisperEnglish() = runBlocking {
        val provider = RecordingProvider { request -> validResponse(request).copy(schemaVersion = "invalid") }
        val translator = RecordingTranslator()
        val source = rawCues()

        val outcome = coordinator(provider, translator).enhance("job-invalid", source)

        assertEquals(source.map { it.english }, translator.inputs)
        assertEquals(source.map { it.english }, outcome.captions.map { it.english })
        assertEquals(CaptionResultSource.LOCAL_FALLBACK, outcome.source)
        assertEquals(CaptionEnhancementErrorKind.INVALID_RESPONSE, outcome.errorKind)
    }

    @Test
    fun authenticationFailureDoesNotSilentlyDowngradeToLocalTranslation() {
        val provider = RecordingProvider {
            throw CaptionEnhancementProviderException(
                CaptionEnhancementErrorKind.AUTHENTICATION,
                "Provider authentication failed.",
            )
        }
        val translator = RecordingTranslator()

        assertThrows(CaptionEnhancementProviderException::class.java) {
            runBlocking { coordinator(provider, translator).enhance("job-auth", rawCues()) }
        }
        assertEquals(0, translator.translateCalls)
    }

    @Test
    fun cancellationEmitsCancelledAndDoesNotStartFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = RecordingProvider {
            entered.complete(Unit)
            release.await()
            error("unreachable")
        }
        val translator = RecordingTranslator()
        val states = mutableListOf<CaptionEnhancementState>()
        var cancelled = false
        val job = launch {
            try {
                coordinator(provider, translator).enhance("job-cancel", rawCues(), states::add)
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        entered.await()

        job.cancelAndJoin()

        assertTrue(cancelled)
        assertEquals(CaptionEnhancementState.CANCELLED, states.last())
        assertEquals(0, translator.translateCalls)
    }

    @Test
    fun localFallbackFailureReturnsRecoverableErrorWithoutPartialCaptionBatch() = runBlocking {
        val source = rawCues()
        val provider = RecordingProvider {
            throw CaptionEnhancementProviderException(CaptionEnhancementErrorKind.OFFLINE, "Offline.")
        }
        val translator = RecordingTranslator(failOnInput = source[1].english)

        val error = try {
            coordinator(provider, translator).enhance("job-local-fail", source)
            throw AssertionError("Expected CaptionEnhancementException")
        } catch (failure: CaptionEnhancementException) {
            failure
        }

        assertEquals(CaptionEnhancementErrorKind.LOCAL_TRANSLATION, error.kind)
        assertTrue(error.recoverable)
        assertEquals(2, translator.translateCalls)
        assertTrue(source.all { it.chinese.isBlank() })
    }

    private fun coordinator(
        provider: CaptionEnhancementProvider,
        translator: RecordingTranslator,
    ) = CaptionEnhancementCoordinator(
        provider = provider,
        localTranslation = TranslationModule(translator),
        validator = CaptionEnhancementResponseValidator(),
    )

    private fun validResponse(request: CaptionEnhancementRequest) = CaptionEnhancementResponse(
        schemaVersion = request.schemaVersion,
        jobId = request.jobId,
        processingVersion = "provider-v1",
        cues = request.cues.mapIndexed { index, cue ->
            CaptionEnhancementResponseCue(
                id = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                correctedEnglish = if (index == 0) "cloud-a" else "cloud-b",
                chinese = if (index == 0) "translation-a" else "translation-b",
            )
        },
    )

    private class RecordingProvider(
        private val action: suspend (CaptionEnhancementRequest) -> CaptionEnhancementResponse,
    ) : CaptionEnhancementProvider {
        var calls = 0

        override suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse {
            calls += 1
            return action(request)
        }
    }

    private class RecordingTranslator(
        private val failOnInput: String? = null,
    ) : LocalTranslator {
        var prepareCalls = 0
        var translateCalls = 0
        val inputs = mutableListOf<String>()

        override suspend fun prepareBatch() {
            prepareCalls += 1
        }

        override suspend fun translateEnglishToChinese(text: String): String {
            translateCalls += 1
            inputs += text
            if (text == failOnInput) throw IllegalStateException("local translation unavailable")
            return "local:$text"
        }
    }
}
