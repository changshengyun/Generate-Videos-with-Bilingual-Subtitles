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
import android.provider.DocumentsContract
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
import com.example.lyriccaptioner.processing.ExtractedAudio
import com.example.lyriccaptioner.processing.TranslationBatchResult
import com.example.lyriccaptioner.processing.TranslationModule
import com.example.lyriccaptioner.processing.WhisperLocalSpeechRecognizer
import com.example.lyriccaptioner.processing.WhisperModelStore
import com.example.lyriccaptioner.processing.WhisperAsrModule
import com.example.lyriccaptioner.project.ProjectArchive
import com.example.lyriccaptioner.audio.Pcm16WavWriter
import com.example.lyriccaptioner.processing.CaptionProject
import com.example.lyriccaptioner.processing.ExportEngine
import com.example.lyriccaptioner.processing.ExportResult
import com.example.lyriccaptioner.processing.enhancement.byok.AndroidKeystoreDeepSeekKeyStore
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManagerImpl
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyAvailability
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyProbe
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStore
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStoreHealth
import com.example.lyriccaptioner.processing.enhancement.byok.EncryptedDeepSeekKeyRecord
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.crypto.SecretKeyFactory
import android.security.keystore.KeyInfo

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
            if (inputArguments.getString(ARG_BYOK_SECURITY)?.toBoolean() == true) {
                runBlocking { runByokSecurityAcceptance(results) }
            } else if (inputArguments.getString(ARG_IMPORT_ACCEPTANCE)?.toBoolean() == true) {
                if (inputArguments.getString(ARG_IMPORT_PHASE) == IMPORT_PHASE_RESTORE) {
                    runImportRestoreAcceptance(results)
                } else {
                    runImportAcceptance(results)
                }
            } else if (inputArguments.getString(ARG_WHISPER_CANCEL)?.toBoolean() == true) {
                runBlocking { runWhisperCancellationAcceptance(results) }
            } else if (inputArguments.getString(ARG_ILLEGAL_MEDIA)?.toBoolean() == true) {
                runIllegalMediaAcceptance(results)
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
            throw AssertionError("Instrumentation acceptance failed", error)
        }
    }

    private suspend fun runByokSecurityAcceptance(results: Bundle) {
        val context = targetContext.applicationContext
        val recordFile = File(context.noBackupFilesDir, BYOK_TEST_RECORD)
        val store = AndroidKeystoreDeepSeekKeyStore(context, BYOK_TEST_ALIAS, BYOK_TEST_RECORD)
        runCatching { store.delete() }
        try {
            val first = store.writeEncrypted(BYOK_SENTINEL_ONE)
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            check(keyStore.containsAlias(BYOK_TEST_ALIAS)) { "Test-only Android Keystore alias was not created." }
            val secretKey = keyStore.getKey(BYOK_TEST_ALIAS, null)
            val keyInfo = SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")
                .getKeySpec(secretKey as javax.crypto.SecretKey, KeyInfo::class.java) as KeyInfo
            check(keyInfo.keySize == 256) { "Android Keystore AES key size was not 256 bits." }
            check(first.iv.size == 12) { "AES-GCM IV was not 12 bytes." }
            check(recordFile.isFile && recordFile.length() > 0L) { "Test-owned encrypted record is missing." }
            val encryptedRecordBytes = recordFile.length()
            check(!recordFile.readBytes().toString(Charsets.UTF_8).contains(BYOK_SENTINEL_ONE)) {
                "Encrypted record exposed the synthetic sentinel."
            }
            check(store.decrypt() == BYOK_SENTINEL_ONE) { "Production store round-trip failed." }
            val restarted = AndroidKeystoreDeepSeekKeyStore(context, BYOK_TEST_ALIAS, BYOK_TEST_RECORD)
            check(restarted.decrypt() == BYOK_SENTINEL_ONE) { "New store instance could not recover the record." }
            val manager = DeepSeekByokManagerImpl(restarted, DeepSeekKeyProbe { })
            check(manager.withDecryptedKey { it == BYOK_SENTINEL_ONE }) {
                "withDecryptedKey did not recover the synthetic sentinel."
            }

            val second = restarted.writeEncrypted(BYOK_SENTINEL_TWO)
            check(!first.iv.contentEquals(second.iv)) { "Replacement reused the AES-GCM IV." }
            val secondIv = second.iv.copyOf()
            tamperCiphertext(recordFile, second.iv.size)
            check(manager.status().state == DeepSeekKeyState.NEEDS_REENTRY) {
                "Ciphertext corruption did not enter NEEDS_REENTRY."
            }
            val recoveredFromCorruption = manager.validateAndSave(BYOK_SENTINEL_THREE)
            check(recoveredFromCorruption.state == DeepSeekKeyState.CONFIGURED) {
                "Re-entry after ciphertext corruption failed."
            }
            val recoveredRecord = requireNotNull(restarted.readEncrypted())
            check(!secondIv.contentEquals(recoveredRecord.iv)) { "Corruption recovery reused the prior IV." }

            keyStore.deleteEntry(BYOK_TEST_ALIAS)
            check(manager.status().state == DeepSeekKeyState.NEEDS_REENTRY) {
                "Alias loss did not enter NEEDS_REENTRY."
            }
            val recoveredFromAliasLoss = manager.validateAndSave(BYOK_SENTINEL_FOUR)
            check(recoveredFromAliasLoss.state == DeepSeekKeyState.CONFIGURED) {
                "Re-entry after alias loss failed."
            }
            check(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(BYOK_TEST_ALIAS)) {
                "Re-entry did not create a replacement alias."
            }

            val deleteBlocker = File(recordFile, "blocker")
            check(recordFile.delete() && recordFile.mkdir()) { "Could not create the test-owned delete blocker." }
            deleteBlocker.writeBytes(byteArrayOf(1))
            try {
                val deleteFailure = runCatching { manager.delete() }.exceptionOrNull()
                check(deleteFailure != null) { "Production delete failure was reported as success." }
                val renderedFailure = deleteFailure.stackTraceToString()
                check(!renderedFailure.contains(BYOK_SENTINEL_FOUR) && !renderedFailure.contains(recordFile.path)) {
                    "Production delete failure exposed secret or storage path details."
                }
                check(manager.status().state == DeepSeekKeyState.NEEDS_REENTRY) {
                    "Production delete failure did not leave a deterministic safe state."
                }
            } finally {
                deleteBlocker.delete()
                recordFile.delete()
            }
            check(manager.validateAndSave(BYOK_SENTINEL_FOUR).state == DeepSeekKeyState.CONFIGURED) {
                "Could not restore the test-owned record after delete-failure coverage."
            }
            val deleted = manager.delete()
            check(deleted.state == DeepSeekKeyState.UNCONFIGURED) { "Production delete did not return UNCONFIGURED." }
            check(!recordFile.exists()) { "Production delete left the test-owned record." }
            check(!KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(BYOK_TEST_ALIAS)) {
                "Production delete left the test-only alias."
            }
            check(
                DeepSeekByokManagerImpl(
                    AndroidKeystoreDeepSeekKeyStore(context, BYOK_TEST_ALIAS, BYOK_TEST_RECORD),
                    DeepSeekKeyProbe { },
                ).status().state == DeepSeekKeyState.UNCONFIGURED,
            ) { "New manager instance did not observe UNCONFIGURED after delete." }

            runViewModelCancellationProbe(results)
            runByokUiSecurityProbe(results)
            results.putString("byokKeystore", "AES-256-GCM/AndroidKeyStore")
            results.putString("byokAlias", "test-only")
            results.putString("byokRecordCategory", "noBackupFilesDir/test-owned")
            results.putLong("byokRecordBytes", encryptedRecordBytes)
            results.putString("byokIvRotation", "different")
            results.putString("byokCorruption", "NEEDS_REENTRY/recovered")
            results.putString("byokAliasLoss", "NEEDS_REENTRY/recovered")
            results.putString("byokDelete", "record-and-alias-absent")
            results.putString("byokDeleteFailure", "visible-sanitized-NEEDS_REENTRY")
        } finally {
            runCatching { store.delete() }
            runCatching { recordFile.delete() }
        }
    }

    private suspend fun runViewModelCancellationProbe(results: Bundle) {
        val store = InstrumentationTrackingStore()
        val probeEntered = CompletableDeferred<Unit>()
        val probeRelease = CompletableDeferred<Unit>()
        val manager = DeepSeekByokManagerImpl(
            store,
            DeepSeekKeyProbe {
                probeEntered.complete(Unit)
                probeRelease.await()
            },
        )
        lateinit var viewModel: MainViewModel
        runOnMainSync {
            viewModel = MainViewModel(
                context = targetContext,
                pipeline = CaptionPipeline(
                    object : ExportEngine {
                        override suspend fun export(project: CaptionProject, outputUri: Uri): ExportResult =
                            error("unused")
                    },
                ),
                deepSeekManager = manager,
            )
            viewModel.saveDeepSeekKey(BYOK_SENTINEL_ONE)
        }
        withTimeout(10_000L) { probeEntered.await() }
        runOnMainSync { viewModel.cancelDeepSeekKeyInput() }
        withTimeout(10_000L) {
            while (viewModel.deepSeekKeyUi.value.state == DeepSeekKeyState.VALIDATING_NEW_KEY) delay(10L)
        }
        probeRelease.complete(Unit)
        delay(100L)
        check(store.writeCount.get() == 0) { "Cancelled ViewModel validation wrote a record." }
        check(viewModel.deepSeekKeyUi.value.state == DeepSeekKeyState.UNCONFIGURED) {
            "Cancelled ViewModel validation did not return to UNCONFIGURED."
        }
        results.putString("byokCancellationJob", "cancelled-and-joined")
        results.putString("byokProbe", "entered-then-cancelled")
        results.putInt("byokWriteCountAfterCancel", store.writeCount.get())
    }

    private fun runByokUiSecurityProbe(results: Bundle) {
        val activity = startActivitySync(
            Intent(targetContext, ByokSecurityTestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        clickNode(waitForText("配置", 10_000L))
        val passwordField = waitForContentDescription("deepseek_api_key_input", 10_000L)
        val passwordNode = findPasswordNode(passwordField) ?: findPasswordNode(uiAutomation.rootInActiveWindow)
        check(passwordNode != null) {
            "DeepSeek input is not exposed as a password field."
        }
        val inputArguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, BYOK_SENTINEL_ONE)
        }
        check(passwordNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, inputArguments)) {
            "Synthetic password input could not be entered."
        }
        waitForIdleSync()
        check(findAccessibilityNode(uiAutomation.rootInActiveWindow, BYOK_SENTINEL_ONE) == null) {
            "Full synthetic key appeared in UI semantics."
        }
        val maskedScreenshot = uiAutomation.takeScreenshot()
        check(maskedScreenshot.width > 0 && maskedScreenshot.height > 0) { "Masked UI screenshot was empty." }
        clickNode(waitForContentDescription("deepseek_key_cancel", 10_000L))
        waitForIdleSync()
        check(ByokSecurityTestActivity.cancelInvoked.get()) { "VALIDATING_NEW_KEY cancel action was not invoked." }
        check(findAccessibilityNode(uiAutomation.rootInActiveWindow, BYOK_SENTINEL_ONE) == null) {
            "Full synthetic key remained after cancellation."
        }
        val clearedPassword = findPasswordNode(uiAutomation.rootInActiveWindow)
        check(clearedPassword?.text.isNullOrEmpty()) { "Password input was not cleared after cancellation." }
        results.putString("byokUiPassword", "password-semantics")
        results.putString("byokUiMaskedSuffix", "last-four-only")
        results.putString("byokUiCancel", "visible-and-invoked")
        results.putString("byokUiInputClear", "cleared-after-cancel")
        results.putString("byokUiScreenshot", "masked-and-nonempty")
        activity.finish()
    }

    private fun findPasswordNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isPassword) return node
        for (index in 0 until node.childCount) {
            findPasswordNode(node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun tamperCiphertext(recordFile: File, ivSize: Int) {
        RandomAccessFile(recordFile, "rw").use { file ->
            val ciphertextOffset = 20L + ivSize
            file.seek(ciphertextOffset)
            val original = file.read()
            check(original >= 0) { "Test-owned ciphertext was empty." }
            file.seek(ciphertextOffset)
            file.write(original xor 0x01)
        }
    }

    private class InstrumentationTrackingStore : DeepSeekKeyStore {
        private var record: EncryptedDeepSeekKeyRecord? = null
        private var plaintext: String? = null
        val writeCount = AtomicInteger(0)

        override fun readEncrypted(): EncryptedDeepSeekKeyRecord? = record
        override fun health(): DeepSeekKeyStoreHealth = if (record == null) {
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.ABSENT)
        } else {
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.AVAILABLE, record?.maskedKey)
        }
        override fun writeEncrypted(apiKey: String): EncryptedDeepSeekKeyRecord {
            writeCount.incrementAndGet()
            plaintext = apiKey
            return EncryptedDeepSeekKeyRecord(
                ciphertext = apiKey.encodeToByteArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray(),
                iv = ByteArray(12) { it.toByte() },
                maskedKey = "••••••••" + apiKey.takeLast(4),
            ).also { record = it }
        }
        override fun decrypt(): String? = plaintext
        override fun delete() {
            record = null
            plaintext = null
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

    private suspend fun runWhisperCancellationAcceptance(results: Bundle) {
        val store = WhisperModelStore(targetContext)
        store.ensureBundledModel()
        check(store.status().localRecognitionReady) {
            "Real Whisper cancellation requires the selected model and JNI library."
        }
        val audioFile = File(targetContext.cacheDir, "whisper-cancel-${System.currentTimeMillis()}.wav")
        val sample = ShortArray(16_000) { index ->
            (kotlin.math.sin(index * 2.0 * Math.PI * 440.0 / 16_000.0) * 8_000.0).toInt().toShort()
        }
        Pcm16WavWriter(audioFile, sampleRate = 16_000, channelCount = 1).use { writer ->
            repeat(10) { writer.write(sample) }
        }
        check(audioFile.length() > 100_000L) { "Long cancellation fixture was not created." }

        val completion = AtomicReference<Throwable?>(null)
        val asr = WhisperAsrModule(
            runtimeStatus = store.status(),
            audioExtractor = object : com.example.lyriccaptioner.processing.AudioExtractor {
                override suspend fun extract(videoUri: Uri): ExtractedAudio = ExtractedAudio(
                    uri = Uri.fromFile(audioFile),
                    sampleRate = 16_000,
                    channels = 1,
                    filePath = audioFile.absolutePath,
                    deleteFileAfterUse = true,
                )
            },
            speechRecognizer = WhisperLocalSpeechRecognizer(store.modelFile.absolutePath),
        )
        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                asr.recognize(Uri.EMPTY)
                error("Real Whisper returned normally after cancellation was requested.")
            } catch (error: Throwable) {
                completion.set(error)
                throw error
            }
        }
        check(waitForNativeLog("event=whisper_jni_inference_started", 90_000L)) {
            "Real Whisper did not enter whisper_full within the model-load timeout."
        }
        val startedAt = System.currentTimeMillis()
        delay(500L)
        job.cancel()
        withTimeout(120_000L) { job.join() }
        val elapsedMs = System.currentTimeMillis() - startedAt
        check(job.isCancelled) { "Real Whisper job was not cancelled." }
        check(completion.get() is java.util.concurrent.CancellationException) {
            "Native Whisper did not return CancellationException: ${completion.get()}"
        }
        check(!audioFile.exists()) {
            "Cancellation left the temporary WAV file behind: ${audioFile.absolutePath}"
        }
        delay(1_000L)
        val nativeLog = readShell("logcat -d -s WhisperJNI:I *:S")
        check(nativeLog.contains("event=whisper_full_exited")) {
            "Native whisper_full exit was not observed in logcat."
        }
        check(nativeLog.contains("event=whisper_jni_transcribe_cancelled")) {
            "Native Whisper cancellation event was not observed in logcat."
        }
        results.putLong("whisperCancelMs", elapsedMs)
        results.putString("whisperCancel", "native_whisper_full_exited_and_cancelled")
        results.putString("whisperTempAudio", "deleted")
        results.putString("whisperModel", store.selectedModel?.fileName.orEmpty())
    }

    private fun readShell(command: String): String {
        val descriptor = uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            .also { descriptor.close() }
    }

    private fun waitForNativeLog(marker: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (readShell("logcat -d -s WhisperJNI:I *:S").contains(marker)) return true
            SystemClock.sleep(500L)
        }
        return false
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
        val relinkFile = File(relinkPath)
        val srtFile = File(srtPath)
        check(relinkFile.isFile && relinkFile.length() > 0L) { "Relink input is missing or empty: $relinkPath" }
        check(srtFile.isFile && srtFile.length() > 0L) { "Import SRT is missing or empty: $srtPath" }
        val sourceUri = scanVideo(inputPath)
        val sourceHashBefore = sha256(sourceUri)
        val inputFile = File(inputPath)
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
        val importedCaptionState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        val importedCaptionCount = Regex("caption_count=(\\d+)")
            .find(importedCaptionState)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Caption instrumentation did not expose a measured count: $importedCaptionState")
        check(importedCaptionCount == 2) { "Expected two imported captions, got $importedCaptionCount" }
        results.putInt("importedCaptionCount", importedCaptionCount)

        scrollToTop()
        clickNode(waitForContentDescription("workbench_subtitles"))
        waitForContentDescriptionWithScroll("style_controls", 20_000L)
        clickNode(waitForContentDescriptionWithScroll("英文 #61D6FF", 20_000L))
        val importedStyleState = waitForContentDescriptionContaining(
            prefix = "style_state:",
            expected = "#61D6FF",
            timeoutMs = 20_000L,
        ).contentDescription.toString()
        results.putString("importedStyleState", importedStyleState)
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
        check(sha256(importedVideoUri) == sourceHashBefore) {
            "The test-owned imported fixture differs from the original source."
        }
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
        check(sha256(sourceUri) == sourceHashBefore) { "Source SHA-256 changed during export." }
        check(isActionEnabledByText("分享视频")) { "Exported video was not exposed as shareable." }
        results.putString("exportUri", outputUri.toString())
        results.putLong("outputBytes", output.fileSizeBytes)
        results.putLong("outputDurationMs", output.durationMs)
        results.putString("outputVideoMime", output.videoMime)
        results.putString("outputAudioMime", output.audioMime)
        results.putString("media3Playback", "ready")
        results.putString("sourceSha256AfterExport", sha256(sourceUri))
        results.putString("sourceFixture", "host_prepared_only_not_deleted_by_product_or_test")
        check(sha256(sourceUri) == sourceHashBefore) { "Source SHA-256 changed during acceptance." }
        results.putString("restartBoundary", "ready_for_external_force_stop")
        activity.finish()
    }

    private fun runIllegalMediaAcceptance(results: Bundle) {
        val validPath = inputArguments.getString(ARG_ILLEGAL_VALID)
            ?: error("Missing -e $ARG_ILLEGAL_VALID")
        val srtPath = inputArguments.getString(ARG_ILLEGAL_SRT)
        val invalidFixtures = listOf(
            "non_video" to inputArguments.getString(ARG_ILLEGAL_NON_VIDEO),
            "empty" to inputArguments.getString(ARG_ILLEGAL_EMPTY),
            "unreadable" to inputArguments.getString(ARG_ILLEGAL_UNREADABLE),
            "over_limit" to inputArguments.getString(ARG_ILLEGAL_OVER_LIMIT),
        )
        check(invalidFixtures.all { it.second != null }) { "All four illegal media fixture paths are required." }

        val activity = startActivitySync(
            Intent(targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        waitForIdleSync()
        waitForContentDescription("import_video", 20_000L)
        scanVideo(validPath)
        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        clickDocumentFile(File(validPath).name)
        waitForContentDescription("preview_fullscreen", 45_000L)

        srtPath?.takeIf { it.isNotBlank() }?.let { path ->
            scanDocument(path, "text/plain")
            clickNode(waitForContentDescriptionWithScroll("import_srt", 20_000L))
            clickDocumentFile(File(path).name)
            waitForContentDescriptionStartingWith("caption_state:", 20_000L)
        }
        val baselineState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        results.putString("baselineState", baselineState)

        invalidFixtures.forEach { (label, pathValue) ->
            val path = requireNotNull(pathValue)
            val sourceUri = scanVideo(path)
            val beforeHash = runCatching { sha256(sourceUri) }.getOrNull()
            resetDocumentsUi()
            clickNode(waitForContentDescription("import_video"))
            clickDocumentFile(
                File(path).name,
                beforeSelection = if (label == "unreadable") {
                    {
                        // MediaProvider can read shared-storage mode-000 files as media_rw.
                        // Revoke the fixture after DocumentsUI has located it but before the
                        // product receives the URI, proving the unavailable-URI path.
                        executeShell("rm -f ${path.replace(" ", "\\ ")}")
                    }
                } else {
                    null
                },
            )
            val expectedStatus = if (label == "over_limit") {
                waitForTextContainingAny(
                    listOf("longer than 5 minutes", "超过 5 分钟", "视频导入失败"),
                    30_000L,
                )
            } else {
                waitForImportRejectionStatus(30_000L)
            }
            val afterState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
                .contentDescription.toString()
            check(afterState == baselineState) {
                "Illegal media '$label' changed the project/editor state. before=$baselineState after=$afterState"
            }
            val afterHash = runCatching { sha256(sourceUri) }.getOrNull()
            if (label == "unreadable") {
                check(afterHash == null) { "Unreadable fixture became readable after revocation." }
            } else {
                check(beforeHash == afterHash) { "Illegal media '$label' changed its source bytes." }
            }
            results.putString("$label", "rejected:${expectedStatus.text}")
            results.putString("${label}State", afterState)
        }
        results.putString("illegalMedia", "non_video_empty_unreadable_over_limit_rejected")
        activity.finish()
    }

    private fun waitForImportRejectionStatus(timeoutMs: Long): AccessibilityNodeInfo =
        waitForNode(timeoutMs) { root ->
            listOf("视频导入失败", "Could not import video", "Video import failed")
                .firstNotNullOfOrNull { prefix -> findAccessibilityNodeStartingWith(root, prefix) }
        }

    private fun runImportRestoreAcceptance(results: Bundle) {
        val relinkPath = inputArguments.getString(ARG_IMPORT_RELINK)
            ?: error("Missing -e $ARG_IMPORT_RELINK /sdcard/Download/v2-import-relink.mp4")
        val projectPrefix = inputArguments.getString(ARG_IMPORT_PROJECT_PREFIX) ?: PROJECT_PREFIX
        val projectPath = inputArguments.getString(ARG_IMPORT_PROJECT_PATH)
        val expectUnavailable = inputArguments.getString(ARG_IMPORT_EXPECT_UNAVAILABLE)
            ?.toBooleanStrictOrNull() ?: true
        val projectDisplayName = inputArguments.getString(ARG_IMPORT_PROJECT_NAME)
            ?: inputArguments.getString(ARG_IMPORT_PROJECT_SUFFIX)?.let { "$projectPrefix ($it).lcp" }
        val relinkFile = File(relinkPath)
        check(relinkFile.isFile && relinkFile.length() > 0L) {
            "Relink input is missing or empty: $relinkPath"
        }
        scanVideo(relinkPath)
        projectPath?.let { scanDocument(it, "text/plain") }

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
        if (!expectUnavailable) {
            waitForContentDescription("preview_fullscreen", 30_000L)
            val restoredState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
                .contentDescription.toString()
            check(stateField(restoredState, "video").startsWith("content://")) {
                "Project restore did not expose a playable persisted video URI: $restoredState"
            }
            clickNode(waitForContentDescription("preview_fullscreen", 20_000L))
            waitForContentDescription("preview_fullscreen_dialog", 20_000L)
            exerciseMedia3Controls(results)
            results.putString("restoreState", "video_available_media3_ready")
            results.putString("restoreSnapshot", restoredState)
            activity.finish()
            return
        }
        waitForTextStartingWith("项目已恢复：视频不可用", 30_000L)
        clickNode(waitForContentDescription("workbench_export", 20_000L))
        check(!isActionEnabledByText("分享视频")) {
            "Stale export remained available after invalid URI restore."
        }
        val beforeRelinkState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        results.putString("invalidUriState", "unavailable_with_rebind")

        clickNode(waitForContentDescription("workbench_import", 20_000L))
        scanVideo(relinkPath)
        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        clickDocumentFile(relinkFile.name)
        waitForText("视频预览", 45_000L)
        val afterRelinkState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        check(stateField(beforeRelinkState, "captions") == stateField(afterRelinkState, "captions")) {
            "Relink changed measured caption content, IDs, timing, or confirmation state."
        }
        check(stateField(beforeRelinkState, "style") == stateField(afterRelinkState, "style")) {
            "Relink changed measured subtitle style state."
        }
        results.putString("relinkCaptionState", stateField(afterRelinkState, "captions"))
        results.putString("relinkStyleState", stateField(afterRelinkState, "style"))
        clickNode(waitForContentDescription("workbench_export", 20_000L))
        check(!isActionEnabledByText("分享视频")) {
            "Old export remained available after relink."
        }
        results.putString("relinkState", "captions_preserved_export_invalidated")

        clickNode(waitForContentDescription("workbench_import", 20_000L))
        resetDocumentsUi()
        clickNode(waitForContentDescription("import_video"))
        val cancelBeforeState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            "DocumentsUI cancel action was not dispatched."
        }
        waitForText("视频预览", 20_000L)
        val cancelAfterState = waitForContentDescriptionStartingWith("caption_state:", 20_000L)
            .contentDescription.toString()
        check(cancelAfterState == cancelBeforeState) {
            "Picker cancellation changed the measured project/editor state."
        }
        results.putString("cancelSnapshot", cancelAfterState)
        results.putString("cancelState", "project_preserved")
        results.putString("processRestart", "external_force_stop_and_relaunch")
        activity.finish()
    }

    private fun scanVideo(path: String): Uri {
        return scanDocument(path, "video/mp4")
    }

    private fun resetDocumentsUi() {
        executeShell("am force-stop com.google.android.documentsui")
        SystemClock.sleep(500L)
    }

    private fun scanDocument(path: String, mimeType: String): Uri {
        val latch = CountDownLatch(1)
        var scannedUri: Uri? = null
        MediaScannerConnection.scanFile(
            targetContext,
            arrayOf(path),
            arrayOf(mimeType),
        ) { _, uri ->
            scannedUri = uri
            latch.countDown()
        }
        check(latch.await(10, TimeUnit.SECONDS)) { "Test video was not indexed: $path" }
        val uri = scannedUri ?: error("Media scanner did not return a content URI: $path")
        val displayName = File(path).name
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (queryMediaStoreFromShell().any { (_, name) -> name == displayName }) return uri
            SystemClock.sleep(250L)
        }
        error("Media scanner callback completed but the document was not queryable: $path")
    }

    private fun confirmDocumentCreation() {
        clickNode(waitForAnyText(listOf("保存", "Save", "SAVE"), 20_000L))
        check(findAccessibilityNode(uiAutomation.rootInActiveWindow, "替换") == null)
        check(findAccessibilityNode(uiAutomation.rootInActiveWindow, "Replace") == null) {
            "DocumentsUI offered Replace; product must use a unique destination and never replace a user file."
        }
    }

    private fun clickDocumentFile(
        displayName: String,
        beforeSelection: (() -> Unit)? = null,
    ) {
        waitForPackage("com.google.android.documentsui", 20_000L)
        openDownloadsFromRoots()
        val exact = runCatching { waitForDocumentText(displayName, 10_000L) }.getOrNull()
        if (exact != null) {
            beforeSelection?.invoke()
            tapNode(exact)
            return
        }
        searchDocumentsUi(displayName, beforeSelection)
    }

    private fun clickDocumentFileStartingWith(prefix: String) {
        waitForPackage("com.google.android.documentsui", 20_000L)
        openDownloadsFromRoots()
        tapNode(waitForDocumentTextStartingWith(prefix, 30_000L))
    }

    private fun searchDocumentsUi(displayName: String, beforeSelection: (() -> Unit)? = null) {
        val search = findAccessibilityNodeByContentDescription(uiAutomation.rootInActiveWindow, "Search")
            ?: findAccessibilityNode(uiAutomation.rootInActiveWindow, "Search")
            ?: error("DocumentsUI did not expose its search control for $displayName")
        clickNode(search)
        executeShell("input text ${displayName.replace(" ", "%s")}")
        executeShell("input keyevent 66")
        val result = waitForSearchResult(displayName, 30_000L)
        beforeSelection?.invoke()
        tapNode(result)
    }

    private fun openDownloadsFromRoots() {
        val roots = findAccessibilityNodeByContentDescription(uiAutomation.rootInActiveWindow, "Show roots")
            ?: run {
                executeShell("input keyevent 4")
                waitForContentDescription("Show roots", 10_000L)
        }
        clickNode(roots)
        clickTextOrTap(waitForText("Downloads", 10_000L))
        SystemClock.sleep(750L)
        tapScreen(900, 300)
        SystemClock.sleep(750L)
        waitForPackage("com.google.android.documentsui", 10_000L)
    }

    private fun isDocumentsRootsDrawerOpen(): Boolean =
        findAccessibilityNodeByClassName(
            uiAutomation.rootInActiveWindow,
            "com.android.documentsui.sidebar.RootsList",
        ) != null

    private fun waitForDocumentsRootText(text: String, timeoutMs: Long): AccessibilityNodeInfo =
        waitForNode(timeoutMs) { root -> findDocumentsRootText(root, text) }

    private fun findDocumentsRootText(
        node: AccessibilityNodeInfo?,
        text: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.endsWith("RootItemView") == true) {
            return findAccessibilityNode(node, text)
        }
        for (index in 0 until node.childCount) {
            findDocumentsRootText(node.getChild(index), text)?.let { return it }
        }
        return null
    }

    private fun findAccessibilityNodeByClassName(
        node: AccessibilityNodeInfo?,
        className: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString() == className) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNodeByClassName(node.getChild(index), className)?.let { return it }
        }
        return null
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

    private fun waitForSearchResult(displayName: String, timeoutMs: Long): AccessibilityNodeInfo =
        waitForNode(timeoutMs) { root -> findSearchResult(root, displayName) }

    private fun findSearchResult(node: AccessibilityNodeInfo?, displayName: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val bounds = Rect().also(node::getBoundsInScreen)
        if (node.text?.toString() == displayName &&
            node.className?.toString() != "android.widget.EditText" &&
            bounds.top > 250
        ) {
            return node
        }
        for (index in 0 until node.childCount) {
            findSearchResult(node.getChild(index), displayName)?.let { return it }
        }
        return null
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

    private fun waitForTextStartingWithAny(
        prefixes: List<String>,
        timeoutMs: Long,
    ): AccessibilityNodeInfo = waitForNode(timeoutMs) { root ->
        prefixes.firstNotNullOfOrNull { prefix -> findAccessibilityNodeStartingWith(root, prefix) }
    }

    private fun waitForTextContainingAny(
        fragments: List<String>,
        timeoutMs: Long,
    ): AccessibilityNodeInfo = waitForNode(timeoutMs) { root ->
        fragments.firstNotNullOfOrNull { fragment -> findAccessibilityNodeContaining(root, fragment) }
    }

    private fun waitForContentDescriptionStartingWith(
        prefix: String,
        timeoutMs: Long = 15_000L,
    ): AccessibilityNodeInfo = waitForNode(timeoutMs) { root ->
        findAccessibilityNodeContentDescriptionStartingWith(root, prefix)
    }

    private fun waitForContentDescriptionContaining(
        prefix: String,
        expected: String,
        timeoutMs: Long,
    ): AccessibilityNodeInfo = waitForNode(timeoutMs) { root ->
        findAccessibilityNodeContentDescriptionStartingWith(root, prefix)
            ?.takeIf { it.contentDescription?.toString()?.contains(expected) == true }
    }

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

    private fun findAccessibilityNodeContaining(
        node: AccessibilityNodeInfo?,
        fragment: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(fragment) == true) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNodeContaining(node.getChild(index), fragment)?.let { return it }
        }
        return null
    }

    private fun findAccessibilityNodeContentDescriptionStartingWith(
        node: AccessibilityNodeInfo?,
        prefix: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.startsWith(prefix) == true) return node
        for (index in 0 until node.childCount) {
            findAccessibilityNodeContentDescriptionStartingWith(node.getChild(index), prefix)?.let { return it }
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
        targetContext.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        error("Could not resolve display name for $uri")
    }

    private fun stateField(snapshot: String, name: String): String {
        val marker = "$name="
        val start = snapshot.indexOf(marker)
        check(start >= 0) { "State snapshot is missing field $name: $snapshot" }
        val valueStart = start + marker.length
        val end = snapshot.indexOf(';', valueStart).takeIf { it >= 0 } ?: snapshot.length
        return snapshot.substring(valueStart, end)
    }

    private fun sha256(uri: Uri): String {
        uiAutomation.adoptShellPermissionIdentity()
        try {
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
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
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
        const val ARG_IMPORT_PROJECT_PATH = "importProjectPath"
        const val ARG_IMPORT_EXPECT_UNAVAILABLE = "importExpectUnavailable"
        const val ARG_IMPORT_PROJECT_PREFIX = "importProjectPrefix"
        const val ARG_IMPORT_PROJECT_SUFFIX = "importProjectSuffix"
        const val ARG_WHISPER_CANCEL = "whisperCancel"
        const val ARG_ILLEGAL_MEDIA = "illegalMedia"
        const val ARG_ILLEGAL_VALID = "illegalValid"
        const val ARG_ILLEGAL_SRT = "illegalSrt"
        const val ARG_ILLEGAL_NON_VIDEO = "illegalNonVideo"
        const val ARG_ILLEGAL_EMPTY = "illegalEmpty"
        const val ARG_ILLEGAL_UNREADABLE = "illegalUnreadable"
        const val ARG_ILLEGAL_OVER_LIMIT = "illegalOverLimit"
        const val ARG_BYOK_SECURITY = "byokSecurity"
        const val BYOK_TEST_ALIAS = "lyriccaptioner.deepseek.byok.r1test"
        const val BYOK_TEST_RECORD = "deepseek_byok_r1_test.bin"
        const val BYOK_SENTINEL_ONE = "sk-test-r1-sentinel-one-123456"
        const val BYOK_SENTINEL_TWO = "sk-test-r1-sentinel-two-123456"
        const val BYOK_SENTINEL_THREE = "sk-test-r1-sentinel-three-123456"
        const val BYOK_SENTINEL_FOUR = "sk-test-r1-sentinel-four-123456"
        const val PROJECT_PREFIX = "lyric-captioner-project"
        const val OUTPUT_PREFIX = "lyric-captioner-output"
    }
}
