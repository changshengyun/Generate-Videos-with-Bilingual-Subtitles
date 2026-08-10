package com.example.lyriccaptioner.processing

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperSessionRuntimeTest {
    @Test
    fun coldTaskCreatesOneContextThenEntersIdleCache() = withModels { model, _ ->
        val fixture = Fixture()
        val segments = runBlocking { fixture.runtime.transcribe(model.path, "audio-a", 16_000, 1) }

        assertEquals(listOf("audio-a"), segments.map { it.text })
        assertEquals(1, fixture.native.createCount.get())
        assertEquals(0, fixture.native.freeHandles.size)
        assertFalse(fixture.runtime.snapshot().active)
        assertEquals(1L, fixture.runtime.snapshot().contextHandle)
        assertEquals(0L, fixture.runtime.snapshot().idleSinceMs)
    }

    @Test
    fun secondTaskWithinThreeMinutesReusesSameContext() = withModels { model, _ ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "audio-a", 16_000, 1) }
        fixture.time.advanceBy(3 * 60 * 1_000L)
        runBlocking { fixture.runtime.transcribe(model.path, "audio-b", 16_000, 1) }

        assertEquals(1, fixture.native.createCount.get())
        assertEquals(listOf(1L, 1L), fixture.native.transcribeHandles)
        assertTrue(fixture.metrics.first().reusedContext.not())
        assertTrue(fixture.metrics.last().reusedContext)
    }

    @Test
    fun fiveMinuteExpiryFreesThenNextTaskCreatesNewContext() = withModels { model, _ ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "audio-a", 16_000, 1) }

        fixture.time.advanceBy(WhisperSessionRuntime.DEFAULT_IDLE_TIMEOUT_MS)
        assertEquals(listOf(1L), fixture.native.freeHandles)
        assertNull(fixture.runtime.snapshot().contextHandle)

        runBlocking { fixture.runtime.transcribe(model.path, "audio-b", 16_000, 1) }
        assertEquals(2, fixture.native.createCount.get())
        assertEquals(listOf(1L, 2L), fixture.native.transcribeHandles)
    }

    @Test
    fun concurrentRequestsAreStrictlySerialized() = withModels { model, _ ->
        val fixture = Fixture()
        fixture.native.blockNextTranscription()
        runBlocking {
            val first = async(Dispatchers.Default) { fixture.runtime.transcribe(model.path, "first", 16_000, 1) }
            assertTrue(fixture.native.awaitBlockedEntry())
            val second = async(Dispatchers.Default) { fixture.runtime.transcribe(model.path, "second", 16_000, 1) }
            delay(50)
            assertEquals(1, fixture.native.transcribeAudio.size)
            fixture.native.releaseBlockedTranscription()
            first.await()
            second.await()
        }

        assertEquals(listOf("first", "second"), fixture.native.transcribeAudio)
        assertEquals(1, fixture.native.maxConcurrent.get())
        assertEquals(1, fixture.native.createCount.get())
    }

    @Test
    fun taskAudioCancellationAndResultsDoNotPolluteNextTask() = withModels { model, _ ->
        val fixture = Fixture()
        val firstToken = WhisperCancellationToken { false }
        val secondToken = WhisperCancellationToken { false }
        val first = runBlocking {
            fixture.runtime.transcribe(model.path, "first-audio", 16_000, 1, firstToken)
        }
        val second = runBlocking {
            fixture.runtime.transcribe(model.path, "second-audio", 16_000, 1, secondToken)
        }

        assertEquals("first-audio", first.single().text)
        assertEquals("second-audio", second.single().text)
        assertEquals(listOf("first-audio", "second-audio"), fixture.native.transcribeAudio)
        assertNotEquals(firstToken, secondToken)
        assertEquals(1L, fixture.runtime.snapshot().contextHandle)
    }

    @Test
    fun pathSizeAndShaChangesEachInvalidateOldContext() = withModels { model, otherPath ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "one", 16_000, 1) }
        runBlocking { fixture.runtime.transcribe(otherPath.path, "two", 16_000, 1) }

        otherPath.writeText("different-size")
        runBlocking { fixture.runtime.transcribe(otherPath.path, "three", 16_000, 1) }

        otherPath.writeText("another--value")
        runBlocking { fixture.runtime.transcribe(otherPath.path, "four", 16_000, 1) }

        assertEquals(4, fixture.native.createCount.get())
        assertEquals(listOf(1L, 2L, 3L), fixture.native.freeHandles)
        assertEquals(listOf(1L, 2L, 3L, 4L), fixture.native.transcribeHandles)
    }

    @Test
    fun activeModelSwitchDefersFreeUntilNativeInferenceExits() = withModels { model, other ->
        val fixture = Fixture()
        fixture.native.blockNextTranscription()
        runBlocking {
            val first = async(Dispatchers.Default) { fixture.runtime.transcribe(model.path, "first", 16_000, 1) }
            assertTrue(fixture.native.awaitBlockedEntry())
            val second = async(Dispatchers.Default) { fixture.runtime.transcribe(other.path, "second", 16_000, 1) }
            eventually { fixture.runtime.snapshot().pendingInvalidation }
            assertTrue(fixture.native.freeHandles.isEmpty())
            fixture.native.releaseBlockedTranscription()
            first.await()
            second.await()
        }

        assertTrue(fixture.native.events.indexOf("exit:1") < fixture.native.events.indexOf("free:1"))
        assertEquals(listOf(1L), fixture.native.freeHandles)
        assertEquals(listOf(1L, 2L), fixture.native.transcribeHandles)
    }

    @Test
    fun coroutineCancellationAbortsWaitsForExitFreesAndNextTaskRebuilds() = withModels { model, _ ->
        val fixture = Fixture()
        fixture.native.blockUntilAbort()
        runBlocking {
            val job = launch(Dispatchers.Default) { fixture.runtime.transcribe(model.path, "cancelled", 16_000, 1) }
            assertTrue(fixture.native.awaitBlockedEntry())
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }

        val abortIndex = fixture.native.events.indexOf("abort:1")
        val exitIndex = fixture.native.events.indexOf("exit:1")
        val freeIndex = fixture.native.events.indexOf("free:1")
        assertTrue(abortIndex >= 0)
        assertTrue(abortIndex < exitIndex)
        assertTrue(exitIndex < freeIndex)
        assertNull(fixture.runtime.snapshot().contextHandle)

        fixture.native.resumeNormally()
        runBlocking { fixture.runtime.transcribe(model.path, "recovered", 16_000, 1) }
        assertEquals(listOf(1L, 2L), fixture.native.transcribeHandles)
    }

    @Test
    fun criticalMemoryPressureReleasesIdleAndDefersActiveRelease() = withModels { model, _ ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "idle", 16_000, 1) }
        fixture.runtime.onCriticalMemoryPressure()
        assertEquals(listOf(1L), fixture.native.freeHandles)

        fixture.native.blockNextTranscription()
        runBlocking {
            val active = async(Dispatchers.Default) { fixture.runtime.transcribe(model.path, "active", 16_000, 1) }
            assertTrue(fixture.native.awaitBlockedEntry())
            fixture.runtime.onCriticalMemoryPressure()
            assertEquals(listOf(1L), fixture.native.freeHandles)
            assertTrue(fixture.runtime.snapshot().pendingInvalidation)
            fixture.native.releaseBlockedTranscription()
            active.await()
        }
        assertTrue(fixture.native.events.indexOf("exit:2") < fixture.native.events.indexOf("free:2"))
        assertNull(fixture.runtime.snapshot().contextHandle)
    }

    @Test
    fun createAndTranscribeFailuresLeakNothingAndLaterRecover() = withModels { model, _ ->
        val fixture = Fixture()
        fixture.native.createFailure = IllegalStateException("load failed")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.runtime.transcribe(model.path, "load", 16_000, 1) }
        }
        assertNull(fixture.runtime.snapshot().contextHandle)

        fixture.native.createFailure = null
        fixture.native.transcribeFailure = IllegalStateException("inference failed")
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.runtime.transcribe(model.path, "bad", 16_000, 1) }
        }
        assertEquals(listOf(2L), fixture.native.freeHandles)
        assertNull(fixture.runtime.snapshot().contextHandle)

        fixture.native.transcribeFailure = null
        val recovered = runBlocking { fixture.runtime.transcribe(model.path, "good", 16_000, 1) }
        assertEquals("good", recovered.single().text)
        assertEquals(3, fixture.native.createCount.get())
    }

    @Test
    fun freeFailureRemovesHandleFromCacheAndLaterTaskRecovers() = withModels { model, _ ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "before-free", 16_000, 1) }
        fixture.native.freeFailure = IllegalStateException("free failed")

        assertThrows(IllegalStateException::class.java) {
            fixture.runtime.onCriticalMemoryPressure()
        }
        assertNull(fixture.runtime.snapshot().contextHandle)

        fixture.native.freeFailure = null
        runBlocking { fixture.runtime.transcribe(model.path, "after-free", 16_000, 1) }
        assertEquals(listOf(1L, 2L), fixture.native.transcribeHandles)
        assertEquals(2, fixture.native.createCount.get())
    }

    @Test
    fun closeIsIdempotentAndNeverDoubleFrees() = withModels { model, _ ->
        val fixture = Fixture()
        runBlocking { fixture.runtime.transcribe(model.path, "audio", 16_000, 1) }

        fixture.runtime.close()
        fixture.runtime.close()

        assertEquals(listOf(1L), fixture.native.freeHandles)
        assertTrue(fixture.runtime.snapshot().closed)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.runtime.transcribe(model.path, "later", 16_000, 1) }
        }
    }

    @Test
    fun aNewRuntimeStartsWithEmptyCacheAndCannotReuseAnotherRuntimeHandle() = withModels { model, _ ->
        val time = FakeTime()
        val native = FakeNative(time)
        val first = runtime(native, time)
        runBlocking { first.transcribe(model.path, "one", 16_000, 1) }

        val second = runtime(native, time)
        assertNull(second.snapshot().contextHandle)
        runBlocking { second.transcribe(model.path, "two", 16_000, 1) }

        assertEquals(2, native.createCount.get())
        assertEquals(listOf(1L, 2L), native.transcribeHandles)
        first.close()
        second.close()
    }

    @Test
    fun coldAndHotMetricsDistinguishLoadFromInference() = withModels { model, _ ->
        val fixture = Fixture()
        fixture.native.createDurationMs = 25
        fixture.native.transcribeDurationMs = 40
        runBlocking { fixture.runtime.transcribe(model.path, "cold", 16_000, 1) }
        runBlocking { fixture.runtime.transcribe(model.path, "hot", 16_000, 1) }

        assertEquals(25L, fixture.metrics[0].contextLoadMs)
        assertEquals(0L, fixture.metrics[1].contextLoadMs)
        assertEquals(40L, fixture.metrics[0].inferenceMs)
        assertEquals(40L, fixture.metrics[1].inferenceMs)
        assertFalse(fixture.metrics[0].reusedContext)
        assertTrue(fixture.metrics[1].reusedContext)
    }

    private class Fixture {
        val time = FakeTime()
        val native = FakeNative(time)
        val metrics = Collections.synchronizedList(mutableListOf<WhisperSessionRunMetrics>())
        val runtime = runtime(native, time, metrics)
    }

    private class FakeTime : WhisperMonotonicClock, WhisperSessionScheduler {
        private var now = 0L
        private val scheduled = mutableListOf<Scheduled>()

        override fun nowMs(): Long = synchronized(this) { now }

        override fun schedule(delayMs: Long, action: () -> Unit): WhisperSessionScheduledTask {
            val item = synchronized(this) {
                Scheduled(now + delayMs, action).also(scheduled::add)
            }
            return WhisperSessionScheduledTask { synchronized(this) { item.cancelled = true } }
        }

        fun advanceBy(deltaMs: Long) {
            val target = synchronized(this) { now + deltaMs }
            while (true) {
                val next = synchronized(this) {
                    scheduled.filterNot { it.cancelled }.minByOrNull { it.dueMs }
                        ?.takeIf { it.dueMs <= target }
                        ?.also {
                            scheduled.remove(it)
                            now = it.dueMs
                        }
                } ?: break
                next.action()
            }
            synchronized(this) { now = target }
        }

        fun elapse(deltaMs: Long) = synchronized(this) { now += deltaMs }

        private data class Scheduled(
            val dueMs: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false,
        )
    }

    private class FakeNative(private val time: FakeTime) : WhisperSessionNativeClient {
        override val isAvailable = true
        val createCount = AtomicInteger()
        val freeHandles = Collections.synchronizedList(mutableListOf<Long>())
        val transcribeHandles = Collections.synchronizedList(mutableListOf<Long>())
        val transcribeAudio = Collections.synchronizedList(mutableListOf<String>())
        val events = Collections.synchronizedList(mutableListOf<String>())
        val maxConcurrent = AtomicInteger()
        var createFailure: Throwable? = null
        var transcribeFailure: Throwable? = null
        var freeFailure: Throwable? = null
        var createDurationMs = 0L
        var transcribeDurationMs = 0L

        private val concurrent = AtomicInteger()
        private val abortRequested = AtomicBoolean()
        @Volatile private var entered = CountDownLatch(1)
        @Volatile private var release = CountDownLatch(0)
        @Volatile private var waitForAbort = false

        override fun createContext(modelPath: String): Long {
            val count = createCount.incrementAndGet()
            createFailure?.let { throw it }
            time.elapse(createDurationMs)
            events += "create:$count"
            return count.toLong()
        }

        override fun transcribe(
            contextHandle: Long,
            audioPath: String,
            sampleRate: Int,
            channels: Int,
            cancellationToken: WhisperCancellationToken,
        ): List<WhisperSegment> {
            transcribeHandles += contextHandle
            transcribeAudio += audioPath
            events += "enter:$contextHandle"
            val active = concurrent.incrementAndGet()
            maxConcurrent.updateAndGet { previous -> maxOf(previous, active) }
            entered.countDown()
            try {
                while (waitForAbort && !abortRequested.get() && !cancellationToken.isCancelled()) {
                    Thread.sleep(2)
                }
                if (waitForAbort || cancellationToken.isCancelled()) {
                    events += "exit:$contextHandle"
                    throw CancellationException("native cancelled")
                }
                release.await(5, TimeUnit.SECONDS)
                transcribeFailure?.let { throw it }
                time.elapse(transcribeDurationMs)
                events += "exit:$contextHandle"
                return listOf(WhisperSegment(0, 100, audioPath, 0.9f))
            } finally {
                concurrent.decrementAndGet()
            }
        }

        override fun requestAbort(contextHandle: Long) {
            events += "abort:$contextHandle"
            abortRequested.set(true)
        }

        override fun freeContext(contextHandle: Long) {
            events += "free:$contextHandle"
            freeHandles += contextHandle
            freeFailure?.let { throw it }
        }

        fun blockNextTranscription() {
            entered = CountDownLatch(1)
            release = CountDownLatch(1)
            waitForAbort = false
            abortRequested.set(false)
        }

        fun blockUntilAbort() {
            entered = CountDownLatch(1)
            release = CountDownLatch(0)
            waitForAbort = true
            abortRequested.set(false)
        }

        fun awaitBlockedEntry(): Boolean = entered.await(5, TimeUnit.SECONDS)

        fun releaseBlockedTranscription() {
            release.countDown()
        }

        fun resumeNormally() {
            entered = CountDownLatch(1)
            release = CountDownLatch(0)
            waitForAbort = false
            abortRequested.set(false)
        }
    }

    companion object {
        private fun runtime(
            native: FakeNative,
            time: FakeTime,
            metrics: MutableList<WhisperSessionRunMetrics> = mutableListOf(),
        ) = WhisperSessionRuntime(
            nativeClient = native,
            clock = time,
            scheduler = time,
            nativeDispatcher = Dispatchers.Default,
            observer = WhisperSessionObserver(metrics::add),
        )

        private fun withModels(block: (File, File) -> Unit) {
            val directory = Files.createTempDirectory("whisper-session-").toFile()
            val model = File(directory, "model-a.bin").apply { writeText("same-content") }
            val other = File(directory, "model-b.bin").apply { writeText("same-content") }
            try {
                block(model, other)
            } finally {
                directory.deleteRecursively()
            }
        }

        private fun eventually(predicate: () -> Boolean) {
            repeat(100) {
                if (predicate()) return
                Thread.sleep(5)
            }
            assertTrue("condition was not reached", predicate())
        }
    }
}
