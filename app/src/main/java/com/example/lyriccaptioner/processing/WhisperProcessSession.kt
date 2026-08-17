package com.example.lyriccaptioner.processing

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log

/** Process-wide owner for the one production Whisper session cache. */
object WhisperProcessSession {
    private const val LOG_TAG = "WhisperSession"
    private val lock = Any()

    @Volatile
    private var runtime: WhisperSessionRuntime? = null

    fun get(context: Context): WhisperSessionRuntime {
        runtime?.let { return it }
        return synchronized(lock) {
            runtime ?: create(context.applicationContext).also { runtime = it }
        }
    }

    private fun create(appContext: Context): WhisperSessionRuntime {
        val created = WhisperSessionRuntime(
            nativeClient = WhisperNativeSessionBridge,
            observer = WhisperSessionObserver { metrics ->
                Log.i(
                    LOG_TAG,
                    "event=whisper_session_run handle=${metrics.contextHandle} " +
                        "reused=${metrics.reusedContext} loadMs=${metrics.contextLoadMs} " +
                        "inferenceMs=${metrics.inferenceMs} totalMs=${metrics.totalMs} " +
                        "segmentCount=${metrics.segmentCount} cancelled=${metrics.cancelled}",
                )
            },
        )
        appContext.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                override fun onLowMemory() {
                    created.onCriticalMemoryPressure()
                }

                @Suppress("DEPRECATION")
                override fun onTrimMemory(level: Int) {
                    if (
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                    ) {
                        created.onCriticalMemoryPressure()
                    }
                }
            },
        )
        return created
    }
}
