package com.example.lyriccaptioner

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.WhisperSessionObserver
import com.example.lyriccaptioner.processing.WhisperSessionRunMetrics
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Test-only physical-device evidence collector for the Whisper session cache.
 *
 * It deliberately records no media path, audio, cue text, or other user content. Cue output is
 * represented only by a one-way digest so two test-owned fixture runs can be compared safely.
 */
class WhisperSessionDeviceDiagnostics(
    context: Context,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) : WhisperSessionObserver {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val completedMetrics = ArrayDeque<WhisperSessionRunMetrics>()

    override fun onRunCompleted(metrics: WhisperSessionRunMetrics) {
        synchronized(lock) {
            completedMetrics.addLast(metrics)
        }
    }

    fun beginRun(
        label: String,
        lifecycle: WhisperSessionLifecycleSnapshot? = null,
    ): WhisperSessionRunStart {
        require(label.isNotBlank()) { "A diagnostics run needs a non-blank label." }
        return WhisperSessionRunStart(
            label = label,
            startedElapsedRealtimeMs = elapsedRealtimeMs(),
            rss = readProcessRss(),
            batteryTemperatureDeciC = readBatteryTemperatureDeciC(),
            lifecycle = lifecycle,
        )
    }

    /** Call only after recognize/cancellation has completely returned to the instrumentation. */
    fun finishRun(
        start: WhisperSessionRunStart,
        cues: List<CaptionCue>,
        failure: Throwable? = null,
        processCrashObserved: Boolean = false,
        lifecycle: WhisperSessionLifecycleSnapshot? = null,
        metrics: WhisperSessionRunMetrics? = pollCompletedMetrics(),
    ): WhisperSessionRunEvidence {
        val finishedAt = elapsedRealtimeMs()
        check(finishedAt >= start.startedElapsedRealtimeMs) {
            "The monotonic diagnostics clock moved backwards."
        }
        val cueEvidence = cueEvidence(cues)
        return WhisperSessionRunEvidence(
            label = start.label,
            wallTotalMs = finishedAt - start.startedElapsedRealtimeMs,
            session = metrics,
            rssBefore = start.rss,
            rssAfter = readProcessRss(),
            temperatureDeciCBefore = start.batteryTemperatureDeciC,
            temperatureDeciCAfter = readBatteryTemperatureDeciC(),
            cueCount = cueEvidence.count,
            cueFingerprint = cueEvidence.fingerprint,
            cueTimestampsValid = cueEvidence.timestampsValid,
            emptyResult = cues.isEmpty(),
            failureObserved = failure != null,
            failureCategory = failure?.javaClass?.simpleName,
            processCrashObserved = processCrashObserved,
            lifecycleBefore = start.lifecycle,
            lifecycleAfter = lifecycle,
        )
    }

    fun pollCompletedMetrics(): WhisperSessionRunMetrics? = synchronized(lock) {
        if (completedMetrics.isEmpty()) null else completedMetrics.removeFirst()
    }

    fun clearCompletedMetrics() {
        synchronized(lock) { completedMetrics.clear() }
    }

    fun writeTo(
        results: Bundle,
        prefix: String,
        evidence: WhisperSessionRunEvidence,
    ) {
        require(prefix.isNotBlank()) { "A diagnostics Bundle prefix is required." }
        results.putString("${prefix}_label", evidence.label)
        results.putLong("${prefix}_wall_total_ms", evidence.wallTotalMs)
        evidence.session?.let { session ->
            results.putString("${prefix}_handle_token", session.contextHandle.toString())
            results.putBoolean("${prefix}_context_reused", session.reusedContext)
            results.putLong("${prefix}_context_load_ms", session.contextLoadMs)
            results.putLong("${prefix}_inference_ms", session.inferenceMs)
            results.putLong("${prefix}_runtime_total_ms", session.totalMs)
            results.putInt("${prefix}_segment_count", session.segmentCount)
            results.putBoolean("${prefix}_cancelled", session.cancelled)
        }
        results.putLong("${prefix}_rss_before_bytes", evidence.rssBefore.currentBytes)
        results.putLong("${prefix}_rss_after_bytes", evidence.rssAfter.currentBytes)
        results.putLong("${prefix}_peak_rss_bytes", evidence.peakRssBytes)
        evidence.temperatureDeciCBefore?.let {
            results.putInt("${prefix}_temperature_deci_c_before", it)
        }
        evidence.temperatureDeciCAfter?.let {
            results.putInt("${prefix}_temperature_deci_c_after", it)
        }
        results.putString("${prefix}_rss_source", "proc-self-status-vmrss-vmhwm")
        results.putInt("${prefix}_cue_count", evidence.cueCount)
        results.putString("${prefix}_cue_fingerprint_sha256", evidence.cueFingerprint)
        results.putBoolean("${prefix}_cue_timestamps_valid", evidence.cueTimestampsValid)
        results.putBoolean("${prefix}_empty_result", evidence.emptyResult)
        results.putBoolean("${prefix}_failure_observed", evidence.failureObserved)
        evidence.failureCategory?.let { results.putString("${prefix}_failure_category", it) }
        results.putBoolean("${prefix}_process_crash_observed", evidence.processCrashObserved)
        evidence.lifecycleAfter?.let { lifecycle ->
            results.putInt("${prefix}_create_count", lifecycle.createCount)
            results.putInt("${prefix}_free_count", lifecycle.freeCount)
            results.putInt("${prefix}_max_concurrent_inference", lifecycle.maxConcurrentInference)
        }
    }

    fun assertColdThenHot(
        cold: WhisperSessionRunEvidence,
        hot: WhisperSessionRunEvidence,
    ) {
        val coldMetrics = requireNotNull(cold.session) { "Cold run emitted no session metrics." }
        val hotMetrics = requireNotNull(hot.session) { "Hot run emitted no session metrics." }
        check(coldMetrics.contextHandle != 0L) { "Cold run returned an invalid context handle." }
        check(!coldMetrics.reusedContext) { "Cold run was incorrectly reported as reused." }
        check(hotMetrics.reusedContext) { "Second run did not report context reuse." }
        check(hotMetrics.contextHandle == coldMetrics.contextHandle) {
            "Second run did not use the cold run's context handle."
        }
        check(hotMetrics.contextLoadMs < coldMetrics.contextLoadMs) {
            "Hot context initialization was not lower than cold initialization: " +
                "cold=${coldMetrics.contextLoadMs}ms hot=${hotMetrics.contextLoadMs}ms"
        }
        check(!cold.processCrashObserved && !hot.processCrashObserved) {
            "A cold/hot evidence run observed a process crash."
        }
        check(cold.cueTimestampsValid && hot.cueTimestampsValid) {
            "A cold/hot evidence run produced invalid cue timestamps."
        }
        val coldBefore = cold.lifecycleBefore
        val coldAfter = cold.lifecycleAfter
        val hotAfter = hot.lifecycleAfter
        if (coldBefore != null && coldAfter != null && hotAfter != null) {
            check(coldAfter.createCount == coldBefore.createCount + 1) {
                "Cold run did not create exactly one context."
            }
            check(hotAfter.createCount == coldAfter.createCount) {
                "Hot run created another context."
            }
            check(hotAfter.freeCount == coldAfter.freeCount) {
                "Hot run unexpectedly freed the cached context."
            }
            check(hotAfter.maxConcurrentInference <= 1) {
                "Native inference concurrency exceeded one."
            }
        }
    }

    fun assertExpiredThenRecreated(
        cached: WhisperSessionRunEvidence,
        recreated: WhisperSessionRunEvidence,
    ) {
        requireNotNull(cached.session) { "Cached run emitted no session metrics." }
        val recreatedMetrics = requireNotNull(recreated.session) { "Recreated run emitted no session metrics." }
        check(!recreatedMetrics.reusedContext) { "Expired context was incorrectly reused." }
        check(recreatedMetrics.contextHandle != 0L) { "Expiry produced an invalid replacement context handle." }
        val before = recreated.lifecycleBefore
        val after = recreated.lifecycleAfter
        if (before != null && after != null) {
            check(after.createCount == before.createCount + 1) { "Expiry did not create one replacement context." }
            check(after.freeCount >= before.freeCount + 1) { "Expiry did not free the old context." }
        }
    }

    fun assertCancellationThenRebuild(
        cancelled: WhisperSessionRunEvidence,
        rebuilt: WhisperSessionRunEvidence,
    ) {
        val cancelledMetrics = requireNotNull(cancelled.session) { "Cancellation emitted no session metrics." }
        val rebuiltMetrics = requireNotNull(rebuilt.session) { "Post-cancellation run emitted no session metrics." }
        check(cancelledMetrics.cancelled) { "Cancelled run was not marked cancelled." }
        check(!rebuiltMetrics.reusedContext) { "A cancelled context was reused." }
        val cancelledAfter = cancelled.lifecycleAfter
        val rebuiltAfter = rebuilt.lifecycleAfter
        if (cancelledAfter != null && rebuiltAfter != null) {
            check(rebuiltAfter.createCount == cancelledAfter.createCount + 1) {
                "Post-cancellation run did not create one replacement context."
            }
        }
        assertSafeReleaseOrder(cancelled.lifecycleAfter?.events.orEmpty())
    }

    fun assertSafeReleaseOrder(events: List<WhisperSessionLifecycleEvent>) {
        val abort = events.indexOf(WhisperSessionLifecycleEvent.ABORT_REQUESTED)
        val transcribeExit = events.indexOf(WhisperSessionLifecycleEvent.TRANSCRIBE_EXITED)
        val threadExit = events.indexOf(WhisperSessionLifecycleEvent.INFERENCE_THREAD_EXITED)
        val taskCleanup = events.indexOf(WhisperSessionLifecycleEvent.TASK_STATE_CLEANED)
        val free = events.indexOf(WhisperSessionLifecycleEvent.CONTEXT_FREED)
        check(abort >= 0 && transcribeExit > abort && threadExit > transcribeExit &&
            taskCleanup > threadExit && free > taskCleanup) {
            "Unsafe cancellation release order: $events"
        }
    }

    fun assertDeferredReleaseAfterInference(events: List<WhisperSessionLifecycleEvent>) {
        val pending = events.indexOf(WhisperSessionLifecycleEvent.INVALIDATION_PENDING)
        val transcribeExit = events.indexOf(WhisperSessionLifecycleEvent.TRANSCRIBE_EXITED)
        val threadExit = events.indexOf(WhisperSessionLifecycleEvent.INFERENCE_THREAD_EXITED)
        val free = events.indexOf(WhisperSessionLifecycleEvent.CONTEXT_FREED)
        check(pending >= 0 && transcribeExit > pending && threadExit > transcribeExit && free > threadExit) {
            "An active context was not released after native inference fully exited: $events"
        }
    }

    fun assertIdleRelease(
        before: WhisperSessionLifecycleSnapshot,
        after: WhisperSessionLifecycleSnapshot,
    ) {
        check(after.createCount == before.createCount) { "Idle release unexpectedly created a context." }
        check(after.freeCount == before.freeCount + 1) { "Idle release did not free exactly one context." }
        check(after.activeHandle == null) { "Idle release left an active context handle." }
    }

    fun assertSingleNativeInference(snapshot: WhisperSessionLifecycleSnapshot) {
        check(snapshot.maxConcurrentInference <= 1) {
            "Native inference concurrency exceeded one: ${snapshot.maxConcurrentInference}"
        }
    }

    fun assertCueIsolation(
        first: WhisperSessionRunEvidence,
        second: WhisperSessionRunEvidence,
        expectedSecondFingerprint: String,
        fixturesAreDifferent: Boolean,
    ) {
        check(second.cueFingerprint == expectedSecondFingerprint) {
            "Second task output does not match its independently computed expected cue fingerprint."
        }
        if (fixturesAreDifferent) {
            check(first.cueFingerprint != second.cueFingerprint) {
                "Distinct test fixtures produced indistinguishable cue evidence; task carry-over cannot be excluded."
            }
        }
    }

    companion object {
        fun fingerprintCues(cues: List<CaptionCue>): String = cueEvidence(cues).fingerprint

        private fun cueEvidence(cues: List<CaptionCue>): CueEvidence {
            var previousStart = -1L
            var previousEnd = -1L
            val timestampsValid = cues.all { cue ->
                val valid = cue.startMs >= 0L && cue.endMs > cue.startMs &&
                    cue.startMs >= previousStart && cue.endMs >= previousEnd
                previousStart = cue.startMs
                previousEnd = cue.endMs
                valid
            }
            val digest = MessageDigest.getInstance("SHA-256")
            cues.forEach { cue ->
                digest.update(cue.id.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(cue.startMs.toString().toByteArray(Charsets.US_ASCII))
                digest.update(0.toByte())
                digest.update(cue.endMs.toString().toByteArray(Charsets.US_ASCII))
                digest.update(0.toByte())
                digest.update(cue.english.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            return CueEvidence(
                count = cues.size,
                fingerprint = digest.digest().joinToString("") { "%02x".format(it) },
                timestampsValid = timestampsValid,
            )
        }

        private data class CueEvidence(
            val count: Int,
            val fingerprint: String,
            val timestampsValid: Boolean,
        )
    }

    private fun readProcessRss(): ProcessRss {
        var currentKb = 0L
        var peakKb = 0L
        runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("VmRSS:") -> currentKb = parseStatusKb(line)
                        line.startsWith("VmHWM:") -> peakKb = parseStatusKb(line)
                    }
                }
            }
        }
        return ProcessRss(
            currentBytes = currentKb * 1_024L,
            highWaterMarkBytes = peakKb * 1_024L,
        )
    }

    private fun parseStatusKb(line: String): Long =
        line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L

    @Suppress("DEPRECATION")
    private fun readBatteryTemperatureDeciC(): Int? {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val value = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return value.takeUnless { it == Int.MIN_VALUE }
    }
}

data class WhisperSessionRunStart(
    val label: String,
    val startedElapsedRealtimeMs: Long,
    val rss: ProcessRss,
    val batteryTemperatureDeciC: Int?,
    val lifecycle: WhisperSessionLifecycleSnapshot?,
)

data class WhisperSessionRunEvidence(
    val label: String,
    val wallTotalMs: Long,
    val session: WhisperSessionRunMetrics?,
    val rssBefore: ProcessRss,
    val rssAfter: ProcessRss,
    val temperatureDeciCBefore: Int?,
    val temperatureDeciCAfter: Int?,
    val cueCount: Int,
    val cueFingerprint: String,
    val cueTimestampsValid: Boolean,
    val emptyResult: Boolean,
    val failureObserved: Boolean,
    val failureCategory: String?,
    val processCrashObserved: Boolean,
    val lifecycleBefore: WhisperSessionLifecycleSnapshot?,
    val lifecycleAfter: WhisperSessionLifecycleSnapshot?,
) {
    val peakRssBytes: Long
        get() = maxOf(
            rssBefore.currentBytes,
            rssBefore.highWaterMarkBytes,
            rssAfter.currentBytes,
            rssAfter.highWaterMarkBytes,
        )
}

data class ProcessRss(
    val currentBytes: Long,
    val highWaterMarkBytes: Long,
)

data class WhisperSessionLifecycleSnapshot(
    val createCount: Int,
    val freeCount: Int,
    val maxConcurrentInference: Int,
    val activeHandle: Long?,
    val events: List<WhisperSessionLifecycleEvent> = emptyList(),
)

enum class WhisperSessionLifecycleEvent {
    CONTEXT_CREATED,
    INFERENCE_STARTED,
    ABORT_REQUESTED,
    INVALIDATION_PENDING,
    TRANSCRIBE_EXITED,
    INFERENCE_THREAD_EXITED,
    TASK_STATE_CLEANED,
    CONTEXT_FREED,
}
