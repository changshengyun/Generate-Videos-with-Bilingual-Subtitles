package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.LocalTranslator
import com.example.lyriccaptioner.processing.TranslationModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    fun unknownProviderFailureDoesNotSilentlyDowngradeToLocalTranslation() {
        val provider = RecordingProvider { throw IllegalStateException("unexpected provider failure") }
        val translator = RecordingTranslator()

        val error = assertThrows(CaptionEnhancementProviderException::class.java) {
            runBlocking { coordinator(provider, translator).enhance("job-unknown", rawCues()) }
        }

        assertEquals(CaptionEnhancementErrorKind.UNKNOWN, error.kind)
        assertEquals(0, translator.translateCalls)
        assertEquals(1, provider.calls)
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
    fun cancellationWhileWorkerPreprocessingIsQueuedEmitsCancelledWithoutProviderOrFallback() {
        val workerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "coordinator-preprocessing-worker")
        }
        val workerDispatcher = workerExecutor.asCoroutineDispatcher()
        val blockerEntered = CountDownLatch(1)
        val releaseBlocker = CountDownLatch(1)
        workerExecutor.execute {
            blockerEntered.countDown()
            releaseBlocker.await()
        }

        try {
            assertTrue(blockerEntered.await(5, TimeUnit.SECONDS))
            runBlocking {
                val rawReady = CompletableDeferred<Unit>()
                val provider = RecordingProvider { request -> validResponse(request) }
                val translator = RecordingTranslator()
                val states = mutableListOf<CaptionEnhancementState>()
                var callerReceivedCancellation = false
                val job = launch {
                    try {
                        coordinator(
                            provider = provider,
                            translator = translator,
                            workerDispatcher = workerDispatcher,
                        ).enhance("job-cancel-preprocessing", largeCueBatch(4_000), onStateChanged = { state ->
                            states += state
                            if (state == CaptionEnhancementState.RAW_ASR_READY) {
                                rawReady.complete(Unit)
                            }
                        })
                    } catch (_: CancellationException) {
                        callerReceivedCancellation = true
                    }
                }
                rawReady.await()

                job.cancel()
                releaseBlocker.countDown()
                job.join()

                assertTrue(callerReceivedCancellation)
                assertEquals(
                    listOf(
                        CaptionEnhancementState.RAW_ASR_READY,
                        CaptionEnhancementState.CANCELLED,
                    ),
                    states,
                )
                assertEquals(0, provider.calls)
                assertEquals(0, translator.translateCalls)
                assertEquals(0, translator.prepareCalls)
            }
        } finally {
            releaseBlocker.countDown()
            workerDispatcher.close()
        }
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

    @Test
    fun largeBatchProcessingUsesWorkerWhileCallerStatesAndHeartbeatRemainResponsive() {
        val callerDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "coordinator-test-caller")
        }.asCoroutineDispatcher()
        val workerDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "coordinator-test-worker")
        }.asCoroutineDispatcher()

        try {
            runBlocking(callerDispatcher) {
                val source = largeCueBatch(4_000)
                val providerEntered = CompletableDeferred<Unit>()
                val heartbeatRan = AtomicBoolean(false)
                var providerObservedHeartbeat = false
                var providerThread = ""
                val stateThreads = mutableListOf<String>()
                val provider = RecordingProvider { request ->
                    providerThread = Thread.currentThread().name
                    providerEntered.complete(Unit)
                    val deadline = System.nanoTime() + 2_000_000_000L
                    while (!heartbeatRan.get() && System.nanoTime() < deadline) {
                        Thread.yield()
                    }
                    providerObservedHeartbeat = heartbeatRan.get()
                    validResponse(request)
                }
                val heartbeat = launch {
                    providerEntered.await()
                    heartbeatRan.set(true)
                }

                val outcome = coordinator(
                    provider = provider,
                    translator = RecordingTranslator(),
                    workerDispatcher = workerDispatcher,
                ).enhance("job-large-batch", source, onStateChanged = {
                    stateThreads += Thread.currentThread().name
                })
                heartbeat.join()

                assertEquals(4_000, outcome.captions.size)
                assertTrue(providerThread.startsWith("coordinator-test-worker"))
                assertTrue("Caller heartbeat must run during worker processing", providerObservedHeartbeat)
                assertTrue(stateThreads.isNotEmpty())
                assertTrue(stateThreads.all { it.startsWith("coordinator-test-caller") })
            }
        } finally {
            callerDispatcher.close()
            workerDispatcher.close()
        }
    }

    private fun coordinator(
        provider: CaptionEnhancementProvider,
        translator: RecordingTranslator,
        workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) = CaptionEnhancementCoordinator(
        provider = provider,
        localTranslation = TranslationModule(translator),
        validator = CaptionEnhancementResponseValidator(),
        workerDispatcher = workerDispatcher,
    )

    private fun largeCueBatch(size: Int): List<CaptionCue> = List(size) { index ->
        cue(
            id = "cue-$index",
            english = "line $index",
            startMs = index * 1_000L,
            endMs = (index + 1) * 1_000L,
        )
    }

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
