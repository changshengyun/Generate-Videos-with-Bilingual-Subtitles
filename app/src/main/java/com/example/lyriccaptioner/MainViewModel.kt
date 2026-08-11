package com.example.lyriccaptioner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lyriccaptioner.captions.CaptionTimingEditor
import com.example.lyriccaptioner.captions.LyricLineAligner
import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.clearOverridesForCue
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.adjustCaptionFontSizeRatio
import com.example.lyriccaptioner.model.CueEditingPolicy
import com.example.lyriccaptioner.model.DerivedOutputPolicy
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.normalizeSubtitleColor
import com.example.lyriccaptioner.model.resolveCaptionStyle
import com.example.lyriccaptioner.model.withFontSizeRatio
import com.example.lyriccaptioner.model.validated
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.VideoImportMode
import com.example.lyriccaptioner.model.VideoImportPolicy
import com.example.lyriccaptioner.processing.AsrModule
import com.example.lyriccaptioner.processing.AppPipelineFactory
import com.example.lyriccaptioner.processing.CaptionPipeline
import com.example.lyriccaptioner.processing.TranslationBatchException
import com.example.lyriccaptioner.processing.TranslationModelState
import com.example.lyriccaptioner.processing.TranslationModule
import com.example.lyriccaptioner.processing.TranslationStage
import com.example.lyriccaptioner.processing.WhisperRuntimeStatus
import com.example.lyriccaptioner.processing.WhisperModelStore
import com.example.lyriccaptioner.processing.WhisperRuntimeStatusResolver
import com.example.lyriccaptioner.processing.UnavailableAsrModule
import com.example.lyriccaptioner.processing.AndroidMediaStoreDestinationStore
import com.example.lyriccaptioner.processing.MediaStoreExportGateway
import com.example.lyriccaptioner.processing.MediaStoreExportSession
import com.example.lyriccaptioner.processing.MediaStoreWritePolicy
import com.example.lyriccaptioner.processing.enhancement.byok.AndroidKeystoreDeepSeekKeyStore
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManagerImpl
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiMapper
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiModel
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekModelsAuthenticationProbe
import com.example.lyriccaptioner.project.AndroidProjectRepository
import com.example.lyriccaptioner.project.MediaAccessResult
import com.example.lyriccaptioner.project.ProjectLoadResult
import com.example.lyriccaptioner.project.ProjectRepository
import com.example.lyriccaptioner.project.ProjectSaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun createDefaultDeepSeekManager(context: Context): DeepSeekByokManager =
    DeepSeekByokManagerImpl(
        store = AndroidKeystoreDeepSeekKeyStore(context.applicationContext),
        probe = DeepSeekModelsAuthenticationProbe(),
    )

class MainViewModel(
    context: Context,
    private var pipeline: CaptionPipeline,
    private val srtParser: SrtParser = SrtParser(),
    private val projectRepository: ProjectRepository = AndroidProjectRepository(context),
    private var asrModule: AsrModule = UnavailableAsrModule(
        WhisperRuntimeStatus(
            modelInstalled = false,
            nativeLibraryReady = false,
            localRecognitionReady = false,
            mode = SpeechMode.UNAVAILABLE,
            detail = "Checking local speech runtime...",
        ),
    ),
    private val timingEditor: CaptionTimingEditor = CaptionTimingEditor(),
    private val lyricLineAligner: LyricLineAligner = LyricLineAligner(),
    private val translationModule: TranslationModule = TranslationModule(
        AppPipelineFactory.createTranslationDefault(context),
    ),
    private val deepSeekManager: DeepSeekByokManager = createDefaultDeepSeekManager(context),
    private val mediaStoreGateway: MediaStoreExportGateway? = null,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val galleryGateway: MediaStoreExportGateway
        get() = mediaStoreGateway ?: MediaStoreExportGateway(
            store = AndroidMediaStoreDestinationStore(appContext.contentResolver),
            policy = MediaStoreWritePolicy.current(hasLegacyWritePermission()),
        )
    private val whisperModelStore = WhisperModelStore(appContext)
    private val mutableState = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutableState.asStateFlow()
    private val mutableDeepSeekKeyUi = MutableStateFlow(
        DeepSeekKeyUiMapper.from(deepSeekManager.status()),
    )
    val deepSeekKeyUi: StateFlow<DeepSeekKeyUiModel> = mutableDeepSeekKeyUi.asStateFlow()
    private var exportJob: Job? = null
    private var asrJob: Job? = null
    private var translationJob: Job? = null
    private var deepSeekKeyOperationJob: Job? = null
    private var deepSeekKeyOperationGeneration = 0L

    init {
        refreshSpeechRuntimeStatus()
        refreshLocalSpeechRuntime()
        refreshTranslationModelState()
    }

    fun importVideo(uri: Uri, mode: VideoImportMode = VideoImportMode.NEW_VIDEO) {
        mutableState.update { it.copy(isWorking = true, status = "Checking video access...") }
        viewModelScope.launch {
            val access = withContext(Dispatchers.IO) { projectRepository.retainMediaReadAccess(uri) }
            if (access is MediaAccessResult.Unavailable) {
                Log.w(LOG_TAG, "event=video_import_failed reason=${access.reason}")
                mutableState.update { it.copy(isWorking = false, status = "Could not import video: ${access.reason}") }
                return@launch
            }
            val status = when (access) {
                is MediaAccessResult.Persisted -> if (mode == VideoImportMode.RELINK) {
                    "Video re-associated and persisted."
                } else {
                    "Video imported with persistent access."
                }
                is MediaAccessResult.SessionOnly -> "Video imported for this session only: ${access.reason}"
                is MediaAccessResult.ProviderUnsupported -> "Video imported for this session only: ${access.reason}"
                is MediaAccessResult.Unavailable -> access.reason
            }
            val maxDurationMs = state.value.modelState.maxVideoDurationMs
            val durationMs = access.durationMs
            if (!VideoImportPolicy.isDurationAllowed(durationMs, maxDurationMs)) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = if (durationMs != null && durationMs > maxDurationMs) {
                            "Video is longer than 5 minutes. Please import a shorter clip."
                        } else {
                            "Could not import video: Video duration is unavailable or invalid."
                        },
                    )
                }
                return@launch
            }

            mutableState.update {
                VideoImportPolicy.apply(
                    current = it.copy(isWorking = false),
                    uri = uri,
                    durationMs = durationMs,
                    mediaState = access.toEditorMediaState(),
                    mode = mode,
                    status = status,
                )
            }
            Log.i(
                LOG_TAG,
                "event=video_import_completed mediaState=${access::class.simpleName} durationMs=$durationMs captionCount=${state.value.captions.size}",
            )
        }
    }

    fun importSrt(raw: String) {
        val cues = srtParser.parse(raw)
        mutableState.update {
            DerivedOutputPolicy.invalidateDerivedOutputs(it.copy(
                captions = cues,
                selectedCaptionId = cues.firstOrNull()?.id,
                status = if (cues.isEmpty()) {
                    "No valid SRT subtitles found."
                } else {
                    "Imported ${cues.size} subtitle cues from SRT."
                },
            ))
        }
    }

    fun importSrt(uri: Uri) {
        readTextFile(uri, "SRT") { raw -> importSrt(raw) }
    }

    fun applyLyricText(raw: String) {
        val lyricLines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lyricLines.isEmpty()) {
            mutableState.update { it.copy(status = "No lyric lines found.") }
            return
        }

        mutableState.update { current ->
            val matches = lyricLineAligner.align(current.captions, lyricLines)
            val updated = current.captions.map { cue ->
                val match = matches[cue.id] ?: return@map cue
                if (cue.english.equals(match.lyric, ignoreCase = true)) {
                    cue
                } else {
                    cue.copy(
                        correctionCandidates = (cue.correctionCandidates + match.lyric).distinct(),
                        confirmed = false,
                    )
                }
            }
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                captions = updated,
                status = "Matched ${matches.size} lyric lines. Review the suggested corrections.",
            ))
        }
    }

    fun importLyricText(uri: Uri) {
        readTextFile(uri, "lyrics") { raw -> applyLyricText(raw) }
    }

    fun createCaptionsFromLyrics(raw: String) {
        val lyricLines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val durationMs = state.value.videoDurationMs
        if (lyricLines.isEmpty()) {
            mutableState.update { it.copy(status = "No lyric lines found.") }
            return
        }
        if (durationMs == null || durationMs <= 0L) {
            mutableState.update { it.copy(status = "Import a readable video before creating captions.") }
            return
        }

        val cues = lyricLines.mapIndexed { index, line ->
            val startMs = durationMs * index / lyricLines.size
            val endMs = (durationMs * (index + 1) / lyricLines.size)
                .coerceAtLeast(startMs + MIN_CAPTION_DURATION_MS)
                .coerceAtMost(durationMs)
            CaptionCue(
                id = "lyrics-$index-${System.nanoTime()}",
                startMs = startMs,
                endMs = endMs,
                english = line,
                chinese = "",
                confidence = 1f,
                confirmed = false,
            )
        }
        mutableState.update {
            DerivedOutputPolicy.invalidateDerivedOutputs(it.copy(
                captions = cues,
                selectedCaptionId = cues.firstOrNull()?.id,
                status = "Created ${cues.size} lyric captions. Adjust timing as needed.",
            ))
        }
    }

    fun translateMissingChinese() {
        val snapshot = state.value
        val targets = snapshot.captions.filter { it.english.isNotBlank() && it.chinese.isBlank() }
        if (targets.isEmpty()) {
            mutableState.update { it.copy(status = "All English captions already have Chinese text.") }
            return
        }
        val startedAtMs = elapsedRealtimeMs()
        Log.i(LOG_TAG, "event=translation_started targetCount=${targets.size}")
        translationJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isWorking = true,
                    translationRunning = true,
                    status = "Preparing English-to-Chinese translation model...",
                )
            }
            try {
                val result = translationModule.translateMissingChinese(
                    captions = snapshot.captions,
                    onStateChanged = ::updateTranslationModelState,
                    onStageChanged = { stage ->
                        mutableState.update {
                            it.copy(
                                status = when (stage) {
                                    TranslationStage.MODEL_PREPARATION ->
                                        "Preparing English-to-Chinese translation model..."
                                    TranslationStage.TRANSLATING ->
                                        "Translating ${targets.size} captions locally..."
                                    TranslationStage.COMMITTING ->
                                        "Applying translated captions..."
                                },
                            )
                        }
                        Log.i(
                            LOG_TAG,
                            "event=translation_stage stage=$stage targetCount=${targets.size}",
                        )
                    },
                )
                var committed = false
                mutableState.update { current ->
                    if (current.captions != snapshot.captions) {
                        current.copy(
                            isWorking = false,
                            translationRunning = false,
                            status = "Translation was not applied because captions changed. Retry.",
                        )
                    } else {
                        committed = true
                        DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                            isWorking = false,
                            translationRunning = false,
                            captions = result.captions,
                            status = "Translated ${result.translatedCount} captions to Chinese.",
                        ))
                    }
                }
                Log.i(
                    LOG_TAG,
                    "event=translation_completed targetCount=${targets.size} " +
                        "translatedCount=${result.translatedCount} committed=$committed " +
                        "elapsedMs=${elapsedRealtimeMs() - startedAtMs}",
                )
            } catch (error: CancellationException) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        translationRunning = false,
                        status = "Translation cancelled. No translated captions were applied.",
                    )
                }
                Log.i(
                    LOG_TAG,
                    "event=translation_cancelled targetCount=${targets.size} " +
                        "elapsedMs=${elapsedRealtimeMs() - startedAtMs}",
                )
            } catch (error: TranslationBatchException) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        translationRunning = false,
                        status = "Chinese translation failed during ${error.stage.name.lowercase()}. Retry.",
                    )
                }
                Log.w(
                    LOG_TAG,
                    "event=translation_failed stage=${error.stage} targetCount=${targets.size} " +
                        "elapsedMs=${elapsedRealtimeMs() - startedAtMs} " +
                        "errorType=${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}",
                )
            } finally {
                translationJob = null
            }
        }
    }

    fun cancelTranslation() {
        if (translationJob?.isActive == true) {
            Log.i(LOG_TAG, "event=translation_cancel_requested")
            translationJob?.cancel()
        }
    }

    fun generateCaptions() {
        val current = state.value
        val uri = current.videoUri ?: return
        val module = asrModule
        if (module.runtimeStatus.mode == SpeechMode.UNAVAILABLE) {
            mutableState.update { it.copy(status = module.runtimeStatus.detail) }
            Log.w(LOG_TAG, "event=asr_unavailable reason=${module.runtimeStatus.detail}")
            return
        }
        Log.i(LOG_TAG, "event=asr_started mode=${module.runtimeStatus.mode}")
        asrJob = viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, asrRunning = true, status = module.runtimeStatus.detail) }
            try {
                val cues = module.recognize(uri) { status ->
                    mutableState.update { it.copy(status = status) }
                }
                val mode = module.runtimeStatus.mode
                mutableState.update {
                    DerivedOutputPolicy.invalidateDerivedOutputs(it.copy(
                        isWorking = false,
                        asrRunning = false,
                        captions = cues,
                        selectedCaptionId = cues.firstOrNull()?.id,
                        status = if (mode == SpeechMode.LOCAL) {
                            "Local Whisper JNI generated ${cues.size} English captions."
                        } else {
                            module.runtimeStatus.detail
                        },
                    ))
                }
                Log.i(LOG_TAG, "event=asr_completed mode=$mode captionCount=${cues.size}")
            } catch (error: CancellationException) {
                mutableState.update {
                    it.copy(isWorking = false, asrRunning = false, status = "ASR cancelled; temporary audio was cleaned.")
                }
                Log.i(LOG_TAG, "event=asr_cancelled mode=${module.runtimeStatus.mode}")
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        asrRunning = false,
                        status = "ASR failed (${module.runtimeStatus.mode}): ${error.message ?: "unknown error"}",
                    )
                }
                Log.e(LOG_TAG, "event=asr_failed mode=${module.runtimeStatus.mode}", error)
            } finally {
                asrJob = null
            }
        }
    }

    fun cancelGenerateCaptions() {
        if (asrJob?.isActive == true) {
            Log.i(LOG_TAG, "event=asr_cancel_requested")
            asrJob?.cancel()
        }
    }

    fun importWhisperModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.update {
                it.copy(isWorking = true, status = "Importing Whisper model...")
            }
            runCatching {
                whisperModelStore.install(uri)
            }.onSuccess { runtime ->
                val refreshedAsr = AppPipelineFactory.createAsrDefault(appContext)
                pipeline = AppPipelineFactory.createDefault(appContext)
                asrModule = refreshedAsr
                updateSpeechRuntime(runtime)
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = if (runtime.localRecognitionReady) {
                            "Whisper model installed. Local recognition is ready."
                        } else {
                            runtime.detail
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Could not import Whisper model: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun addCaption() {
        mutableState.update { current ->
            val previousEndMs = current.captions.maxOfOrNull { it.endMs } ?: 0L
            val videoDurationMs = current.videoDurationMs
            val startMs = when {
                videoDurationMs == null -> previousEndMs
                previousEndMs < videoDurationMs -> previousEndMs
                else -> (videoDurationMs - MIN_CAPTION_DURATION_MS).coerceAtLeast(0L)
            }
            val endMs = videoDurationMs
                ?.coerceAtLeast(startMs + MIN_CAPTION_DURATION_MS)
                ?.let { minOf(it, startMs + DEFAULT_CAPTION_DURATION_MS) }
                ?: startMs + DEFAULT_CAPTION_DURATION_MS
            val cue = CaptionCue(
                id = "manual-${System.nanoTime()}",
                startMs = startMs,
                endMs = endMs,
                english = "",
                chinese = "",
                confidence = 1f,
                confirmed = false,
            )
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                captions = current.captions + cue,
                selectedCaptionId = cue.id,
                status = "Added a caption. Edit its text and timing.",
            ))
        }
    }

    fun updateEnglishText(cueId: String, text: String) {
        updateCue(cueId) { CueEditingPolicy.updateEnglish(it, text) }
    }

    fun updateChineseText(cueId: String, text: String) {
        updateCue(cueId) { CueEditingPolicy.updateChinese(it, text) }
    }

    fun confirmCue(cueId: String) {
        mutableState.update { current ->
            val cue = current.captions.firstOrNull { it.id == cueId } ?: return@update current
            if (!cue.canConfirm) {
                current.copy(
                    captions = current.captions.map {
                        if (it.id == cueId) CueEditingPolicy.confirm(it) else it
                    },
                    status = "Both English and Chinese text are required before confirmation.",
                )
            } else {
                current.copy(
                    captions = current.captions.map {
                        if (it.id == cueId) CueEditingPolicy.confirm(it) else it
                    },
                    status = "Caption confirmed.",
                ).let(DerivedOutputPolicy::invalidateDerivedOutputs)
            }
        }
    }

    fun applyCorrectionCandidate(cueId: String, candidate: String) {
        updateCue(cueId) { CueEditingPolicy.applyEnglishCorrection(it, candidate) }
    }

    fun shiftCueStart(cueId: String, deltaMs: Long) {
        val captions = state.value.captions
        val index = captions.indexOfFirst { it.id == cueId }
        if (index < 0) return
        val earliestStartMs = captions.getOrNull(index - 1)?.endMs ?: 0L
        updateCue(cueId) {
            CueEditingPolicy.updateTiming(
                it,
                timingEditor.shiftStart(it, deltaMs, earliestStartMs),
            )
        }
    }

    fun shiftCueEnd(cueId: String, deltaMs: Long) {
        val current = state.value
        val index = current.captions.indexOfFirst { it.id == cueId }
        if (index < 0) return
        val latestEndMs = current.captions.getOrNull(index + 1)?.startMs
        updateCue(cueId) {
            CueEditingPolicy.updateTiming(
                it,
                timingEditor.shiftEnd(it, deltaMs, current.videoDurationMs, latestEndMs),
            )
        }
    }

    fun selectCue(cueId: String) {
        mutableState.update { it.copy(selectedCaptionId = cueId) }
    }

    fun deleteCaption(cueId: String) {
        mutableState.update { current ->
            val remaining = current.captions.filterNot { it.id == cueId }
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                captions = remaining,
                selectedCaptionId = remaining.firstOrNull()?.id,
                status = "Caption removed.",
            ))
        }
    }

    fun exportVideo() {
        val current = state.value
        val rejection = when {
            current.videoUri == null -> "no_video" to "No video selected. Import a video before exporting."
            current.captions.isEmpty() -> "no_captions" to "No subtitles available. Import or create subtitles before exporting."
            else -> null
        }
        if (rejection != null) {
            Log.w(
                LOG_TAG,
                "event=export_rejected reason=${rejection.first} destinationUntouched=true",
            )
            mutableState.update { it.copy(status = rejection.second) }
            return
        }

        if (exportJob?.isActive == true) {
            mutableState.update { it.copy(status = "An export is already running.") }
            return
        }
        val uri = checkNotNull(current.videoUri)
        val taskId = "export-${System.nanoTime()}"
        Log.i(LOG_TAG, "event=export_started captionCount=${current.captions.size}")
        exportJob = viewModelScope.launch {
            var session: MediaStoreExportSession? = null
            try {
                val exportSession = withContext(Dispatchers.IO) {
                    galleryGateway.begin(taskId, sourceUri = uri)
                }
                session = exportSession
                exportSession.beginExternalWrite()
                mutableState.update {
                    it.copy(
                        isWorking = true,
                        exportUri = null,
                        status = "Rendering burned-in subtitles...",
                    )
                }
                // The API 36.1 emulator releases PlayerView's decoder surface asynchronously.
                // Wait for that release before Transformer opens a second video decoder.
                delay(PREVIEW_RELEASE_DELAY_MS)
                pipeline.export(
                    uri,
                    exportSession.destination.uri,
                    current.captions,
                    current.exportProfile,
                    current.captionLayout,
                    current.defaultCaptionStyle,
                ) { status ->
                    mutableState.update { it.copy(status = status) }
                }
                val published = withContext(Dispatchers.IO) { exportSession.publish() }
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        exportUri = published.uri,
                        status = "Export saved to system gallery.",
                    )
                }
            } catch (error: CancellationException) {
                withContext(Dispatchers.IO) { session?.cancel() }
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video export cancelled.",
                        exportUri = null,
                    )
                }
                throw error
            } catch (error: Throwable) {
                withContext(Dispatchers.IO) { session?.rollback() }
                Log.e(LOG_TAG, "event=export_failed destinationUntouched=true")
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video export failed.",
                        exportUri = null,
                    )
                }
            } finally {
                exportJob = null
            }
        }
    }

    fun cancelExport() {
        if (exportJob?.isActive == true) {
            Log.i(LOG_TAG, "event=export_cancel_requested")
            exportJob?.cancel()
        }
    }

    fun exportSidecarSrt() {
        val srt = pipeline.exportSidecarSrt(state.value.captions)
        mutableState.update {
            it.copy(
                status = "SRT sidecar is ready to save.",
                pendingSidecarSrt = srt,
            )
        }
    }

    fun sidecarSrtSaved(uri: Uri) {
        mutableState.update {
            it.copy(
                status = "SRT sidecar saved: $uri",
                pendingSidecarSrt = null,
            )
        }
    }

    fun sidecarSrtSaveFailed(message: String) {
        mutableState.update {
            it.copy(status = "Could not save SRT sidecar: $message")
        }
    }

    fun saveDeepSeekKey(apiKey: String) {
        launchDeepSeekKeyOperation(
            initialStatus = DeepSeekKeyStatus(
                DeepSeekKeyState.VALIDATING_NEW_KEY,
                mutableDeepSeekKeyUi.value.maskedKey,
            ),
        ) {
            deepSeekManager.validateAndSave(apiKey)
        }
    }

    fun replaceDeepSeekKey(apiKey: String) {
        launchDeepSeekKeyOperation(
            initialStatus = DeepSeekKeyStatus(
                DeepSeekKeyState.VALIDATING_NEW_KEY,
                mutableDeepSeekKeyUi.value.maskedKey,
            ),
        ) {
            deepSeekManager.replace(apiKey)
        }
    }

    fun testDeepSeekConnection() {
        launchDeepSeekKeyOperation(
            initialStatus = DeepSeekKeyStatus(
                DeepSeekKeyState.TESTING_CONNECTION,
                mutableDeepSeekKeyUi.value.maskedKey,
            ),
        ) {
            deepSeekManager.testConnection()
        }
    }

    fun cancelDeepSeekKeyInput() {
        launchDeepSeekKeyOperation { deepSeekManager.cancelInput() }
    }

    fun deleteDeepSeekKey() {
        val maskedKeyBeforeDelete = mutableDeepSeekKeyUi.value.maskedKey
        launchDeepSeekKeyOperation(
            onFailure = {
                DeepSeekKeyStatus(
                    state = DeepSeekKeyState.NEEDS_REENTRY,
                    maskedKey = maskedKeyBeforeDelete,
                    detail = "Secure deletion failed.",
                )
            },
        ) {
            deepSeekManager.delete()
        }
    }

    private fun updateDeepSeekKeyUi(status: DeepSeekKeyStatus) {
        mutableDeepSeekKeyUi.value = DeepSeekKeyUiMapper.from(status)
    }

    private fun launchDeepSeekKeyOperation(
        initialStatus: DeepSeekKeyStatus? = null,
        onFailure: () -> DeepSeekKeyStatus = {
            DeepSeekKeyStatus(DeepSeekKeyState.VALIDATION_FAILED, detail = "Secure key operation failed.")
        },
        operation: suspend () -> DeepSeekKeyStatus,
    ) {
        val previous = deepSeekKeyOperationJob
        val generation = ++deepSeekKeyOperationGeneration
        initialStatus?.let(::updateDeepSeekKeyUi)
        deepSeekKeyOperationJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            val result = try {
                withContext(Dispatchers.IO) { operation() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                onFailure()
            }
            if (generation == deepSeekKeyOperationGeneration) {
                updateDeepSeekKeyUi(result)
            }
        }
    }

    fun saveSidecarSrt(uri: Uri, srt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(srt.toByteArray(Charsets.UTF_8))
                } ?: error("No output stream")
                withContext(Dispatchers.Main.immediate) { sidecarSrtSaved(uri) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "event=srt_sidecar_save_failed", error)
                withContext(Dispatchers.Main.immediate) {
                    sidecarSrtSaveFailed(error.message ?: "unknown error")
                }
            }
        }
    }

    fun updateFontSize(delta: Int) {
        updateDefaultCaptionStyle { style ->
            val ratio = adjustCaptionFontSizeRatio(style.validated().fontSizeRatio, delta)
            style.withFontSizeRatio(ratio)
        }
    }

    fun updateBottomMargin(delta: Int) {
        updateCaptionLayout { layout ->
            layout.copy(yRatio = (layout.yRatio - delta / 100f).coerceIn(0.72f, 0.96f))
        }
    }

    fun updateEnglishColor(colorHex: String) {
        updateDefaultCaptionStyle { style -> style.copy(primaryColorHex = normalizeSubtitleColor(colorHex, style.primaryColorHex)) }
    }

    fun updateChineseColor(colorHex: String) {
        updateDefaultCaptionStyle { style -> style.copy(secondaryColorHex = normalizeSubtitleColor(colorHex, style.secondaryColorHex)) }
    }

    fun updateOutlineColor(colorHex: String) {
        updateDefaultCaptionStyle { style -> style.copy(outlineColorHex = normalizeSubtitleColor(colorHex, style.outlineColorHex)) }
    }

    fun updateFontFamily(fontFamily: String) {
        val supported = setOf(SUBTITLE_FONT_SANS, SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO)
        updateDefaultCaptionStyle { style -> style.copy(fontFamily = fontFamily.takeIf { it in supported } ?: style.fontFamily) }
    }

    fun toggleDefaultBold() = updateDefaultCaptionStyle { it.copy(bold = !it.bold) }

    fun toggleDefaultItalic() = updateDefaultCaptionStyle { it.copy(italic = !it.italic) }

    fun updateDefaultAlignment(alignment: CaptionAlignment) =
        updateDefaultCaptionStyle { it.copy(alignment = alignment) }

    fun updateSelectedCueFontSize(delta: Int) {
        updateSelectedCueStyle { cue, override ->
            val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
            override.withFontSizeRatio(adjustCaptionFontSizeRatio(resolved.fontSizeRatio, delta))
        }
    }

    /** Cue-card API: every write is explicitly bound to the card's stable cue id. */
    fun updateCueFontSize(cueId: String, delta: Int) = updateCueStyle(cueId) { cue, override ->
        val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
        override.withFontSizeRatio(adjustCaptionFontSizeRatio(resolved.fontSizeRatio, delta))
    }

    fun updateCueEnglishColor(cueId: String, colorHex: String) = updateCueStyle(cueId) { cue, override ->
        val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
        override.copy(primaryColorHex = normalizeSubtitleColor(colorHex, resolved.primaryColorHex))
    }

    fun updateCueChineseColor(cueId: String, colorHex: String) = updateCueStyle(cueId) { cue, override ->
        val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
        override.copy(secondaryColorHex = normalizeSubtitleColor(colorHex, resolved.secondaryColorHex))
    }

    fun updateCueOutlineColor(cueId: String, colorHex: String) = updateCueStyle(cueId) { cue, override ->
        val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
        override.copy(outlineColorHex = normalizeSubtitleColor(colorHex, resolved.outlineColorHex))
    }

    fun updateCueFontFamily(cueId: String, fontFamily: String) = updateCueStyle(cueId) { cue, override ->
        val supported = setOf(SUBTITLE_FONT_SANS, SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO)
        val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
        override.copy(fontFamily = fontFamily.takeIf { it in supported } ?: resolved.fontFamily)
    }

    fun toggleCueBold(cueId: String) = updateCueStyle(cueId) { cue, override ->
        override.copy(bold = !resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride).bold)
    }

    fun toggleCueItalic(cueId: String) = updateCueStyle(cueId) { cue, override ->
        override.copy(italic = !resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride).italic)
    }

    fun updateCueAlignment(cueId: String, alignment: CaptionAlignment) =
        updateCueStyle(cueId) { _, override -> override.copy(alignment = alignment) }

    fun updateCuePosition(cueId: String, delta: Int) {
        val current = state.value
        val cue = current.captions.firstOrNull { it.id == cueId } ?: return
        val resolved = com.example.lyriccaptioner.model.resolveCaptionLayout(
            current.captionLayout,
            cue.layoutOverride,
        )
        val nextY = (resolved.yRatio - delta / 100f).coerceIn(0f, 1f)
        updateCue(cueId) { existing ->
            val next = (existing.layoutOverride ?: CaptionLayoutOverride()).copy(yRatio = nextY)
            existing.copy(layoutOverride = next.takeUnless { it.isEmpty })
        }
    }

    fun clearCueStyleOverride(cueId: String) {
        mutableState.update { current ->
            DerivedOutputPolicy.invalidateDerivedOutputs(
                current.copy(captions = current.captions.clearOverridesForCue(cueId)),
            )
        }
    }

    fun updateSelectedCueEnglishColor(colorHex: String) {
        updateSelectedCueStyle { cue, override ->
            val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
            override.copy(primaryColorHex = normalizeSubtitleColor(colorHex, resolved.primaryColorHex))
        }
    }

    fun updateSelectedCueChineseColor(colorHex: String) {
        updateSelectedCueStyle { cue, override ->
            val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
            override.copy(secondaryColorHex = normalizeSubtitleColor(colorHex, resolved.secondaryColorHex))
        }
    }

    fun updateSelectedCueOutlineColor(colorHex: String) {
        updateSelectedCueStyle { cue, override ->
            val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
            override.copy(outlineColorHex = normalizeSubtitleColor(colorHex, resolved.outlineColorHex))
        }
    }

    fun updateSelectedCueFontFamily(fontFamily: String) {
        val supported = setOf(SUBTITLE_FONT_SANS, SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO)
        updateSelectedCueStyle { cue, override ->
            val resolved = resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride)
            override.copy(fontFamily = fontFamily.takeIf { it in supported } ?: resolved.fontFamily)
        }
    }

    fun toggleSelectedCueBold() {
        updateSelectedCueStyle { cue, override ->
            override.copy(bold = !resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride).bold)
        }
    }

    fun toggleSelectedCueItalic() {
        updateSelectedCueStyle { cue, override ->
            override.copy(italic = !resolveCaptionStyle(state.value.defaultCaptionStyle, cue.styleOverride).italic)
        }
    }

    fun updateSelectedCueAlignment(alignment: CaptionAlignment) {
        updateSelectedCueStyle { _, override -> override.copy(alignment = alignment) }
    }

    fun clearSelectedCueStyleOverride() {
        val selectedId = state.value.selectedCaptionId ?: return
        mutableState.update { current ->
            DerivedOutputPolicy.invalidateDerivedOutputs(
                current.copy(captions = current.captions.clearOverridesForCue(selectedId)),
            )
        }
    }

    fun saveProjectArchive(destinationUri: Uri) {
        val snapshot = currentSnapshot()
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Saving project archive...") }
            when (val result = withContext(Dispatchers.IO) { projectRepository.save(snapshot, destinationUri) }) {
                is ProjectSaveResult.Success -> mutableState.update {
                    Log.i(LOG_TAG, "event=project_save_completed captionCount=${snapshot.captions.size}")
                    it.copy(isWorking = false, status = "Project archive saved.")
                }
                is ProjectSaveResult.Failure -> mutableState.update {
                    Log.w(LOG_TAG, "event=project_save_failed kind=${result.error.kind}")
                    it.copy(isWorking = false, status = "Could not save project: ${result.error.message}")
                }
            }
        }
    }

    fun importProjectArchive(sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(isWorking = true, status = "Opening project archive...") }
            when (val result = projectRepository.load(sourceUri)) {
                is ProjectLoadResult.Success -> {
                   val snapshot = result.snapshot
                    val requiresVideoAssociation = snapshot.videoUri.isNullOrBlank() ||
                        result.mediaAccess is MediaAccessResult.Unavailable
                    val mediaState = if (snapshot.videoUri.isNullOrBlank()) {
                        MediaState.NONE
                    } else {
                        result.mediaAccess.toEditorMediaState()
                    }
                    val status = if (snapshot.videoUri.isNullOrBlank()) {
                        "Project restored without a video. Select a video to associate it."
                    } else when (result.mediaAccess) {
                        is MediaAccessResult.Persisted -> "Project restored with persistent video access."
                        is MediaAccessResult.SessionOnly -> "Project restored; video access is session-only."
                        is MediaAccessResult.ProviderUnsupported -> "Project restored; video access is session-only because the provider cannot persist it."
                        is MediaAccessResult.Unavailable -> "Project restored; video is unavailable. Select a new video to re-associate it."
                    }
                    mutableState.update {
                        Log.i(
                            LOG_TAG,
                            "event=project_load_completed mediaState=$mediaState captionCount=${snapshot.captions.size}",
                        )
                        DerivedOutputPolicy.invalidateDerivedOutputs(it.copy(
                            isWorking = false,
                            videoUri = snapshot.videoUri?.let(Uri::parse),
                            videoDurationMs = snapshot.videoDurationMs ?: result.mediaAccess.durationMs,
                            mediaState = mediaState,
                            requiresVideoAssociation = requiresVideoAssociation,
                            captions = snapshot.captions,
                            selectedCaptionId = snapshot.captions.firstOrNull()?.id,
                            exportProfile = snapshot.exportProfile,
                            captionLayout = snapshot.captionLayout,
                            defaultCaptionStyle = snapshot.defaultCaptionStyle,
                            status = status,
                        ))
                    }
                }
                is ProjectLoadResult.Failure -> mutableState.update {
                    Log.w(LOG_TAG, "event=project_load_failed kind=${result.error.kind}")
                    it.copy(isWorking = false, status = "Could not import project: ${result.error.message}")
                }
            }
        }
    }

    private fun MediaAccessResult.toEditorMediaState(): MediaState = when (this) {
        is MediaAccessResult.Persisted -> MediaState.PERSISTED
        is MediaAccessResult.SessionOnly -> MediaState.SESSION_ONLY
        is MediaAccessResult.ProviderUnsupported -> MediaState.PROVIDER_UNSUPPORTED
        is MediaAccessResult.Unavailable -> MediaState.UNAVAILABLE
    }

    private fun updateCue(cueId: String, transform: (CaptionCue) -> CaptionCue) {
        mutableState.update { current ->
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                captions = current.captions.map { cue ->
                    if (cue.id == cueId) transform(cue) else cue
                },
            ))
        }
    }

    private fun refreshSpeechRuntimeStatus() {
        updateSpeechRuntime(asrModule.runtimeStatus)
    }

    private fun refreshLocalSpeechRuntime() {
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = runCatching { AppPipelineFactory.createAsrDefault(appContext) }
                .getOrElse { error ->
                    Log.e(LOG_TAG, "event=speech_runtime_initialization_failed", error)
                    UnavailableAsrModule(WhisperRuntimeStatusResolver.resolve(false, false))
                }
            asrModule = resolved
            updateSpeechRuntime(resolved.runtimeStatus)
        }
    }

    private fun refreshTranslationModelState() {
        viewModelScope.launch(Dispatchers.IO) {
            translationModule.refreshModelState(::updateTranslationModelState)
        }
    }

    private fun readTextFile(uri: Uri, label: String, onRead: (String) -> Unit) {
        mutableState.update { it.copy(isWorking = true, status = "Reading $label file...") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val raw = readUtf8Text(uri)
                withContext(Dispatchers.Main.immediate) {
                    onRead(raw)
                    mutableState.update { it.copy(isWorking = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(LOG_TAG, "event=text_import_failed label=$label", error)
                mutableState.update {
                    it.copy(isWorking = false, status = "Could not read $label file: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun readUtf8Text(uri: Uri): String {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                check(total <= MAX_TEXT_FILE_BYTES) { "Text file exceeds ${MAX_TEXT_FILE_BYTES / 1_024} KiB." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("The selected file cannot be read.")
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun updateTranslationModelState(modelState: TranslationModelState) {
        mutableState.update { current ->
            current.copy(
                modelState = current.modelState.copy(translationModelState = modelState),
            )
        }
        Log.i(LOG_TAG, "event=translation_model_state state=$modelState")
    }

    private fun updateSpeechRuntime(runtime: WhisperRuntimeStatus) {
        mutableState.update { current ->
            current.copy(
                modelState = current.modelState.copy(
                    speechModelReady = runtime.localRecognitionReady,
                    speechModelInstalled = runtime.modelInstalled,
                    speechNativeLibraryReady = runtime.nativeLibraryReady,
                    speechMode = runtime.mode,
                    speechRuntimeDetail = runtime.detail,
                ),
            )
        }
    }

    private fun updateDefaultCaptionStyle(transform: (DefaultCaptionStyle) -> DefaultCaptionStyle) {
        mutableState.update { current ->
            val updated = transform(current.defaultCaptionStyle)
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                defaultCaptionStyle = updated,
                exportProfile = current.exportProfile.copy(
                    subtitleStyle = current.exportProfile.subtitleStyle.copy(
                        fontSizeSp = updated.fontSizeSp,
                        primaryColorHex = updated.primaryColorHex,
                        secondaryColorHex = updated.secondaryColorHex,
                        outlineColorHex = updated.outlineColorHex,
                        fontFamily = updated.fontFamily,
                    ),
                ),
            ))
        }
    }

    private fun updateCaptionLayout(transform: (CaptionLayout) -> CaptionLayout) {
        mutableState.update { current ->
            val updated = transform(current.captionLayout)
            val bottomMargin = ((1f - updated.yRatio) * 100f).toInt().coerceIn(4, 28)
            DerivedOutputPolicy.invalidateDerivedOutputs(current.copy(
                captionLayout = updated,
                exportProfile = current.exportProfile.copy(
                    subtitleStyle = current.exportProfile.subtitleStyle.copy(bottomMarginPercent = bottomMargin),
                ),
            ))
        }
    }

    private fun updateSelectedCueStyle(
        transform: (CaptionCue, CaptionStyleOverride) -> CaptionStyleOverride,
    ) {
        val selectedId = state.value.selectedCaptionId ?: return
        updateCue(selectedId) { cue ->
            cue.copy(styleOverride = transform(cue, cue.styleOverride ?: CaptionStyleOverride()))
        }
    }

    private fun updateCueStyle(
        cueId: String,
        transform: (CaptionCue, CaptionStyleOverride) -> CaptionStyleOverride,
    ) {
        updateCue(cueId) { cue ->
            cue.copy(styleOverride = transform(cue, cue.styleOverride ?: CaptionStyleOverride()))
        }
    }

    private fun currentSnapshot(): ProjectSnapshot {
        val current = state.value
        return ProjectSnapshot(
            videoUri = current.videoUri?.toString(),
            videoDurationMs = current.videoDurationMs,
            captions = current.captions,
            exportProfile = current.exportProfile,
            captionProcessing = current.captionProcessing,
            captionLayout = current.captionLayout,
            defaultCaptionStyle = current.defaultCaptionStyle,
        )
    }

    private fun elapsedRealtimeMs(): Long = System.nanoTime() / 1_000_000L

    private fun hasLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val pipeline = AppPipelineFactory.createDefault(context)
            return MainViewModel(
                context = context,
                pipeline = pipeline,
                deepSeekManager = createDefaultDeepSeekManager(context),
            ) as T
        }
    }

    private companion object {
        const val LOG_TAG = "MainViewModel"
        const val DEFAULT_CAPTION_DURATION_MS = 2_000L
        const val MIN_CAPTION_DURATION_MS = 100L
        const val PREVIEW_RELEASE_DELAY_MS = 1_500L
        const val MAX_TEXT_FILE_BYTES = 1 * 1_024 * 1_024
    }
}
