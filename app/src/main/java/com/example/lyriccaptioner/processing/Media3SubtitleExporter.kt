package com.example.lyriccaptioner.processing

import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.UpdateAppearance
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Clock
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult as Media3ExportResult
import androidx.media3.transformer.Transformer
import com.example.lyriccaptioner.captions.CaptionTimeline
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.SubtitleStyle
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@OptIn(markerClass = [UnstableApi::class])
class Media3SubtitleExporter(
    context: Context,
) : ExportEngine {
    private val appContext = context.applicationContext

    override suspend fun export(
        project: CaptionProject,
        destinationUri: android.net.Uri,
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        val videoUri = project.videoUri
        val captions = project.captions
        val exportProfile = project.exportProfile
        require(captions.isNotEmpty()) { "At least one subtitle cue is required." }
        require(exportProfile.burnInSubtitles) { "Burn-in subtitles are disabled." }

        val outputFile = createTemporaryOutputFile(exportProfile.outputName)
        suspendCancellableCoroutine { continuation ->
            val overlay = TimedBilingualTextOverlay(captions, exportProfile.subtitleStyle)
            val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(OverlayEffect(listOf(overlay))),
                    ),
                )
                .build()

            lateinit var transformer: Transformer
            var copyJob: Job? = null
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: Media3ExportResult) {
                    copyJob = CoroutineScope(Dispatchers.IO).launch {
                        runCatching {
                            // The emulator's muxer can finish its final file flush just after this callback.
                            // Give the temporary MP4 a short chance to become readable before copying it.
                            delay(750)
                            val inspectedOutput = inspectOutput(outputFile)
                            val copiedBytes = appContext.contentResolver.openOutputStream(destinationUri, "w")
                                ?.use { output ->
                                    outputFile.inputStream().use { input -> input.copyTo(output) }
                                }
                                ?: error("Could not open the selected output file.")
                            check(copiedBytes == inspectedOutput.fileSizeBytes) {
                                "The exported video was not fully written to the selected destination."
                            }
                            inspectedOutput
                        }.onSuccess {
                            val result = it
                            outputFile.delete()
                            if (continuation.isActive) {
                                continuation.resume(
                                    result.copy(
                                        outputUri = destinationUri,
                                    ),
                                )
                            }
                        }.onFailure { error ->
                            outputFile.delete()
                            deleteDestination(destinationUri)
                            if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: Media3ExportResult,
                    exportException: ExportException,
                ) {
                    Log.e(
                        LOG_TAG,
                        "event=media3_export_error " +
                            "errorCode=${exportException.errorCode} " +
                            "errorCodeName=${exportException.errorCodeName} " +
                            "exceptionClass=${exportException.javaClass.name} " +
                            "exceptionMessage=\"${sanitizeMedia3DiagnosticMessage(exportException.message)}\" " +
                            "approximateDurationMs=${exportResult.approximateDurationMs} " +
                            "fileSizeBytes=${exportResult.fileSizeBytes} " +
                            "videoFrameCount=${exportResult.videoFrameCount} " +
                            "width=${exportResult.width} height=${exportResult.height} " +
                            "audioConversionProcess=${exportResult.audioConversionProcess} " +
                            "videoConversionProcess=${exportResult.videoConversionProcess}",
                        exportException,
                    )
                    outputFile.delete()
                    deleteDestination(destinationUri)
                    if (continuation.isActive) {
                        continuation.resumeWithException(exportException)
                    }
                }
            }

            transformer = Transformer.Builder(appContext)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAssetLoaderFactory(
                    DefaultAssetLoaderFactory(
                        appContext,
                        DefaultDecoderFactory.Builder(appContext)
                            .setEnableDecoderFallback(true)
                            .setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
                            .build(),
                        Clock.DEFAULT,
                        null,
                    ),
                )
                .addListener(listener)
                .build()

            continuation.invokeOnCancellation {
                copyJob?.cancel()
                Handler(Looper.getMainLooper()).post { transformer.cancel() }
                outputFile.delete()
                deleteDestination(destinationUri)
            }
            runCatching {
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }.onFailure { error ->
                outputFile.delete()
                deleteDestination(destinationUri)
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    private fun deleteDestination(destinationUri: android.net.Uri) {
        runCatching {
            appContext.contentResolver.delete(destinationUri, null, null)
        }
    }

    private fun createTemporaryOutputFile(requestedName: String): File {
        val directory = File(appContext.cacheDir, "video-exports")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create export directory."
        }

        val safeBaseName = requestedName
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-', '.')
            .ifBlank { "lyric-captioner-output" }
        return File(directory, "$safeBaseName-${System.currentTimeMillis()}.mp4")
    }

    private fun inspectOutput(outputFile: File): ExportResult {
        val fileSize = outputFile.length()
        check(fileSize > MIN_VALID_OUTPUT_BYTES) { "The export created an empty MP4 file." }

        val extractor = MediaExtractor()
        val hasVideoTrack: Boolean
        val hasAudioTrack: Boolean
        try {
            extractor.setDataSource(outputFile.absolutePath)
            hasVideoTrack = (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
            hasAudioTrack = (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
        } finally {
            extractor.release()
        }
        check(hasVideoTrack) { "The export did not contain a video track." }
        check(hasAudioTrack) { "The export did not contain an audio track." }

        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(outputFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } finally {
            retriever.release()
        }
        check(durationMs > 0L) { "The exported MP4 has no playable duration." }
        return ExportResult(
            outputUri = android.net.Uri.EMPTY,
            fileSizeBytes = fileSize,
            durationMs = durationMs,
            hasAudioTrack = hasAudioTrack,
        )
    }

    private companion object {
        const val LOG_TAG = "Media3SubtitleExporter"
        const val MIN_VALID_OUTPUT_BYTES = 1_024L
    }
}

internal fun sanitizeMedia3DiagnosticMessage(message: String?): String {
    if (message == null) return "<none>"
    val controlsNormalized = buildString(message.length) {
        message.forEach { character ->
            append(if (character.code < 0x20) ' ' else character)
        }
    }
    val escaped = controlsNormalized
        .replace(QUOTED_SENSITIVE_PATH, "<redacted-path>")
        .replace(UNQUOTED_URI_WITH_COMMON_ROOT, "<redacted-uri>")
        .replace(URI_WITH_SPACES, "<redacted-uri>")
        .replace(URI, "<redacted-uri>")
        .replace(WINDOWS_FILE_PATH, "<redacted-path>")
        .replace(UNC_FILE_PATH, "<redacted-path>")
        .replace(UNIX_FILE_PATH, "<redacted-path>")
        .replace(UNQUOTED_PATH_WITH_COMMON_ROOT, "<redacted-path>")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return escaped
        .take(MAX_DIAGNOSTIC_MESSAGE_LENGTH)
        .trimEnd('\\')
}

private const val MAX_DIAGNOSTIC_MESSAGE_LENGTH = 256

private val QUOTED_SENSITIVE_PATH = Regex(
    """(?i)(?:\"(?:[A-Za-z][A-Za-z0-9+.-]*://|[A-Z]:[\\/]|\\\\|/)[^\"\r\n]*\"|'(?:[A-Za-z][A-Za-z0-9+.-]*://|[A-Z]:[\\/]|\\\\|/)[^'\r\n]*')""",
)
private val URI_WITH_SPACES = Regex(
    """(?i)\b[A-Za-z][A-Za-z0-9+.-]*://[^"']+?\.[A-Za-z0-9]{1,8}(?=$|[\s,;.)\]}])""",
)
private val UNQUOTED_URI_WITH_COMMON_ROOT = Regex(
    """(?i)\b[A-Za-z][A-Za-z0-9+.-]*://[^"']+?(?=\s+(?:at|or|and|because|from|to|in)\b|[\],;)]|$)""",
)
private val URI = Regex("""(?i)\b[A-Za-z][A-Za-z0-9+.-]*://\S+""")
private val WINDOWS_FILE_PATH = Regex(
    """(?i)(?<![A-Za-z0-9])(?:[A-Z]:[\\/])(?:[^\\/:\"'\r\n]+[\\/])+?[^\\/:\"'\r\n]+(?:\.[A-Za-z0-9]{1,8})(?=$|[\s,;.)\]}])""",
)
private val UNC_FILE_PATH = Regex(
    """(?i)(?<![A-Za-z0-9])\\\\(?:[^\\/\"'\r\n]+\\)+?[^\\/\"'\r\n]+(?:\.[A-Za-z0-9]{1,8})(?=$|[\s,;.)\]}])""",
)
private val UNIX_FILE_PATH = Regex(
    """(?i)(?<![A-Za-z0-9])/(?:[^/:\"'\r\n]+/)+?[^/:\"'\r\n]+(?:\.[A-Za-z0-9]{1,8})(?=$|[\s,;.)\]}])""",
)
private val UNQUOTED_PATH_WITH_COMMON_ROOT = Regex(
    """(?i)(?<![A-Za-z0-9])(?:[A-Z]:[\\/]|/(?:storage|data|sdcard|mnt|tmp|var|home|Users)[\\/]|\\\\)[^\"']+?(?=\s+(?:at|or|and|because|from|to|in)\b|[\],;.)]|$)""",
)

@OptIn(markerClass = [UnstableApi::class])
private class TimedBilingualTextOverlay(
    captions: List<CaptionCue>,
    style: SubtitleStyle,
) : TextOverlay() {
    private val timeline = CaptionTimeline(captions)
    private val emptyText = SpannableString("")
    private val renderedCues = captions.associate { cue ->
        cue.id to renderCue(cue, style)
    }
    private val settings = StaticOverlaySettings.Builder()
        .setBackgroundFrameAnchor(
            0f,
            (-1f + style.bottomMarginPercent.coerceIn(0, 40) / 50f),
        )
        .setOverlayFrameAnchor(0f, -1f)
        .setScale(
            style.fontSizeSp.coerceIn(14, 48) / 24f,
            style.fontSizeSp.coerceIn(14, 48) / 24f,
        )
        .build()

    override fun getText(presentationTimeUs: Long): SpannableString {
        val cue = timeline.cueAt(presentationTimeUs / 1_000L) ?: return emptyText
        return renderedCues.getValue(cue.id)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

    private fun renderCue(cue: CaptionCue, style: SubtitleStyle): SpannableString {
        val english = cue.english.trim()
        val chinese = cue.chinese.trim()
        val text = listOf(english, chinese).filter { it.isNotEmpty() }.joinToString("\n")
        return SpannableString(text).apply {
            setSpan(
                AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE,
            )
            setSpan(
                SubtitleShadowSpan(parseColor(style.outlineColorHex, Color.BLACK)),
                0,
                length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE,
            )
            if (english.isNotEmpty()) {
                setSpan(
                    ForegroundColorSpan(parseColor(style.primaryColorHex, Color.WHITE)),
                    0,
                    english.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (chinese.isNotEmpty()) {
                val start = if (english.isEmpty()) 0 else english.length + 1
                setSpan(
                    ForegroundColorSpan(parseColor(style.secondaryColorHex, Color.YELLOW)),
                    start,
                    length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    private fun parseColor(value: String, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }.getOrDefault(fallback)
    }
}

private class SubtitleShadowSpan(
    private val color: Int,
) : CharacterStyle(), UpdateAppearance {
    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.setShadowLayer(5f, 0f, 2f, color)
    }
}
