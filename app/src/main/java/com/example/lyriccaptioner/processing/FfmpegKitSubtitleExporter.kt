package com.example.lyriccaptioner.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionGeometry
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionVerticalAnchor
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.PreviewContainerSize
import com.example.lyriccaptioner.model.SourceVideoSize
import com.example.lyriccaptioner.model.SubtitleStyle
import com.example.lyriccaptioner.model.toCaptionLayout
import com.example.lyriccaptioner.model.toDefaultCaptionStyle
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

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
        var stage = "validation"
        var workDirectory: File? = null
        val destinationState: ExportDestinationState
        try {
            require(project.captions.isNotEmpty()) { "At least one subtitle cue is required." }
            require(project.exportProfile.burnInSubtitles) { "Burn-in subtitles are disabled." }
            stage = "ownership"
            check(!ExportDestinationPolicy.isSameDocument(project.videoUri, destinationUri, appContext.contentResolver)) {
                "The export destination must not be the source video."
            }
            destinationState = ExportDestinationPolicy.inspectDestination(
                appContext.contentResolver,
                destinationUri,
            )
            ExportDestinationPolicy.requireNewDestination(destinationState)
            stage = "work_directory"
            workDirectory = createWorkDirectory()
        } catch (error: Throwable) {
            workDirectory?.deleteRecursively()
            logPreSessionFailure(stage, error)
            throw error
        }

        val activeWorkDirectory = checkNotNull(workDirectory)
        val inputFile = File(activeWorkDirectory, "input.mp4")
        val assFile = File(activeWorkDirectory, "captions.ass")
        val outputFile = File(activeWorkDirectory, "output.mp4")
        try {
            stage = "source_copy"
            copyUriToFile(project.videoUri, inputFile)
            stage = "ass_write"
            assFile.writeText(
                AssSubtitleWriter.write(
                    captions = project.captions,
                    layout = project.captionLayout,
                    defaultStyle = project.defaultCaptionStyle,
                ),
                Charsets.UTF_8,
            )
            stage = "font_config"
            FFmpegKitConfig.setFontDirectory(appContext, "/system/fonts", emptyMap())
            Log.i(LOG_TAG, "event=ffmpegkit_pre_session_completed stage=font_config")
        } catch (error: Throwable) {
            logPreSessionFailure(stage, error)
            throw error
        }
        try {
            runFfmpeg(inputFile, assFile, outputFile, destinationUri, destinationState)
        } finally {
            activeWorkDirectory.deleteRecursively()
        }
    }

    private fun logPreSessionFailure(stage: String, error: Throwable) {
        Log.e(
            LOG_TAG,
            "event=ffmpegkit_pre_session_failed stage=$stage " +
                "errorType=${error.javaClass.simpleName} reasonCode=PRE_SESSION_EXCEPTION",
        )
    }

    private suspend fun runFfmpeg(
        inputFile: File,
        assFile: File,
        outputFile: File,
        destinationUri: Uri,
        destinationState: ExportDestinationState,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val terminal = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        var session: FFmpegSession? = null
        var copyJob: Job? = null

        fun fail(error: Throwable) {
            if (terminal.compareAndSet(false, true)) {
                if (destinationState == ExportDestinationState.NEW) {
                    deleteOwnedDestination(destinationUri)
                }
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

        val callback = FFmpegSessionCompleteCallback { finishedSession ->
            if (terminal.get() || cancelled.get()) return@FFmpegSessionCompleteCallback
            if (ReturnCode.isSuccess(finishedSession.returnCode)) {
                Log.i(
                    LOG_TAG,
                    "event=ffmpegkit_render_completed returnCode=${finishedSession.returnCode?.getValue()}",
                )
                copyJob = CoroutineScope(continuation.context + Dispatchers.IO).launch {
                    try {
                        ensureActive()
                        val inspected = inspectOutput(outputFile)
                        copyFileToUri(
                            outputFile,
                            destinationUri,
                            inspected.fileSizeBytes,
                            ownsDestination = destinationState == ExportDestinationState.NEW,
                        )
                        ensureActive()
                        if (terminal.compareAndSet(false, true) && continuation.isActive && !cancelled.get()) {
                            Log.i(LOG_TAG, "event=ffmpegkit_target_copy_completed bytes=${inspected.fileSizeBytes}")
                            continuation.resume(inspected.copy(outputUri = destinationUri))
                        } else if (destinationState == ExportDestinationState.NEW) {
                            // Cancellation can race with the final resume after the
                            // destination has been fully copied. It is still owned by
                            // this task until the result is delivered.
                            deleteOwnedDestination(destinationUri)
                        }
                    } catch (error: Throwable) {
                        if (destinationState == ExportDestinationState.NEW) {
                            deleteOwnedDestination(destinationUri)
                        }
                        if (terminal.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }
            } else {
                val returnCode = finishedSession.returnCode?.getValue()
                Log.e(LOG_TAG, "event=ffmpegkit_export_failed returnCode=$returnCode")
                logErrorText("ffmpegkit_all_logs", finishedSession.allLogsAsString)
                logErrorText("ffmpegkit_fail_stack_trace", finishedSession.failStackTrace)
                val reason = if (ReturnCode.isCancel(finishedSession.returnCode)) {
                    "FFmpeg export cancelled."
                } else {
                    "FFmpeg export failed (returnCode=$returnCode)."
                }
                fail(IllegalStateException(reason))
            }
        }

        continuation.invokeOnCancellation {
            cancelled.set(true)
            terminal.set(true)
            copyJob?.cancel()
            session?.let { FFmpegKit.cancel(it.sessionId) }
            if (destinationState == ExportDestinationState.NEW) {
                deleteOwnedDestination(destinationUri)
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
            Log.i(LOG_TAG, "event=ffmpegkit_session_submitted")
            if (cancelled.get()) session?.let { FFmpegKit.cancel(it.sessionId) }
        }.onFailure { error ->
            Log.e(
                LOG_TAG,
                "event=ffmpegkit_session_submit_failed " +
                    "errorType=${error.javaClass.simpleName} reasonCode=SESSION_SUBMIT_EXCEPTION",
            )
            fail(error)
        }
    }

    private fun createWorkDirectory(): File {
        val parent = File(appContext.cacheDir, "ffmpeg-exports")
        check(parent.exists() || parent.mkdirs()) { "Could not create FFmpeg work directory." }
        return File(parent, "job-${System.nanoTime()}").also {
            check(it.mkdirs()) { "Could not create FFmpeg job directory." }
        }
    }

    private suspend fun copyUriToFile(sourceUri: Uri, targetFile: File) {
        coroutineContext.ensureActive()
        val input = appContext.contentResolver.openInputStream(sourceUri)
            ?: error("Could not open the selected video.")
        input.use { source ->
            targetFile.outputStream().use { destination ->
                val buffer = ByteArray(DEFAULT_COPY_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    destination.write(buffer, 0, count)
                }
            }
        }
        check(targetFile.length() > 0L) { "The selected video is empty." }
    }

    private suspend fun copyFileToUri(
        sourceFile: File,
        destinationUri: Uri,
        expectedBytes: Long,
        ownsDestination: Boolean,
    ) {
        coroutineContext.ensureActive()
        val output = appContext.contentResolver.openOutputStream(destinationUri, "w")
            ?: error("Could not open the selected output file.")
        try {
            val copiedBytes = output.use { destination ->
                sourceFile.inputStream().use { source ->
                    copyStreamCancellable(source, destination)
                }
            }
            coroutineContext.ensureActive()
            check(copiedBytes == expectedBytes) {
                "The exported video was not fully written to the selected destination."
            }
        } catch (error: Throwable) {
            if (ownsDestination) {
                deleteOwnedDestination(destinationUri)
            }
            throw error
        }
    }

    private fun deleteOwnedDestination(destinationUri: Uri) {
        runCatching {
            if (destinationUri.scheme == "file") {
                File(destinationUri.path.orEmpty()).delete()
            } else {
                appContext.contentResolver.delete(destinationUri, null, null)
            }
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
        const val DEFAULT_COPY_BUFFER_SIZE = 64 * 1024
    }
}

internal suspend fun copyStreamCancellable(
    source: InputStream,
    destination: OutputStream,
): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        coroutineContext.ensureActive()
        val count = source.read(buffer)
        if (count < 0) return total
        coroutineContext.ensureActive()
        destination.write(buffer, 0, count)
        total += count
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
    fun write(
        captions: List<CaptionCue>,
        layout: CaptionLayout,
        defaultStyle: DefaultCaptionStyle,
    ): String {
        // Compose preview consumes this same resolved render.  Keep all per-cue style and
        // placement inheritance above the ASS-specific coordinate conversion boundary.
        val cues = captions.sortedBy { it.startMs }
            .mapIndexedNotNull { index, caption ->
                val spec = CaptionRenderResolver.resolveSpec(
                    caption = caption,
                    layout = layout,
                    defaultStyle = defaultStyle,
                    source = SourceVideoSize(PLAY_RES_X, PLAY_RES_Y),
                    container = PreviewContainerSize(PLAY_RES_X, PLAY_RES_Y),
                )
                val text = dialogueText(spec)
                if (text.isEmpty() || spec.caption.endMs <= spec.caption.startMs) {
                    null
                } else {
                    AssCue(
                        spec = spec,
                        styleName = "Cue${index.toString().padStart(4, '0')}",
                        geometry = resolveGeometry(spec.geometry),
                        text = text,
                    )
                }
            }
        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: $PLAY_RES_X")
            appendLine("PlayResY: $PLAY_RES_Y")
            appendLine("WrapStyle: 0")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            cues.forEach { cue -> appendLine(styleLine(cue)) }
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            cues.forEach { cue ->
                val source = cue.spec.caption
                val geometry = cue.geometry
                val overrides = "{\\an${geometry.alignment}\\pos(${geometry.positionX},${geometry.positionY})\\q0}"
                appendLine(
                    "Dialogue: 0,${formatAssTime(source.startMs)},${formatAssTime(source.endMs)}," +
                        "${cue.styleName},,${geometry.marginLeft},${geometry.marginRight}," +
                        "${geometry.marginVertical},,$overrides${cue.text}",
                )
            }
        }
    }

    /**
     * ASS does not apply SecondaryColour to the second line of a Dialogue. Keep the
     * bilingual cue in one Dialogue for timing/position consistency, but explicitly
     * switch to the resolved Chinese colour immediately before the Chinese text.
     * Override tags are scoped to this Dialogue by ASS, so they cannot affect the
     * English text before the line break or a subsequent Dialogue.
     */
    private fun dialogueText(spec: CaptionRenderSpec): String {
        val english = escapeText(spec.caption.english)
        val chinese = escapeText(spec.caption.chinese)
        val chineseWithColor = chinese.takeIf { it.isNotEmpty() }?.let {
            "{\\c${assColor(spec.style.secondaryColorHex, "F4E7A1")}&}$it"
        }.orEmpty()
        return when {
            english.isNotEmpty() && chineseWithColor.isNotEmpty() -> "$english\\N$chineseWithColor"
            english.isNotEmpty() -> english
            else -> chineseWithColor
        }
    }

    fun write(captions: List<CaptionCue>, style: SubtitleStyle): String = write(
        captions = captions,
        layout = style.toCaptionLayout(),
        defaultStyle = style.toDefaultCaptionStyle(),
    )

    private fun styleLine(cue: AssCue): String {
        val spec = cue.spec
        val style = spec.style
        val geometry = cue.geometry
        val primary = assColor(style.primaryColorHex, "FFFFFF")
        val secondary = assColor(style.secondaryColorHex, "F4E7A1")
        val outline = assColor(style.outlineColorHex, "000000")
        val back = if (style.backgroundEnabled) {
            assColor(style.backgroundColorHex, "000000")
        } else {
            LEGACY_ASS_BACK_COLOR
        }
        val borderStyle = if (style.backgroundEnabled) ASS_OPAQUE_BOX_BORDER_STYLE else ASS_OUTLINE_BORDER_STYLE
        val bold = if (style.bold) -1 else 0
        val italic = if (style.italic) -1 else 0
        return "Style: ${cue.styleName},${assFontName(style.fontFamily)},${spec.fontSizePx}," +
            "$primary,$secondary,$outline,$back,$bold,$italic,0,0,100,100,0,0,$borderStyle," +
            "${spec.outlineWidthPx},0," +
            "${geometry.alignment},${geometry.marginLeft},${geometry.marginRight}," +
            "${geometry.marginVertical},1"
    }

    /**
     * ASS uses a fixed 1920x1080 PlayRes. Resolve through the same effective
     * video rectangle model as Compose with an equal virtual source/container;
     * this keeps normalized cue placement and anchor semantics shared while
     * leaving ASS-specific alignment/margin encoding at this boundary.
     */
    private fun resolveGeometry(resolved: CaptionGeometry): AssGeometry {
        val left = resolved.textBoxLeftPx
        val right = (resolved.videoRect.right -
            (resolved.textBoxLeftPx + resolved.textBoxWidthPx)).coerceIn(0, PLAY_RES_X)
        val positionX = resolved.anchorXpx.coerceIn(0, PLAY_RES_X)
        val positionY = resolved.anchorYpx.coerceIn(0, PLAY_RES_Y)
        val verticalBand = when (resolved.anchor) {
            CaptionVerticalAnchor.TOP -> VerticalBand.TOP
            CaptionVerticalAnchor.MIDDLE -> VerticalBand.MIDDLE
            CaptionVerticalAnchor.BOTTOM -> VerticalBand.BOTTOM
        }
        val assAlignment = when (verticalBand) {
            VerticalBand.TOP -> when (resolved.alignment) {
                CaptionAlignment.LEFT -> 7
                CaptionAlignment.CENTER -> 8
                CaptionAlignment.RIGHT -> 9
            }
            VerticalBand.MIDDLE -> when (resolved.alignment) {
                CaptionAlignment.LEFT -> 4
                CaptionAlignment.CENTER -> 5
                CaptionAlignment.RIGHT -> 6
            }
            VerticalBand.BOTTOM -> when (resolved.alignment) {
                CaptionAlignment.LEFT -> 1
                CaptionAlignment.CENTER -> 2
                CaptionAlignment.RIGHT -> 3
            }
        }
        val marginVertical = when (verticalBand) {
            VerticalBand.TOP -> positionY
            VerticalBand.MIDDLE -> minOf(positionY, PLAY_RES_Y - positionY)
            VerticalBand.BOTTOM -> PLAY_RES_Y - positionY
        }.coerceIn(0, PLAY_RES_Y)
        return AssGeometry(
            alignment = assAlignment,
            marginLeft = left,
            marginRight = right,
            marginVertical = marginVertical,
            positionX = positionX,
            positionY = positionY,
        )
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

    private fun assFontName(fontFamily: String): String = when (fontFamily) {
        "serif" -> "serif"
        "mono" -> "monospace"
        else -> "sans-serif"
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

    private data class AssCue(
        val spec: CaptionRenderSpec,
        val styleName: String,
        val geometry: AssGeometry,
        val text: String,
    )

    private data class AssGeometry(
        val alignment: Int,
        val marginLeft: Int,
        val marginRight: Int,
        val marginVertical: Int,
        val positionX: Int,
        val positionY: Int,
    )

    private enum class VerticalBand {
        TOP,
        MIDDLE,
        BOTTOM,
    }

    private const val PLAY_RES_X = 1_920
    private const val PLAY_RES_Y = 1_080
    private const val ASS_OUTLINE_BORDER_STYLE = 1
    private const val ASS_OPAQUE_BOX_BORDER_STYLE = 3
    private const val LEGACY_ASS_BACK_COLOR = "&H80000000"
}
