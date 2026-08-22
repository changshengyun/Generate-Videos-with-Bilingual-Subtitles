package com.example.lyriccaptioner

import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.example.lyriccaptioner.processing.AndroidAudioExtractor
import com.example.lyriccaptioner.processing.WhisperNativeSessionBridge
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Test-only fixed-WAV harness for raw Whisper Native diagnostics. */
class AsrDiagnosticInstrumentation : Instrumentation() {
    private lateinit var inputArguments: Bundle

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        inputArguments = arguments
        start()
    }

    override fun onStart() {
        super.onStart()
        val results = Bundle()
        runCatching {
            when (inputArguments.getString(ARG_MODE)) {
                MODE_EXTRACT -> runBlocking { extractFixedWav(results) }
                MODE_RUN -> runRawDiagnostic(results)
                else -> error("Missing or invalid -e $ARG_MODE $MODE_EXTRACT|$MODE_RUN")
            }
        }.onSuccess {
            finish(Activity.RESULT_OK, results)
        }.onFailure { error ->
            results.putString("failure", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, results)
            throw AssertionError("ASR diagnostic instrumentation failed", error)
        }
    }

    private suspend fun extractFixedWav(results: Bundle) {
        val sourceFile = File(
            inputArguments.getString(ARG_SOURCE)
                ?: error("Missing -e $ARG_SOURCE <source.mp4>"),
        )
        check(sourceFile.isFile && sourceFile.length() > 0L) {
            "Diagnostic source video is missing or empty: ${sourceFile.absolutePath}"
        }
        val reportDirectory = reportDirectory()
        val fixedWav = File(reportDirectory, FIXED_WAV_NAME)
        check(!fixedWav.exists()) {
            "Fixed diagnostic WAV already exists; refusing to extract the video again."
        }

        val extracted = AndroidAudioExtractor(targetContext.applicationContext)
            .extract(Uri.fromFile(sourceFile))
        val extractedFile = File(requireNotNull(extracted.filePath))
        try {
            extractedFile.copyTo(fixedWav, overwrite = false)
        } finally {
            if (extracted.deleteFileAfterUse) extractedFile.delete()
        }
        check(fixedWav.isFile && fixedWav.length() > 44L) {
            "Could not freeze the diagnostic WAV."
        }
        results.putString("sourceSha256", sha256(sourceFile))
        results.putLong("sourceBytes", sourceFile.length())
        results.putString("wavSha256", sha256(fixedWav))
        results.putLong("wavBytes", fixedWav.length())
        results.putString("wavPath", fixedWav.absolutePath)
        results.putInt("sampleRate", extracted.sampleRate)
        results.putInt("channels", extracted.channels)
    }

    private fun runRawDiagnostic(results: Bundle) {
        val label = inputArguments.getString(ARG_LABEL)
            ?.takeIf { it == "small" || it == "base" }
            ?: error("Missing or invalid -e $ARG_LABEL small|base")
        val wavFile = File(
            inputArguments.getString(ARG_WAV)
                ?: File(reportDirectory(), FIXED_WAV_NAME).absolutePath,
        )
        val modelFile = File(
            inputArguments.getString(ARG_MODEL)
                ?: error("Missing -e $ARG_MODEL <model.bin>"),
        )
        check(wavFile.isFile && wavFile.length() > 44L) {
            "Diagnostic WAV is missing or empty: ${wavFile.absolutePath}"
        }
        check(modelFile.isFile && modelFile.length() > 0L) {
            "Diagnostic model is missing or empty: ${modelFile.absolutePath}"
        }
        check(WhisperNativeSessionBridge.isAvailable) {
            "Whisper diagnostic requires a Native-enabled Debug APK."
        }

        readShell("logcat -c")
        val startedAt = SystemClock.elapsedRealtime()
        val reportJson = nativeRunWhisperDebug(modelFile.absolutePath, wavFile.absolutePath)
        val callElapsedMs = SystemClock.elapsedRealtime() - startedAt
        val report = JSONObject(reportJson)
        val reportFile = File(reportDirectory(), "$label.json")
        reportFile.writeText(reportJson, Charsets.UTF_8)

        results.putString("modelLabel", label)
        results.putString("modelSha256", sha256(modelFile))
        results.putString("wavSha256", sha256(wavFile))
        results.putLong("wavBytes", wavFile.length())
        results.putString("whisperVersion", report.getString("whisper_version"))
        results.putString("whisperCommit", report.getString("whisper_commit"))
        results.putBoolean("freshContext", report.getBoolean("fresh_context"))
        results.putBoolean("contextReuse", report.getBoolean("context_reuse"))
        results.putBoolean("noContext", report.getBoolean("no_context"))
        results.putString("language", report.getString("language"))
        results.putInt("threads", report.getInt("threads"))
        results.putLong("wavDurationMs", report.getLong("wav_duration_ms"))
        results.putInt("whisperFullReturnCode", report.getInt("whisper_full_return_code"))
        results.putLong("inferenceMs", report.getLong("inference_ms"))
        results.putInt("segmentCount", report.getInt("segment_count"))
        results.putLong("callElapsedMs", callElapsedMs)
        results.putString("reportPath", reportFile.absolutePath)
        results.putString("nativeLifecycleLog", readShell("logcat -d -s WhisperDiag:I *:S"))
    }

    private fun reportDirectory(): File =
        File(targetContext.filesDir, "asr-diagnostics").also { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Could not create ASR diagnostic directory."
            }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readShell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            .also { descriptor.close() }
    }

    private external fun nativeRunWhisperDebug(modelPath: String, audioPath: String): String

    private companion object {
        const val ARG_MODE = "mode"
        const val ARG_SOURCE = "source"
        const val ARG_LABEL = "label"
        const val ARG_WAV = "wav"
        const val ARG_MODEL = "model"
        const val MODE_EXTRACT = "extract"
        const val MODE_RUN = "run"
        const val FIXED_WAV_NAME = "fixed-input.wav"
    }
}
