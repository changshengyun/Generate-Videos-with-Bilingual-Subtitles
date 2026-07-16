package com.example.lyriccaptioner.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.SubtitleStyle
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Dual-ABI product exporter backed by the checked-in FFmpegKit AAR.
 * Media3 remains responsible for playback and preview; this class only replaces MP4 burn-in.
 */
class FfmpegKitSubtitleExporter(
    context: Context,
) : ExportEngine {
    private val appContext = context.applicationContext

    override suspend fun export(
        project: CaptionProject,
        destinationUri: Uri,
    ): ExportResult = withContext(Dispatchers.IO) {
        Log.i(LOG_TAG, "event=ffmpegkit_export_started captionCount=${project.captions.size}")
        require(project.captions.isNotEmpty()) { "At least one subtitle cue is required." }
        require(project.exportProfile.burnInSubtitles) { "Burn-in subtitles are disabled." }

        val workDirectory = createWorkDirectory()
        val inputFile = File(workDirectory, "input.mp4")
        val assFile = File(workDirectory, "captions.ass")
        val outputFile = File(workDirectory, "output.mp4")
        try {
            copyUriToFile(project.videoUri, inputFile)
            assFile.writeText(
                AssSubtitleWriter.write(project.captions, project.exportProfile.subtitleStyle),
                Charsets.UTF_8,
            )
            FFmpegKitConfig.setFontDirectory(appContext, "/system/fonts", emptyMap())
            runFfmpeg(inputFile, assFile, outputFile, destinationUri)
        } catch (error: Throwable) {
            // Failures before FFmpegKit owns the session (for example an empty input URI)
            // must also remove the SAF destination created by the save picker.
            deleteDestination(destinationUri)
            throw error
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    private suspend fun runFfmpeg(
        inputFile: File,
        assFile: File,
        outputFile: File,
        destinationUri: Uri,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val completed = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        var session: FFmpegSession? = null

        fun cleanup(deleteDestination: Boolean) {
            outputFile.delete()
            assFile.delete()
            inputFile.delete()
            if (deleteDestination) {
                deleteDestination(destinationUri)
            }
        }

        fun fail(error: Throwable) {
            if (completed.compareAndSet(false, true)) {
                cleanup(deleteDestination = true)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

        val callback = FFmpegSessionCompleteCallback { finishedSession ->
            if (!completed.compareAndSet(false, true)) return@FFmpegSessionCompleteCallback
            if (ReturnCode.isSuccess(finishedSession.returnCode)) {
                Log.i(
                    LOG_TAG,
                    "event=ffmpegkit_export_completed returnCode=${finishedSession.returnCode?.getValue()}",
                )
                runCatching {
                    val inspected = inspectOutput(outputFile)
                    copyFileToUri(outputFile, destinationUri, inspected.fileSizeBytes)
                    inspected.copy(outputUri = destinationUri)
                }.onSuccess { result ->
                    cleanup(deleteDestination = false)
                    if (continuation.isActive) continuation.resume(result)
                }.onFailure { error ->
                    cleanup(deleteDestination = true)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            } else {
                cleanup(deleteDestination = true)
                val returnCode = finishedSession.returnCode?.getValue()
                Log.e(LOG_TAG, "event=ffmpegkit_export_failed returnCode=$returnCode")
                logErrorText("ffmpegkit_all_logs", finishedSession.allLogsAsString)
                logErrorText("ffmpegkit_fail_stack_trace", finishedSession.failStackTrace)
                val reason = if (ReturnCode.isCancel(finishedSession.returnCode)) {
                    "FFmpeg export cancelled."
                } else {
                    "FFmpeg export failed (returnCode=$returnCode)."
                }
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(reason))
            }
        }

        continuation.invokeOnCancellation {
            cancelled.set(true)
            if (completed.compareAndSet(false, true)) {
                session?.let { FFmpegKit.cancel(it.sessionId) }
                cleanup(deleteDestination = true)
            }
        }

        val arguments = arrayOf(
            "-hide_banner",
            "-loglevel",
            "error",
            "-nostdin",
            "-y",
            "-i",
            inputFile.absolutePath,
            "-vf",
            buildSubtitleFilter(assFile.absolutePath),
            "-map",
            "0:v:0",
            "-map",
            "0:a:0",
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            "-crf",
            "23",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "128k",
            "-movflags",
            "+faststart",
            outputFile.absolutePath,
        )
        runCatching {
            session = FFmpegKit.executeWithArgumentsAsync(
                arguments,
                callback,
                LogCallback { log ->
                    if (log.level == Level.AV_LOG_ERROR || log.level == Level.AV_LOG_FATAL) {
                        logErrorText("ffmpegkit_log level=${log.level}", log.message)
                    }
                },
                null,
            )
            if (cancelled.get()) session?.let { FFmpegKit.cancel(it.sessionId) }
        }.onFailure(::fail)
    }

    private fun createWorkDirectory(): File {
        val parent = File(appContext.cacheDir, "ffmpeg-exports")
        check(parent.exists() || parent.mkdirs()) { "Could not create FFmpeg work directory." }
        return File(parent, "job-${System.nanoTime()}").also {
            check(it.mkdirs()) { "Could not create FFmpeg job directory." }
        }
    }

    private fun deleteDestination(destinationUri: Uri) {
        val deletedByDocumentApi = runCatching {
            DocumentsContract.deleteDocument(appContext.contentResolver, destinationUri)
        }.getOrDefault(false)
        if (!deletedByDocumentApi) {
            runCatching { appContext.contentResolver.delete(destinationUri, null, null) }
        }
    }

    private fun copyUriToFile(sourceUri: Uri, targetFile: File) {
        val input = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Could not open the selected video.")
        input.use { source ->
            targetFile.outputStream().use { destination -> source.copyTo(destination) }
        }
        check(targetFile.length() > 0L) { "The selected video is empty." }
    }

    private fun copyFileToUri(sourceFile: File, destinationUri: Uri, expectedBytes: Long) {
        val output = appContext.contentResolver.openOutputStream(destinationUri, "w")
            ?: error("Could not open the selected output file.")
        val copiedBytes = output.use { destination ->
            sourceFile.inputStream().use { source -> source.copyTo(destination) }
        }
        check(copiedBytes == expectedBytes) {
            "The exported video was not fully written to the selected destination."
        }
    }

    private fun inspectOutput(outputFile: File): ExportResult {
        check(outputFile.length() > MIN_VALID_OUTPUT_BYTES) { "The export created an empty MP4 file." }
        val extractor = MediaExtractor()
        var hasVideoTrack = false
        var hasAudioTrack = false
        try {
            extractor.setDataSource(outputFile.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                hasVideoTrack = hasVideoTrack || mime?.startsWith("video/") == true
                hasAudioTrack = hasAudioTrack || mime?.startsWith("audio/") == true
            }
        } finally {
            extractor.release()
        }
        check(hasVideoTrack) { "The exported MP4 has no video track." }
        check(hasAudioTrack) { "The exported MP4 has no audio track." }

        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(outputFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
        check(durationMs > 0L) { "The exported MP4 has no playable duration." }
        return ExportResult(
            outputUri = Uri.EMPTY,
            fileSizeBytes = outputFile.length(),
            durationMs = durationMs,
            hasAudioTrack = hasAudioTrack,
        )
    }

    private fun logErrorText(event: String, value: String?) {
        val text = value?.takeIf { it.isNotEmpty() } ?: "<empty>"
        text.lineSequence().forEachIndexed { lineIndex, line ->
            val chunks = line.chunked(MAX_LOG_MESSAGE_CHARS).ifEmpty { listOf("") }
            chunks.forEachIndexed { chunkIndex, chunk ->
                Log.e(
                    LOG_TAG,
                    "event=$event line=$lineIndex chunk=$chunkIndex message=$chunk",
                )
            }
        }
    }

    private companion object {
        const val LOG_TAG = "FfmpegKitSubtitleExporter"
        const val MAX_LOG_MESSAGE_CHARS = 3_000
        const val MIN_VALID_OUTPUT_BYTES = 1_024L
    }
}

internal fun buildSubtitleFilter(path: String): String = buildString {
    append("subtitles=filename='")
    path.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            ':' -> append("\\:")
            '\'' -> append("""'\\\''""")
            else -> append(character)
        }
    }
    append('\'')
}

internal object AssSubtitleWriter {
    fun write(captions: List<CaptionCue>, style: SubtitleStyle): String {
        val marginV = (1080 * style.bottomMarginPercent.coerceIn(0, 40) / 100f).toInt()
        val fontSize = style.fontSizeSp.coerceIn(14, 96)
        val primary = assColor(style.primaryColorHex, "FFFFFF")
        val secondary = assColor(style.secondaryColorHex, "F4E7A1")
        val outline = assColor(style.outlineColorHex, "000000")
        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: 1920")
            appendLine("PlayResY: 1080")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: Default,Arial,$fontSize,$primary,$secondary,$outline,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,40,40,$marginV,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            captions.sortedBy { it.startMs }.forEach { cue ->
                val english = escapeText(cue.english.trim())
                val chinese = escapeText(cue.chinese.trim())
                val text = listOf(english, chinese).filter { it.isNotEmpty() }.joinToString("\\N")
                if (text.isNotEmpty() && cue.endMs > cue.startMs) {
                    appendLine(
                        "Dialogue: 0,${formatAssTime(cue.startMs)},${formatAssTime(cue.endMs)},Default,,0,0,0,,$text",
                    )
                }
            }
        }
    }

    private fun escapeText(value: String): String = value
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("\r\n", "\\N")
        .replace("\n", "\\N")

    private fun assColor(value: String, fallback: String): String {
        val hex = value.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) } ?: fallback
        return "&H00${hex.substring(4, 6)}${hex.substring(2, 4)}${hex.substring(0, 2)}"
    }

    private fun formatAssTime(milliseconds: Long): String {
        val totalCentiseconds = (milliseconds.coerceAtLeast(0L) / 10L)
        val centiseconds = totalCentiseconds % 100
        val totalSeconds = totalCentiseconds / 100
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centiseconds)
    }
}
