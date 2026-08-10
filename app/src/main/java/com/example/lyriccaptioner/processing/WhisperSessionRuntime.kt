package com.example.lyriccaptioner.processing

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun interface WhisperMonotonicClock {
    fun nowMs(): Long
}

fun interface WhisperSessionScheduledTask {
    fun cancel()
}

fun interface WhisperSessionScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): WhisperSessionScheduledTask
}

data class WhisperSessionSnapshot(
    val contextHandle: Long?,
    val modelIdentity: WhisperModelIdentity?,
    val active: Boolean,
    val pendingInvalidation: Boolean,
    val idleSinceMs: Long?,
    val closed: Boolean,
)

/**
 * Owns one process-local Whisper context at a time. Task-specific audio, cancellation, and
 * transcription results are deliberately kept on the call stack and are never cached here.
 */
class WhisperSessionRuntime(
    private val nativeClient: WhisperSessionNativeClient,
    private val clock: WhisperMonotonicClock = SystemWhisperMonotonicClock,
    private val scheduler: WhisperSessionScheduler = SharedWhisperSessionScheduler,
    private val nativeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val observer: WhisperSessionObserver = WhisperSessionObserver {},
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) : AutoCloseable {
    private val inferenceMutex = Mutex()
    private val stateLock = Any()

    private var cachedContext: CachedContext? = null
    private var activeIdentity: WhisperModelIdentity? = null
    private var activeHandle: Long? = null
    private var pendingInvalidation = false
    private var idleSinceMs: Long? = null
    private var expirationGeneration = 0L
    private var expirationTask: WhisperSessionScheduledTask? = null
    private var closed = false

    init {
        require(idleTimeoutMs > 0L) { "idleTimeoutMs must be positive" }
    }

    suspend fun transcribe(
        modelPath: String,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken = WhisperCancellationToken { false },
    ): List<WhisperSegment> {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0) { "channels must be positive" }
        if (!nativeClient.isAvailable) {
            throw WhisperJniUnavailableException(
                "Whisper native library is not available. Build with enableWhisperNative before using local ASR.",
            )
        }

        val totalStartedAt = clock.nowMs()
        val identity = withContext(nativeDispatcher) { identifyModel(modelPath) }
        announceRequestedModel(identity)

        return inferenceMutex.withLock {
            currentCoroutineContext().ensureActive()
            beginTask(identity)

            var handle = 0L
            var reused = false
            var loadMs = 0L
            var inferenceMs = 0L
            var segmentCount = 0
            var failure: Throwable? = null
            var result: List<WhisperSegment>? = null
            var cancellationWatcher: Job? = null

            try {
                val prepared = prepareContext(identity)
                handle = prepared.handle
                reused = prepared.reused
                loadMs = prepared.loadMs
                markInferenceActive(handle)

                val callerJob = currentCoroutineContext()[Job]
                cancellationWatcher = callerJob?.let { job ->
                    CoroutineScope(job + Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            awaitCancellation()
                        } finally {
                            if (!job.isActive) requestAbortSafely(handle)
                        }
                    }
                }

                val inferenceStartedAt = clock.nowMs()
                result = withContext(nativeDispatcher) {
                    nativeClient.transcribe(
                        contextHandle = handle,
                        audioPath = audioPath,
                        sampleRate = sampleRate,
                        channels = channels,
                        cancellationToken = WhisperCancellationToken {
                            cancellationToken.isCancelled() || callerJob?.isActive == false
                        },
                    )
                }
                inferenceMs = elapsedSince(inferenceStartedAt)
                currentCoroutineContext().ensureActive()
                if (cancellationToken.isCancelled()) {
                    throw CancellationException("Whisper transcription was cancelled")
                }
                segmentCount = result.size
            } catch (error: Throwable) {
                failure = error
            } finally {
                withContext(NonCancellable) {
                    cancellationWatcher?.cancelAndJoin()
                }
            }

            val cancelled =
                failure is CancellationException ||
                    cancellationToken.isCancelled() ||
                    currentCoroutineContext()[Job]?.isActive == false
            val releaseFailure = finishTask(handle, reusable = failure == null && !cancelled)
            if (releaseFailure != null) {
                if (failure == null) failure = releaseFailure else failure.addSuppressed(releaseFailure)
            }

            notifyObserver(
                WhisperSessionRunMetrics(
                    contextHandle = handle,
                    reusedContext = reused,
                    contextLoadMs = loadMs,
                    inferenceMs = inferenceMs,
                    totalMs = elapsedSince(totalStartedAt),
                    segmentCount = segmentCount,
                    cancelled = cancelled,
                ),
            )

            failure?.let { throw it }
            checkNotNull(result)
        }
    }

    /** Immediately releases an idle context, or defers release until active native work exits. */
    fun onCriticalMemoryPressure() {
        val detached = synchronized(stateLock) {
            if (activeIdentity != null) {
                pendingInvalidation = true
                null
            } else {
                detachCachedContextLocked()
            }
        }
        detached?.let { nativeClient.freeContext(it.handle) }
    }

    fun snapshot(): WhisperSessionSnapshot = synchronized(stateLock) {
        WhisperSessionSnapshot(
            contextHandle = cachedContext?.handle,
            modelIdentity = cachedContext?.identity,
            active = activeIdentity != null,
            pendingInvalidation = pendingInvalidation,
            idleSinceMs = idleSinceMs,
            closed = closed,
        )
    }

    override fun close() {
        var detached: CachedContext? = null
        var abortHandle: Long? = null
        synchronized(stateLock) {
            if (closed) return
            closed = true
            if (activeIdentity != null) {
                pendingInvalidation = true
                abortHandle = activeHandle
            } else {
                detached = detachCachedContextLocked()
            }
        }
        abortHandle?.let(::requestAbortSafely)
        detached?.let { nativeClient.freeContext(it.handle) }
    }

    private fun announceRequestedModel(identity: WhisperModelIdentity) {
        val detached = synchronized(stateLock) {
            check(!closed) { "Whisper session runtime is closed" }
            val currentIdentity = activeIdentity ?: cachedContext?.identity
            if (currentIdentity == null || currentIdentity == identity) {
                null
            } else if (activeIdentity != null) {
                pendingInvalidation = true
                null
            } else {
                detachCachedContextLocked()
            }
        }
        detached?.let { nativeClient.freeContext(it.handle) }
    }

    private fun beginTask(identity: WhisperModelIdentity) {
        synchronized(stateLock) {
            check(!closed) { "Whisper session runtime is closed" }
            check(activeIdentity == null) { "Whisper session runtime admitted concurrent native work" }
            activeIdentity = identity
            expirationGeneration += 1
            expirationTask?.cancel()
            expirationTask = null
        }
    }

    private fun prepareContext(identity: WhisperModelIdentity): PreparedContext {
        var detached: CachedContext? = null
        val reusable = synchronized(stateLock) {
            val existing = cachedContext
            val expired = idleSinceMs?.let { elapsedSince(it) >= idleTimeoutMs } == true
            if (existing != null && (existing.identity != identity || expired)) {
                detached = detachCachedContextLocked()
                null
            } else {
                existing
            }
        }
        detached?.let { nativeClient.freeContext(it.handle) }
        if (reusable != null) return PreparedContext(reusable.handle, reused = true, loadMs = 0L)

        val loadStartedAt = clock.nowMs()
        val newHandle = nativeClient.createContext(identity.canonicalPath)
        if (newHandle == 0L) {
            throw IllegalStateException("Whisper native context creation returned an invalid handle")
        }
        val loadMs = elapsedSince(loadStartedAt)

        val rejectNewHandle = synchronized(stateLock) {
            if (closed) {
                true
            } else {
                cachedContext = CachedContext(newHandle, identity)
                idleSinceMs = null
                false
            }
        }
        if (rejectNewHandle) {
            nativeClient.freeContext(newHandle)
            throw IllegalStateException("Whisper session runtime is closed")
        }
        return PreparedContext(newHandle, reused = false, loadMs = loadMs)
    }

    private fun markInferenceActive(handle: Long) {
        synchronized(stateLock) {
            check(cachedContext?.handle == handle) { "Whisper context is no longer owned by this runtime" }
            activeHandle = handle
        }
    }

    private fun finishTask(handle: Long, reusable: Boolean): Throwable? {
        var detached: CachedContext? = null
        var shouldScheduleExpiry = false
        synchronized(stateLock) {
            activeHandle = null
            activeIdentity = null
            val ownsHandle = handle != 0L && cachedContext?.handle == handle
            if (ownsHandle && reusable && !pendingInvalidation && !closed) {
                idleSinceMs = clock.nowMs()
                shouldScheduleExpiry = true
            } else if (ownsHandle) {
                detached = detachCachedContextLocked()
            }
            pendingInvalidation = false
        }

        var releaseFailure = runCatching { detached?.let { nativeClient.freeContext(it.handle) } }.exceptionOrNull()
        if (shouldScheduleExpiry) {
            val scheduleFailure = runCatching { scheduleIdleExpiry(handle) }.exceptionOrNull()
            if (scheduleFailure != null) {
                val fallback = synchronized(stateLock) {
                    if (cachedContext?.handle == handle && activeIdentity == null) detachCachedContextLocked() else null
                }
                val fallbackFailure = runCatching { fallback?.let { nativeClient.freeContext(it.handle) } }.exceptionOrNull()
                fallbackFailure?.let(scheduleFailure::addSuppressed)
                releaseFailure = scheduleFailure
            }
        }
        return releaseFailure
    }

    private fun scheduleIdleExpiry(handle: Long) {
        val generation: Long
        synchronized(stateLock) {
            if (cachedContext?.handle != handle || activeIdentity != null || closed) return
            expirationGeneration += 1
            generation = expirationGeneration
        }
        val task = scheduler.schedule(idleTimeoutMs) { expireIdleContext(handle, generation) }
        synchronized(stateLock) {
            if (
                cachedContext?.handle == handle &&
                activeIdentity == null &&
                expirationGeneration == generation &&
                !closed
            ) {
                expirationTask = task
            } else {
                task.cancel()
            }
        }
    }

    private fun expireIdleContext(handle: Long, generation: Long) {
        var detached: CachedContext? = null
        var remainingMs: Long? = null
        synchronized(stateLock) {
            if (
                cachedContext?.handle != handle ||
                activeIdentity != null ||
                expirationGeneration != generation ||
                closed
            ) return
            val idleFor = idleSinceMs?.let(::elapsedSince) ?: return
            if (idleFor >= idleTimeoutMs) {
                detached = detachCachedContextLocked()
            } else {
                remainingMs = idleTimeoutMs - idleFor
            }
        }
        detached?.let { runCatching { nativeClient.freeContext(it.handle) } }
        remainingMs?.let { delay ->
            val task = scheduler.schedule(delay) { expireIdleContext(handle, generation) }
            synchronized(stateLock) {
                if (
                    cachedContext?.handle == handle &&
                    activeIdentity == null &&
                    expirationGeneration == generation &&
                    !closed
                ) expirationTask = task else task.cancel()
            }
        }
    }

    private fun detachCachedContextLocked(): CachedContext? {
        val detached = cachedContext
        cachedContext = null
        idleSinceMs = null
        expirationGeneration += 1
        expirationTask?.cancel()
        expirationTask = null
        return detached
    }

    private fun requestAbortSafely(handle: Long) {
        runCatching { nativeClient.requestAbort(handle) }
    }

    private fun notifyObserver(metrics: WhisperSessionRunMetrics) {
        runCatching { observer.onRunCompleted(metrics) }
    }

    private fun elapsedSince(startMs: Long): Long = (clock.nowMs() - startMs).coerceAtLeast(0L)

    private fun identifyModel(modelPath: String): WhisperModelIdentity {
        val file = File(modelPath).canonicalFile
        require(file.isFile) { "Whisper model does not exist: ${file.path}" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_DIGEST_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return WhisperModelIdentity(
            canonicalPath = file.path,
            sizeBytes = file.length(),
            sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
        )
    }

    private data class CachedContext(val handle: Long, val identity: WhisperModelIdentity)
    private data class PreparedContext(val handle: Long, val reused: Boolean, val loadMs: Long)

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 5 * 60 * 1_000L
        private const val DEFAULT_DIGEST_BUFFER_SIZE = 64 * 1_024
    }
}

private object SystemWhisperMonotonicClock : WhisperMonotonicClock {
    override fun nowMs(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
}

private object SharedWhisperSessionScheduler : WhisperSessionScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "whisper-session-expiry").apply { isDaemon = true }
    }

    override fun schedule(delayMs: Long, action: () -> Unit): WhisperSessionScheduledTask {
        val future = executor.schedule(action, delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        return WhisperSessionScheduledTask { future.cancel(false) }
    }
}
