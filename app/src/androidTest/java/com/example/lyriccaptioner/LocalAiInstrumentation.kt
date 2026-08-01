package com.example.lyriccaptioner

import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.FileInputStream
import java.security.MessageDigest
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
            if (inputArguments.getString(ARG_IMPORT_ACCEPTANCE)?.toBoolean() == true) {
                if (inputArguments.getString(ARG_IMPORT_PHASE) == IMPORT_PHASE_RESTORE) {
                    runImportRestoreAcceptance(results)
                } else {
                    runImportAcceptance(results)
                }
            } else if (!inputArguments.getString(ARG_PREVIEW_INPUT).isNullOrBlank()) {
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
            runCatching { results.putString("failureScreenshot", saveScreenshot("import-failure.png")) }
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
        val titleNode = waitForText("歌词字幕工作台", 10_000L)
        val titleBounds = Rect().also(titleNode::getBoundsInScreen)
        check(titleBounds.top >= statusBarInset) {
            "Editor title entered the status bar: titleTop=${titleBounds.top}, statusBarInset=$statusBarInset"
        }
        verifyWorkbenchSemantics(results, statusBarInset)
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
        waitForText("歌词字幕工作台", 10_000L)
        verifyWorkbenchSemantics(results, statusBarInset = currentStatusBarInset(activity))
        results.putString("initialScreenshot", saveScreenshot("ui2-initial.png"))
        clickNode(waitForContentDescription("import_video"))
        clickNode(waitForText(inputFile.name))
        waitForText("视频预览", 45_000L)
        results.putString("importedScreenshot", saveScreenshot("ui2-imported.png"))
        inputArguments.getString(ARG_PREVIEW_SRT)?.takeIf { it.isNotBlank() }?.let { srtPath ->
            val srtFile = File(srtPath)
            check(srtFile.isFile && srtFile.length() > 0L) { "Preview SRT is missing or empty: $srtPath" }
            clickNode(waitForContentDescriptionWithScroll("import_srt", 20_000L))
            clickNode(waitForText(srtFile.name, 30_000L))
            results.putString("previewSrt", srtPath)
        }
        scrollToTop()
        val fullscreen = waitForContentDescription("preview_fullscreen", 45_000L)
        val fullscreenBounds = Rect().also(fullscreen::getBoundsInScreen)
        check(fullscreenBounds.width() > 0 && fullscreenBounds.height() > 0) {
            "Fullscreen control has no visible bounds: $fullscreenBounds"
        }
        clickNode(waitForContentDescription("workbench_subtitles"))
        waitForContentDescriptionWithScroll("style_controls", 20_000L)
        results.putString("subtitleScreenshot", saveScreenshot("ui2-subtitles.png"))
        scrollToTop()
        clickNode(waitForContentDescription("workbench_export"))
        waitForContentDescription("export_video")
        results.putString("exportScreenshot", saveScreenshot("ui2-export.png"))
        scrollToTop()
        clickNode(waitForContentDescription("workbench_import"))
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

    private fun runImportAcceptance(results: Bundle) {
        val inputPath = inputArguments.getString(ARG_IMPORT_INPUT)
            ?: error("Missing -e $ARG_IMPORT_INPUT /sdcard/Download/v2-import-test.mp4")
        val relinkPath = inputArguments.getString(ARG_IMPORT_RELINK)
            ?: error("Missing -e $ARG_IMPORT_RELINK /sdcard/Download/v2-import-relink.mp4")
        val srtPath = inputArguments.getString(ARG_IMPORT_SRT)
            ?: error("Missing -e $ARG_IMPORT_SRT /sdcard/Download/v2-import-test.srt")
        val inputFile = File(inputPath)
        val relinkFile = File(relinkPath)
        val srtFile = File(srtPath)
        check(inputFile.isFile && inputFile.length() > 0L) { "Import input is missing or empty: $inputPath" }
        check(relinkFile.isFile && relinkFile.length() > 0L) { "Relink input is missing or empty: $relinkPath" }
        check(srtFile.isFile && srtFile.length() > 0L) { "Import SRT is missing or empty: $srtPath" }
        scanVideo(inputPath)
        scanVideo(relinkPath)
        check(queryMediaStoreFromShell().any { (_, name) -> name == inputFile.name }) {
            "Prepared import video was not indexed by MediaStore: ${inputFile.name}"
        }
        check(queryMediaStoreFromShell().any { (_, name) -> name == relinkFile.name }) {
            "Prepared relink video was not indexed by MediaStore: ${relinkFile.name}"
        }
        // The emulator's Downloads provider only exposes scanner-provided text MIME
        // types to ACTION_OPEN_DOCUMENT. The content remains an SRT fixture; the
        // product reads and parses its text rather than trusting the provider MIME.
        scanDocument(srtPath, "text/plain")

        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        waitForText("歌词字幕工作台", 15_000L)
        verifyWorkbenchSemantics(results, currentStatusBarInset(activity))

        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        clickDocumentFile(inputFile.name)
        waitForText("视频预览", 45_000L)
        results.putString("importEntry", "documents_ui_video_picker")
        results.putString("importInput", inputPath)

        resetDocumentsUi()
        clickNode(waitForContentDescriptionWithScroll("import_srt", 20_000L))
        clickDocumentFile(srtFile.name)
        waitForText("字幕列表", 20_000L)
        results.putInt("importedCaptionCount", 2)

        scrollToTop()
        clickNode(waitForContentDescription("workbench_subtitles"))
        waitForContentDescriptionWithScroll("style_controls", 20_000L)
        clickNode(waitForContentDescriptionWithScroll("英文 #61D6FF", 20_000L))
        scrollToTop()

        clickNode(waitForContentDescription("workbench_export"))
        clickNode(waitForTextWithScroll("保存项目", 20_000L))
        confirmDocumentCreation()
        waitForTextStartingWith("项目状态：", 30_000L)
        val projectUri = waitForContentUriStatus(30_000L)
        val projectDisplayName = displayName(projectUri)
        results.putString("projectDisplayName", projectDisplayName)
        val projectSnapshot = ProjectArchive().read(
            targetContext.contentResolver.openInputStream(projectUri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Saved project archive cannot be read: $projectUri"),
        )
        val importedVideoUri = Uri.parse(projectSnapshot.videoUri ?: error("Saved project did not contain video URI."))
        check(targetContext.contentResolver.persistedUriPermissions.any { it.uri == importedVideoUri && it.isReadPermission }) {
            "Selected video did not retain a verified read permission: $importedVideoUri"
        }
        val sourceHashBefore = sha256(importedVideoUri)
        results.putString("projectUri", projectUri.toString())
        results.putString("importedVideoUri", importedVideoUri.toString())
        results.putString("persistedPermission", "verified")
        results.putString("sourceSha256Before", sourceHashBefore)

        clickNode(waitForText("导出视频", 20_000L))
        confirmDocumentCreation()
        waitForText("视频导出完成", 60_000L)
        val outputUri = waitForContentUriStatus(60_000L)
        val output = inspectUri(outputUri)
        check(output.fileSizeBytes > 0L) { "Product export is empty: $outputUri" }
        check(output.videoMime == "video/avc") { "Expected H.264 product export, got ${output.videoMime}" }
        check(output.audioMime == "audio/mp4a-latm") { "Expected AAC product export, got ${output.audioMime}" }
        check(output.durationMs > 0L) { "Product export has no duration." }
        verifyMedia3Playback(outputUri)
        check(sha256(importedVideoUri) == sourceHashBefore) { "Source SHA-256 changed during export." }
        check(isActionEnabledByText("分享视频")) { "Exported video was not exposed as shareable." }
        results.putString("exportUri", outputUri.toString())
        results.putLong("outputBytes", output.fileSizeBytes)
        results.putLong("outputDurationMs", output.durationMs)
        results.putString("outputVideoMime", output.videoMime)
        results.putString("outputAudioMime", output.audioMime)
        results.putString("media3Playback", "ready")
        results.putString("sourceSha256AfterExport", sha256(importedVideoUri))

        executeShell("rm -f $inputPath")
        results.putString("sourceDeletedOnlyForInvalidUriTest", "true")
        results.putString("restartBoundary", "ready_for_external_force_stop")
        activity.finish()
    }

    private fun runImportRestoreAcceptance(results: Bundle) {
        val relinkPath = inputArguments.getString(ARG_IMPORT_RELINK)
            ?: error("Missing -e $ARG_IMPORT_RELINK /sdcard/Download/v2-import-relink.mp4")
        val projectDisplayName = inputArguments.getString(ARG_IMPORT_PROJECT_NAME)
        val projectPrefix = inputArguments.getString(ARG_IMPORT_PROJECT_PREFIX) ?: PROJECT_PREFIX
        val relinkFile = File(relinkPath)
        check(relinkFile.isFile && relinkFile.length() > 0L) {
            "Relink input is missing or empty: $relinkPath"
        }
        scanVideo(relinkPath)

        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        waitForText("歌词字幕工作台", 20_000L)
        clickNode(waitForContentDescription("workbench_import"))
        resetDocumentsUi()
        clickNode(waitForContentDescription("open_project", 20_000L))
        if (projectDisplayName != null) {
            clickDocumentFile(projectDisplayName)
        } else {
            clickDocumentFileStartingWith(projectPrefix)
        }
        waitForTextStartingWith("项目已恢复：视频不可用", 30_000L)
        clickNode(waitForContentDescription("workbench_export", 20_000L))
        check(!isActionEnabledByText("分享视频")) {
            "Stale export remained available after invalid URI restore."
        }
        results.putString("invalidUriState", "unavailable_with_rebind")

        clickNode(waitForContentDescription("workbench_import", 20_000L))
        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        clickDocumentFile(relinkFile.name)
        waitForText("视频预览", 45_000L)
        waitForText("Hello from the emulator", 20_000L)
        clickNode(waitForContentDescription("workbench_export", 20_000L))
        check(!isActionEnabledByText("分享视频")) {
            "Old export remained available after relink."
        }
        results.putString("relinkState", "captions_preserved_export_invalidated")

        clickNode(waitForContentDescription("workbench_import", 20_000L))
        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            "DocumentsUI cancel action was not dispatched."
        }
        waitForText("视频预览", 20_000L)
        waitForText("Hello from the emulator", 20_000L)
        results.putString("cancelState", "project_preserved")
        results.putString("processRestart", "external_force_stop_and_relaunch")
        activity.finish()
    }

    private fun scanVideo(path: String) {
        scanDocument(path, "video/mp4")
    }

    private fun resetDocumentsUi() {
        executeShell("am force-stop com.google.android.documentsui")
        SystemClock.sleep(500L)
    }

    private fun scanDocument(path: String, mimeType: String) {
        val latch = CountDownLatch(1)
        MediaScannerConnection.scanFile(
            targetContext,
            arrayOf(path),
            arrayOf(mimeType),
        ) { _, _ -> latch.countDown() }
        check(latch.await(10, TimeUnit.SECONDS)) { "Test video was not indexed: $path" }
    }

    private fun confirmDocumentCreation() {
        clickNode(waitForAnyText(listOf("保存", "Save", "SAVE"), 20_000L))
        findAccessibilityNode(uiAutomation.rootInActiveWindow, "替换")?.let { clickNode(it) }
        findAccessibilityNode(uiAutomation.rootInActiveWindow, "Replace")?.let { clickNode(it) }
    }

    private fun clickDocumentFile(displayName: String) {
        waitForPackage("com.google.android.documentsui", 20_000L)
        val node = runCatching { waitForText(displayName, 5_000L) }.getOrNull()
        if (node != null) {
            tapNode(node)
            return
        }
        if (findAccessibilityNodeByContentDescription(uiAutomation.rootInActiveWindow, "Show roots") == null) {
            executeShell("input keyevent 4")
        }
        val roots = waitForContentDescription("Show roots", 10_000L)
        clickNode(roots)
        val downloads = waitForText("Downloads", 10_000L)
        clickTextOrTap(downloads)
        tapNode(waitForDocumentText(displayName, 30_000L))
    }

    private fun clickDocumentFileStartingWith(prefix: String) {
        waitForPackage("com.google.android.documentsui", 20_000L)
        if (findAccessibilityNodeStartingWith(uiAutomation.rootInActiveWindow, prefix) == null) {
            if (findAccessibilityNodeByContentDescription(uiAutomation.rootInActiveWindow, "Show roots") == null) {
                executeShell("input keyevent 4")
            }
            clickNode(waitForContentDescription("Show roots", 10_000L))
            clickTextOrTap(waitForText("Downloads", 10_000L))
        }
        tapNode(waitForDocumentTextStartingWith(prefix, 30_000L))
    }

    private fun tapNode(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            clickNode(node)
            return
        }
        tapScreen(bounds.centerX(), bounds.centerY())
        SystemClock.sleep(1_000L)
    }

    private fun waitForDocumentText(text: String, timeoutMs: Long): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNode(uiAutomation.rootInActiveWindow, text)?.let { return it }
            executeShell("input swipe 540 1850 540 600 400")
            SystemClock.sleep(500L)
        }
        error("Timed out waiting for DocumentsUI file: $text")
    }

    private fun waitForDocumentTextStartingWith(prefix: String, timeoutMs: Long): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNodeStartingWith(uiAutomation.rootInActiveWindow, prefix)?.let { return it }
            executeShell("input swipe 540 1850 540 600 400")
            SystemClock.sleep(500L)
        }
        error("Timed out waiting for DocumentsUI file prefix: $prefix")
    }

    private fun clickTextOrTap(node: AccessibilityNodeInfo) {
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null) {
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            candidate = candidate.parent
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        check(!bounds.isEmpty) { "Accessibility node has no screen bounds: ${node.text}/${node.contentDescription}" }
        tapScreen(bounds.centerX(), bounds.centerY())
        SystemClock.sleep(500L)
    }

    private fun waitForAnyText(texts: List<String>, timeoutMs: Long): AccessibilityNodeInfo =
        waitForNode(timeoutMs) { root -> texts.firstNotNullOfOrNull { findAccessibilityNode(root, it) } }

    private fun waitForTextStartingWith(prefix: String, timeoutMs: Long): AccessibilityNodeInfo =
        waitForNode(timeoutMs) { root -> findAccessibilityNodeStartingWith(root, prefix) }

    private fun waitForContentUriStatus(timeoutMs: Long): Uri {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            findContentUriText(uiAutomation.rootInActiveWindow)?.let { value ->
                return Uri.parse(value)
            }
            SystemClock.sleep(250L)
        }
        error("Saved project URI was not exposed by the product status")
    }

    private fun findContentUriText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        listOf(node.text?.toString(), node.contentDescription?.toString()).forEach { value ->
            value?.indexOf("content://")?.takeIf { it >= 0 }?.let { index ->
                return value.substring(index)
            }
        }
        for (index in 0 until node.childCount) {
            findContentUriText(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun waitForTextWithScroll(text: String, timeoutMs: Long): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNode(uiAutomation.rootInActiveWindow, text)?.let { return it }
            findScrollableNode(uiAutomation.rootInActiveWindow)
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(250L)
        }
        error("Timed out waiting for a scrollable accessibility node after ${timeoutMs}ms: $text")
    }

    private fun isActionEnabledByText(text: String): Boolean {
        var node: AccessibilityNodeInfo? = waitForText(text, 20_000L)
        while (node != null) {
            if (node.isClickable) return node.isEnabled
            node = node.parent
        }
        error("Text action is not attached to a clickable control: $text")
    }

    private fun findAccessibilityNodeStartingWith(node: AccessibilityNodeInfo?, prefix: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.startsWith(prefix) == true) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNodeStartingWith(node.getChild(index), prefix)?.let { return it }
        }
        return null
    }

    private fun waitForMediaDocument(displayName: String, timeoutMs: Long): Uri {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val collection = MediaStore.Files.getContentUri("external")
        while (SystemClock.uptimeMillis() < deadline) {
            targetContext.contentResolver.query(
                collection,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.DISPLAY_NAME}=?",
                arrayOf(displayName),
                "${MediaStore.Files.FileColumns._ID} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return ContentUris.withAppendedId(collection, cursor.getLong(0))
                }
            }
            SystemClock.sleep(300L)
        }
        error("Timed out waiting for DocumentsUI output: $displayName")
    }

    private fun waitForMediaDocumentStartingWith(prefix: String, timeoutMs: Long): Uri {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val collection = MediaStore.Files.getContentUri("external")
        while (SystemClock.uptimeMillis() < deadline) {
            queryMediaStoreFromShell().firstOrNull { (_, name) -> name.startsWith(prefix) }?.let { (id) ->
                return ContentUris.withAppendedId(collection, id)
            }
            SystemClock.sleep(300L)
        }
        error("Timed out waiting for DocumentsUI output prefix: $prefix")
    }

    private fun queryMediaStoreFromShell(): List<Pair<Long, String>> {
        val descriptor = uiAutomation.executeShellCommand(
            "content query --uri content://media/external/file --projection _id:_display_name:_data"
        )
        val output = FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        descriptor.close()
        return output.lineSequence().mapNotNull { line ->
            val id = Regex("_id=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val name = Regex("_display_name=([^,\\r\\n]+)").find(line)?.groupValues?.get(1)?.trim()
                ?: return@mapNotNull null
            id to name
        }.toList()
    }

    private fun displayName(uri: Uri): String {
        ContentUris.parseId(uri).let { id ->
            queryMediaStoreFromShell().firstOrNull { (rowId, _) -> rowId == id }?.second?.let { return it }
        }
        targetContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        error("Could not resolve display name for $uri")
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        targetContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: error("Cannot read media for SHA-256: $uri")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun inspectUri(uri: Uri): Mp4Inspection {
        val size = targetContext.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
            }
            total
        } ?: error("Cannot read product output: $uri")
        val extractor = MediaExtractor()
        var videoMime = ""
        var audioMime = ""
        try {
            extractor.setDataSource(targetContext, uri, null)
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
            retriever.setDataSource(targetContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
        return Mp4Inspection(size, durationMs, videoMime, audioMime)
    }

    private fun executeShell(command: String) {
        uiAutomation.executeShellCommand(command).close()
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

    private fun waitForContentDescriptionWithScroll(
        description: String,
        timeoutMs: Long,
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            findAccessibilityNodeByContentDescription(uiAutomation.rootInActiveWindow, description)?.let { return it }
            findScrollableNode(uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(250L)
        }
        error("Timed out waiting for a scrollable accessibility node after ${timeoutMs}ms: $description")
    }

    private fun scrollToTop() {
        repeat(32) {
            val scrollable = findScrollableNode(uiAutomation.rootInActiveWindow) ?: return
            if (!scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return
            SystemClock.sleep(100L)
        }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (index in 0 until node.childCount) {
            findScrollableNode(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun currentStatusBarInset(activity: Activity): Int {
        return activity.window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
    }

    private fun verifyWorkbenchSemantics(results: Bundle, statusBarInset: Int) {
        val root = uiAutomation.rootInActiveWindow ?: error("Accessibility root is unavailable.")
        val screenshot = uiAutomation.takeScreenshot()
        val requiredTargets = listOf(
            "workbench_import",
            "workbench_asr",
            "workbench_subtitles",
            "workbench_export",
            "import_video",
            "caption_list",
        )
        requiredTargets.forEach { description ->
            val node = findAccessibilityNodeByContentDescription(root, description)
                ?: error("Missing required UI semantic: $description")
            val bounds = Rect().also(node::getBoundsInScreen)
            check(bounds.width() > 0 && bounds.height() > 0) {
                "UI semantic has no visible bounds: $description $bounds"
            }
            check(bounds.left >= 0 && bounds.top >= statusBarInset &&
                bounds.right <= screenshot.width && bounds.bottom <= screenshot.height) {
                "UI semantic is outside safe screen bounds: $description $bounds screen=${screenshot.width}x${screenshot.height} inset=$statusBarInset"
            }
            check(bounds.width() >= 48 && bounds.height() >= 48) {
                "UI semantic touch target is smaller than 48dp at ${screenshot.width}x${screenshot.height}: $description $bounds"
            }
        }
        results.putString("workbenchSemantics", requiredTargets.joinToString(","))
        results.putInt("safeContentWidth", screenshot.width)
        results.putInt("safeContentHeight", screenshot.height)
        results.putInt("safeStatusBarInset", statusBarInset)
        screenshot.recycle()
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

    private fun waitForPackage(packageName: String, timeoutMs: Long) {
        waitForNode(timeoutMs) { root ->
            root?.takeIf { it.packageName?.toString() == packageName }
        }
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
        const val ARG_IMPORT_ACCEPTANCE = "importAcceptance"
        const val ARG_IMPORT_PHASE = "importPhase"
        const val IMPORT_PHASE_RESTORE = "restore"
        const val ARG_IMPORT_INPUT = "importInput"
        const val ARG_IMPORT_RELINK = "importRelink"
        const val ARG_IMPORT_SRT = "importSrt"
        const val ARG_IMPORT_PROJECT_NAME = "importProjectName"
        const val ARG_IMPORT_PROJECT_PREFIX = "importProjectPrefix"
        const val PROJECT_PREFIX = "lyric-captioner-project"
        const val OUTPUT_PREFIX = "lyric-captioner-output"
    }
}
