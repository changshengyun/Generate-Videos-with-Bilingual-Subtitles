package com.example.lyriccaptioner

import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.processing.AppPipelineFactory
import com.example.lyriccaptioner.processing.CaptionPipeline
import com.example.lyriccaptioner.processing.FfmpegKitSubtitleExporter
import com.example.lyriccaptioner.processing.TranslationBatchResult
import com.example.lyriccaptioner.processing.TranslationModule
import com.example.lyriccaptioner.project.ProjectArchive
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

class LocalAiInstrumentation : Instrumentation() {
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
            if (!inputArguments.getString(ARG_PREVIEW_INPUT).isNullOrBlank()) {
                runPreviewUiFlow(results)
            } else if (inputArguments.getString(ARG_INPUT).isNullOrBlank()) {
                runUiSmoke(results)
            } else {
                runBlocking { runLocalAiChain(results) }
            }
        }.onSuccess {
            finish(Activity.RESULT_OK, results)
        }.onFailure { error ->
            results.putString("failure", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun runUiSmoke(results: Bundle) {
        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        val root = activity.window.decorView
        check(root.width > 0 && root.height > 0) {
            "Production activity has no laid-out window: ${root.width}x${root.height}"
        }
        check(findComposeRoot(root)) { "Compose root was not found in the production activity." }
        val statusBarInset = root.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        check(statusBarInset > 0) { "Status bar inset was not reported by the activity window." }
        val titleNode = findAccessibilityNode(uiAutomation.rootInActiveWindow, "歌词字幕工作台")
            ?: error("Editor title was not exposed through the accessibility tree.")
        val titleBounds = Rect().also(titleNode::getBoundsInScreen)
        check(titleBounds.top >= statusBarInset) {
            "Editor title entered the status bar: titleTop=${titleBounds.top}, statusBarInset=$statusBarInset"
        }
        results.putInt("statusBarInset", statusBarInset)
        results.putInt("titleTop", titleBounds.top)
        results.putInt("titleBottom", titleBounds.bottom)
        val screenshot = uiAutomation.takeScreenshot()
        check(screenshot.width > 0 && screenshot.height > 0) {
            "Production UI screenshot is empty: ${screenshot.width}x${screenshot.height}"
        }
        results.putInt("windowWidth", root.width)
        results.putInt("windowHeight", root.height)
        results.putInt("screenshotWidth", screenshot.width)
        results.putInt("screenshotHeight", screenshot.height)
        results.putString("rootClass", root.javaClass.name)
        activity.finish()
    }

    private fun runPreviewUiFlow(results: Bundle) {
        val inputPath = inputArguments.getString(ARG_PREVIEW_INPUT)
            ?: error("Missing -e $ARG_PREVIEW_INPUT /sdcard/Download/preview.mp4")
        val inputFile = File(inputPath)
        check(inputFile.isFile && inputFile.length() > 0L) {
            "Preview input is missing or empty: $inputPath"
        }
        val scanLatch = CountDownLatch(1)
        MediaScannerConnection.scanFile(
            targetContext,
            arrayOf(inputPath),
            arrayOf("video/mp4"),
        ) { _, _ -> scanLatch.countDown() }
        check(scanLatch.await(10, TimeUnit.SECONDS)) { "Preview input was not indexed by MediaStore." }
        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        clickNode(waitForContentDescription("import_video"))
        clickNode(waitForText(inputFile.name))
        inputArguments.getString(ARG_PREVIEW_SRT)?.takeIf { it.isNotBlank() }?.let { srtPath ->
            val srtFile = File(srtPath)
            check(srtFile.isFile && srtFile.length() > 0L) { "Preview SRT is missing or empty: $srtPath" }
            clickNode(waitForContentDescription("import_srt", 15_000L))
            clickNode(waitForText(srtFile.name, 30_000L))
            results.putString("previewSrt", srtPath)
        }
        val fullscreen = waitForContentDescription("preview_fullscreen", 45_000L)
        val fullscreenBounds = Rect().also(fullscreen::getBoundsInScreen)
        check(fullscreenBounds.width() > 0 && fullscreenBounds.height() > 0) {
            "Fullscreen control has no visible bounds: $fullscreenBounds"
        }
        results.putString("normalScreenshot", saveScreenshot("preview-normal.png"))
        clickNode(fullscreen)
        val dialog = waitForContentDescription("preview_fullscreen_dialog", 10_000L)
        val dialogBounds = Rect().also(dialog::getBoundsInScreen)
        check(dialogBounds.width() > 0 && dialogBounds.height() > 0) {
            "Fullscreen preview dialog has no visible bounds: $dialogBounds"
        }
        results.putString("fullscreenScreenshot", saveScreenshot("preview-fullscreen.png"))
        exerciseMedia3Controls(results)
        check(findAccessibilityNode(uiAutomation.rootInActiveWindow, "Demo") == null) {
            "Demo preview content was exposed during the real media flow."
        }
        waitForContentDescription("preview_fullscreen", 10_000L)
        results.putString("restoredScreenshot", saveScreenshot("preview-restored.png"))
        results.putString("previewInput", inputPath)
        results.putString("previewFlow", "imported_media_fullscreen_exit")
        results.putInt("fullscreenWidth", dialogBounds.width())
        results.putInt("fullscreenHeight", dialogBounds.height())
        activity.finish()
    }

    private fun exerciseMedia3Controls(results: Bundle) {
        clickNode(waitForContentDescription("Play", 10_000L))
        SystemClock.sleep(750L)
        clickNode(waitForContentDescription("Pause", 5_000L))
        tapScreen(810, 1990)
        results.putString("media3Controls", "play_pause_seekbar_tap")
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            "Media3 preview back action was not dispatched."
        }
    }

    private fun tapScreen(x: Int, y: Int) {
        val command = uiAutomation.executeShellCommand("input tap $x $y")
        command.close()
    }

    private fun saveScreenshot(fileName: String): String {
        val directory = targetContext.getExternalFilesDir("preview-evidence")
            ?: error("External preview evidence directory is unavailable.")
        check(directory.exists() || directory.mkdirs()) { "Could not create screenshot directory." }
        val file = File(directory, fileName)
        val bitmap = uiAutomation.takeScreenshot()
        file.outputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not save screenshot: ${file.absolutePath}"
            }
        }
        bitmap.recycle()
        check(file.length() > 0L) { "Saved screenshot is empty: ${file.absolutePath}" }
        return file.absolutePath
    }

    private fun waitForContentDescription(description: String, timeoutMs: Long = 15_000L): AccessibilityNodeInfo {
        return waitForNode(timeoutMs) { root -> findAccessibilityNodeByContentDescription(root, description) }
    }

    private fun waitForText(text: String, timeoutMs: Long = 30_000L): AccessibilityNodeInfo {
        return waitForNode(timeoutMs) { root -> findAccessibilityNode(root, text) }
    }

    private fun waitForNode(
        timeoutMs: Long,
        finder: (AccessibilityNodeInfo?) -> AccessibilityNodeInfo?,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            finder(uiAutomation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(250L)
        }
        error("Timed out waiting for an accessibility node after ${timeoutMs}ms.")
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null) {
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            candidate = candidate.parent
        }
        error("Accessibility node was not clickable: ${node.text}/${node.contentDescription}")
    }

    private fun findComposeRoot(view: View): Boolean {
        if (view.javaClass.name.contains("AndroidComposeView")) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (findComposeRoot(view.getChildAt(index))) return true
            }
        }
        return false
    }

    private fun findAccessibilityNode(
        node: AccessibilityNodeInfo?,
        text: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNode(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun findAccessibilityNodeByContentDescription(
        node: AccessibilityNodeInfo?,
        description: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == description) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNodeByContentDescription(node.getChild(index), description)?.let { return it }
        }
        return null
    }

    private suspend fun runLocalAiChain(results: Bundle) {
        val appContext = targetContext.applicationContext
        val inputPath = inputArguments.getString(ARG_INPUT)
            ?: error("Missing -e $ARG_INPUT /data/local/tmp/source.mp4")
        val inputFile = File(inputPath)
        check(inputFile.isFile && inputFile.length() > 0L) { "Input video is missing or empty: $inputPath" }
        val inputUri = Uri.fromFile(inputFile)

        val modelStore = com.example.lyriccaptioner.processing.WhisperModelStore(appContext)
        modelStore.ensureBundledModel()
        val whisperStatus = modelStore.status()
        check(whisperStatus.modelInstalled) { whisperStatus.detail }
        check(whisperStatus.nativeLibraryReady) { whisperStatus.detail }
        results.putString("whisperModel", modelStore.selectedModel?.fileName.orEmpty())

        val translationProbe = AppPipelineFactory.createTranslationDefault(appContext)
        val probeStarted = System.currentTimeMillis()
        val probeChinese = translationProbe.translateEnglishToChinese("hello world")
        results.putLong("translationProbeMs", System.currentTimeMillis() - probeStarted)
        results.putString("translationProbe", probeChinese)
        check(probeChinese.isNotBlank()) { "Local translator returned an empty fixed-sentence result." }

        val asr = AppPipelineFactory.createAsrDefault(appContext)
        val asrStarted = System.currentTimeMillis()
        val captions = asr.recognize(inputUri)
        results.putLong("asrMs", System.currentTimeMillis() - asrStarted)
        results.putInt("asrCaptionCount", captions.size)
        check(captions.isNotEmpty()) { "Local ASR returned no captions." }

        val translationModule = TranslationModule(AppPipelineFactory.createTranslationDefault(appContext))
        val translateStarted = System.currentTimeMillis()
        val translated: TranslationBatchResult = translationModule.translateMissingChinese(captions)
        results.putLong("translateMs", System.currentTimeMillis() - translateStarted)
        results.putInt("translatedCount", translated.translatedCount)
        check(translated.translatedCount > 0) { "Local translation did not translate any ASR captions." }
        check(translated.captions.any { it.english.isNotBlank() && it.chinese.isNotBlank() }) {
            "No bilingual subtitle cue was produced."
        }

        val archive = ProjectArchive()
        val rawProject = archive.write(
            ProjectSnapshot(
                videoUri = inputUri.toString(),
                videoDurationMs = inspect(inputFile).durationMs,
                captions = translated.captions,
                exportProfile = ExportProfile(outputName = "local-ai-instrumentation.mp4"),
            ),
        )
        val restored = archive.read(rawProject)
        check(restored.captions.map { it.id } == translated.captions.map { it.id }) {
            "Project restore changed subtitle ids or order."
        }

        val outputFile = File(appContext.filesDir, "local-ai-instrumentation-output.mp4").apply {
            delete()
        }
        val exportStarted = System.currentTimeMillis()
        CaptionPipeline(FfmpegKitSubtitleExporter(appContext)).export(
            videoUri = inputUri,
            destinationUri = Uri.fromFile(outputFile),
            captions = restored.captions,
            exportProfile = restored.exportProfile,
            onStatus = {},
        )
        results.putLong("exportMs", System.currentTimeMillis() - exportStarted)

        val output = inspect(outputFile)
        check(output.fileSizeBytes > 1_024L) { "Exported MP4 is empty." }
        check(output.videoMime == "video/avc") { "Expected H.264 output, got ${output.videoMime}" }
        check(output.audioMime == "audio/mp4a-latm") { "Expected AAC output, got ${output.audioMime}" }
        check(output.durationMs > 0L) { "Exported MP4 has no duration." }
        verifyMedia3Playback(Uri.fromFile(outputFile))

        results.putLong("outputBytes", output.fileSizeBytes)
        results.putLong("outputDurationMs", output.durationMs)
        results.putString("outputVideoMime", output.videoMime)
        results.putString("outputAudioMime", output.audioMime)
        results.putString("outputPath", outputFile.absolutePath)
        results.putString("firstEnglish", restored.captions.first().english)
        results.putString("firstChinese", restored.captions.first { it.chinese.isNotBlank() }.chinese)
    }

    private fun verifyMedia3Playback(uri: Uri) {
        val latch = CountDownLatch(1)
        val errors = ArrayList<Throwable>()
        var player: ExoPlayer? = null
        runOnMainSync {
            player = ExoPlayer.Builder(targetContext).build()
            player?.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                            latch.countDown()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        errors += error
                        latch.countDown()
                    }
                },
            )
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.prepare()
        }
        check(latch.await(30, TimeUnit.SECONDS)) { "Media3 playback did not become ready." }
        runOnMainSync { player?.release() }
        check(errors.isEmpty()) { "Media3 playback failed: ${errors.first().message}" }
    }

    private fun inspect(file: File): Mp4Inspection {
        check(file.isFile && file.length() > 0L) { "MP4 file is missing or empty: ${file.absolutePath}" }
        val extractor = MediaExtractor()
        var videoMime = ""
        var audioMime = ""
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) videoMime = mime
                if (mime.startsWith("audio/")) audioMime = mime
            }
        } finally {
            extractor.release()
        }
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
        return Mp4Inspection(file.length(), durationMs, videoMime, audioMime)
    }

    private data class Mp4Inspection(
        val fileSizeBytes: Long,
        val durationMs: Long,
        val videoMime: String,
        val audioMime: String,
    )

    private companion object {
        const val ARG_INPUT = "input"
        const val ARG_PREVIEW_INPUT = "previewInput"
        const val ARG_PREVIEW_SRT = "previewSrt"
    }
}
