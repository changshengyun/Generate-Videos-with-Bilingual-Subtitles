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
import com.example.lyriccaptioner.model.CaptionSplitLine
import com.example.lyriccaptioner.model.splitCaptionCue
import com.example.lyriccaptioner.model.splitCaptionCueDraft
import com.example.lyriccaptioner.model.clearOverridesForCue
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionBasicStylePreset
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionProcessingSnapshot
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.CaptionWorkflowStage
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ResolvedCaptionStyle
import com.example.lyriccaptioner.model.adjustCaptionFontSizeRatio
import com.example.lyriccaptioner.model.CueEditingPolicy
import com.example.lyriccaptioner.model.DerivedOutputPolicy
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ExportState
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.normalizeSubtitleColor
import com.example.lyriccaptioner.model.resolveCaptionStyle
import com.example.lyriccaptioner.model.resolveCaptionLayout
import com.example.lyriccaptioner.model.movedToDirectEditPosition
import com.example.lyriccaptioner.model.withBasicStylePreset
import com.example.lyriccaptioner.model.withDirectEditFontSize
import com.example.lyriccaptioner.model.withDirectEditWidth
import com.example.lyriccaptioner.model.withFontSizeRatio
import com.example.lyriccaptioner.model.insertCaptionAt
import com.example.lyriccaptioner.model.withUnifiedTextColor
import com.example.lyriccaptioner.model.validated
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.VideoImportMode
import com.example.lyriccaptioner.model.VideoImportPolicy
import com.example.lyriccaptioner.processing.AsrModule
import com.example.lyriccaptioner.processing.AppPipelineFactory
import com.example.lyriccaptioner.processing.CaptionPipeline
import com.example.lyriccaptioner.processing.CompleteCaptionWorkflowPreflight
import com.example.lyriccaptioner.processing.CompleteCaptionWorkflowRunner
import com.example.lyriccaptioner.processing.blockingMessage
import com.example.lyriccaptioner.processing.TranslationModelState
import com.example.lyriccaptioner.processing.TranslationModule
import com.example.lyriccaptioner.processing.WhisperRuntimeStatus
import com.example.lyriccaptioner.processing.WhisperModelStore
import com.example.lyriccaptioner.processing.WhisperRuntimeStatusResolver
import com.example.lyriccaptioner.processing.UnavailableAsrModule
import com.example.lyriccaptioner.processing.AndroidMediaStoreDestinationStore
import com.example.lyriccaptioner.processing.MediaStoreExportGateway
import com.example.lyriccaptioner.processing.MediaStoreExportResult
import com.example.lyriccaptioner.processing.MediaStoreExportSession
import com.example.lyriccaptioner.processing.MediaStoreExportState
import com.example.lyriccaptioner.processing.MediaStoreWritePolicy
import com.example.lyriccaptioner.processing.enhancement.byok.AndroidKeystoreDeepSeekKeyStore
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManagerImpl
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiMapper
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyUiModel
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekModelsAuthenticationProbe
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementCoordinator
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementException
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementService
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import com.example.lyriccaptioner.processing.enhancement.CaptionProcessingLevel
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestion
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestionRequest
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestionService
import com.example.lyriccaptioner.processing.enhancement.CaptionCueSuggestionUiState
import com.example.lyriccaptioner.processing.enhancement.DeepSeekCaptionEnhancementProvider
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun createDefaultDeepSeekManager(context: Context): DeepSeekByokManager =
    DeepSeekByokManagerImpl(
        store = AndroidKeystoreDeepSeekKeyStore(context.applicationContext),
        probe = DeepSeekModelsAuthenticationProbe(),
    )

/**
 * Pure state reducer for direct-edit writes. Keeping this Android-free makes the stable cue-id,
 * field-isolation and derived-output invalidation contract directly testable on the JVM.
 */
internal fun EditorState.updateDirectEditedCue(
    cueId: String,
    transform: (CaptionCue, EditorState) -> CaptionCue,
): EditorState {
    val index = captions.indexOfFirst { it.id == cueId }
    if (index < 0) return this
    val currentCue = captions[index]
    val updatedCue = transform(currentCue, this)
    if (updatedCue == currentCue) return this
    val updatedCaptions = captions.toMutableList().also { it[index] = updatedCue }
    return DerivedOutputPolicy.invalidateDerivedOutputs(copy(captions = updatedCaptions))
}

internal fun EditorState.withAppliedCueSuggestion(suggestion: CaptionCueSuggestion): EditorState =
    updateDirectEditedCue(suggestion.cueId) { cue, _ ->
        cue.copy(
            english = suggestion.english,
            chinese = suggestion.chinese,
            correctionCandidates = emptyList(),
            confirmed = false,
        )
    }

private fun CaptionLayout.minimizedAgainst(defaultLayout: CaptionLayout): CaptionLayoutOverride =
    CaptionLayoutOverride(
        xRatio = xRatio.takeUnless { it == defaultLayout.xRatio },
        yRatio = yRatio.takeUnless { it == defaultLayout.yRatio },
        widthRatio = widthRatio.takeUnless { it == defaultLayout.widthRatio },
    )

private fun ResolvedCaptionStyle.toDirectEditOverride(): CaptionStyleOverride =
    CaptionStyleOverride(
        primaryColorHex = primaryColorHex,
        secondaryColorHex = secondaryColorHex,
        outlineColorHex = outlineColorHex,
        fontFamily = fontFamily,
        bold = bold,
        italic = italic,
        alignment = alignment,
        fontSizeRatio = fontSizeRatio,
        outlineWidthRatio = outlineWidthRatio,
        backgroundEnabled = backgroundEnabled,
        backgroundColorHex = backgroundColorHex,
    )

private fun CaptionStyleOverride.minimizedAgainst(
    defaultStyle: ResolvedCaptionStyle,
): CaptionStyleOverride = CaptionStyleOverride(
    primaryColorHex = primaryColorHex?.takeUnless { it == defaultStyle.primaryColorHex },
    secondaryColorHex = secondaryColorHex?.takeUnless { it == defaultStyle.secondaryColorHex },
    outlineColorHex = outlineColorHex?.takeUnless { it == defaultStyle.outlineColorHex },
    fontFamily = fontFamily?.takeUnless { it == defaultStyle.fontFamily },
    bold = bold?.takeUnless { it == defaultStyle.bold },
    italic = italic?.takeUnless { it == defaultStyle.italic },
    alignment = alignment?.takeUnless { it == defaultStyle.alignment },
    fontSizeRatio = fontSizeRatio?.takeUnless { it == defaultStyle.fontSizeRatio },
    outlineWidthRatio = outlineWidthRatio?.takeUnless { it == defaultStyle.outlineWidthRatio },
    backgroundEnabled = backgroundEnabled?.takeUnless { it == defaultStyle.backgroundEnabled },
    backgroundColorHex = backgroundColorHex?.takeUnless { it == defaultStyle.backgroundColorHex },
).validated()

internal fun EditorState.withCueDirectPosition(
    cueId: String,
    xRatio: Float,
    yRatio: Float,
): EditorState = updateDirectEditedCue(cueId) { cue, snapshot ->
    val resolved = resolveCaptionLayout(snapshot.captionLayout, cue.layoutOverride)
    val updated = resolved.movedToDirectEditPosition(xRatio, yRatio)
    cue.copy(
        layoutOverride = updated.minimizedAgainst(snapshot.captionLayout).takeUnless { it.isEmpty },
    )
}

internal fun EditorState.withCueDirectWidth(cueId: String, widthRatio: Float): EditorState =
    updateDirectEditedCue(cueId) { cue, snapshot ->
        val resolved = resolveCaptionLayout(snapshot.captionLayout, cue.layoutOverride)
        val updated = resolved.withDirectEditWidth(widthRatio)
        cue.copy(
            layoutOverride = updated.minimizedAgainst(snapshot.captionLayout).takeUnless { it.isEmpty },
        )
    }

internal fun EditorState.withCueDirectFontSize(cueId: String, fontSizeRatio: Float): EditorState =
    updateDirectEditedCue(cueId) { cue, snapshot ->
        val resolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, cue.styleOverride)
        val defaultResolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, null)
        val updated = resolved.toDirectEditOverride()
            .withDirectEditFontSize(fontSizeRatio)
            .minimizedAgainst(defaultResolved)
        cue.copy(styleOverride = updated.takeUnless { it.isEmpty })
    }

internal fun EditorState.withCueBasicStyle(
    cueId: String,
    preset: CaptionBasicStylePreset,
): EditorState = updateDirectEditedCue(cueId) { cue, snapshot ->
    val resolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, cue.styleOverride)
    val defaultResolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, null)
    val updated = resolved.toDirectEditOverride()
        .withBasicStylePreset(preset)
        .minimizedAgainst(defaultResolved)
    cue.copy(styleOverride = updated.takeUnless { it.isEmpty })
}

internal fun EditorState.withCueUnifiedTextColor(cueId: String, colorHex: String): EditorState =
    updateDirectEditedCue(cueId) { cue, snapshot ->
        val resolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, cue.styleOverride)
        val defaultResolved = resolveCaptionStyle(snapshot.defaultCaptionStyle, null)
        val updated = resolved.toDirectEditOverride()
            .withUnifiedTextColor(colorHex)
            .minimizedAgainst(defaultResolved)
        cue.copy(styleOverride = updated.takeUnless { it.isEmpty })
    }

internal suspend fun executeExportTask(
    beginSession: suspend () -> MediaStoreExportSession,
    beforeRender: suspend () -> Unit,
    render: suspend (MediaStoreExportSession) -> Long,
    onRunning: () -> Unit,
    onSucceeded: (MediaStoreExportResult) -> Unit,
    onCancelled: () -> Unit,
    onFailed: (Throwable) -> Unit,
) {
    var session: MediaStoreExportSession? = null
    try {
        withContext(NonCancellable + Dispatchers.IO) {
            session = beginSession()
        }
        currentCoroutineContext().ensureActive()
        val exportSession = checkNotNull(session)
        exportSession.beginExternalWrite()
        onRunning()
        beforeRender()
        val writtenBytes = render(exportSession)
        val published = withContext(Dispatchers.IO) { exportSession.publish(writtenBytes) }
        onSucceeded(published)
    } catch (error: CancellationException) {
        withContext(NonCancellable + Dispatchers.IO) {
            val activeSession = session
            if (activeSession?.state == MediaStoreExportState.PUBLISHED) {
                onSucceeded(activeSession.publish())
            } else {
                activeSession?.cancel()
                onCancelled()
            }
        }
        throw error
    } catch (error: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) {
            val activeSession = session
            if (activeSession?.state == MediaStoreExportState.PUBLISHED) {
                onSucceeded(activeSession.publish())
            } else {
                activeSession?.rollback()
                onFailed(error)
            }
        }
    }
}

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
    private val captionEnhancementService: CaptionEnhancementService? = null,
    private val captionCueSuggestionService: CaptionCueSuggestionService? = null,
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
    private val defaultEnhancementProvider: DeepSeekCaptionEnhancementProvider by lazy {
        DeepSeekCaptionEnhancementProvider(deepSeekManager)
    }
    private val enhancementService: CaptionEnhancementService by lazy {
        captionEnhancementService ?: CaptionEnhancementCoordinator(
            provider = defaultEnhancementProvider,
            localTranslation = translationModule,
        )
    }
    private val cueSuggestionService: CaptionCueSuggestionService by lazy {
        captionCueSuggestionService ?: defaultEnhancementProvider
    }
    private val mutableCueSuggestion = MutableStateFlow(CaptionCueSuggestionUiState())
    val cueSuggestion: StateFlow<CaptionCueSuggestionUiState> = mutableCueSuggestion.asStateFlow()
    private var exportJob: Job? = null
    private var captionWorkflowJob: Job? = null
    private var deepSeekKeyOperationJob: Job? = null
    private var cueSuggestionJob: Job? = null
    private var cueSuggestionGeneration = 0L
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
                ).copy(mediaRevision = it.mediaRevision + 1L)
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

    fun generateCompleteCaptions() {
        val snapshot = state.value
        val module = asrModule
        val preflight = CompleteCaptionWorkflowPreflight(
            hasVideo = snapshot.videoUri != null,
            localRecognitionReady = module.runtimeStatus.mode == SpeechMode.LOCAL,
            deepSeekKeyConfigured = deepSeekKeyUi.value.state == DeepSeekKeyState.CONFIGURED,
            alreadyRunning = captionWorkflowJob?.isActive == true || snapshot.isWorking,
        )
        val blockingMessage = preflight.blockingMessage()
        if (blockingMessage != null) {
            if (!preflight.alreadyRunning) {
                mutableState.update {
                    it.copy(
                        captionWorkflowStage = CaptionWorkflowStage.FAILED,
                        status = if (!preflight.localRecognitionReady && preflight.hasVideo) {
                            module.runtimeStatus.detail
                        } else {
                            blockingMessage
                        },
                    )
                }
            }
            return
        }

        val uri = checkNotNull(snapshot.videoUri)
        val jobId = "caption-${elapsedRealtimeMs()}"
        val runner = CompleteCaptionWorkflowRunner()
        captionWorkflowJob = viewModelScope.launch {
            try {
                val outcome = runner.run(
                    recognize = { onStatus -> module.recognize(uri, onStatus) },
                    enhance = { captions, onStateChanged ->
                        enhancementService.enhance(jobId, captions, onStateChanged)
                    },
                    onStageChanged = { stage ->
                        mutableState.update {
                            it.copy(
                                isWorking = true,
                                asrRunning = stage == CaptionWorkflowStage.LOCAL_RECOGNIZING,
                                enhancementRunning = stage == CaptionWorkflowStage.AI_ENHANCING,
                                captionWorkflowStage = stage,
                                status = when (stage) {
                                    CaptionWorkflowStage.LOCAL_RECOGNIZING -> module.runtimeStatus.detail
                                    CaptionWorkflowStage.AI_ENHANCING -> "Enhancing captions with DeepSeek..."
                                    else -> it.status
                                },
                            )
                        }
                    },
                    onRecognitionStatus = { status -> mutableState.update { it.copy(status = status) } },
                    onEnhancementState = ::applyEnhancementProgress,
                )
                mutableState.update {
                    DerivedOutputPolicy.invalidateDerivedOutputs(
                        it.copy(
                            isWorking = false,
                            asrRunning = false,
                            enhancementRunning = false,
                            captionWorkflowStage = CaptionWorkflowStage.READY_FOR_EDIT,
                            captions = outcome.captions,
                            selectedCaptionId = outcome.captions.firstOrNull()?.id,
                            captionProcessing = CaptionProcessingSnapshot.from(outcome),
                            status = when (outcome.processingLevel) {
                                CaptionProcessingLevel.TWO_PASS_COMPLETE ->
                                    "DeepSeek 双阶段增强完成，共 ${outcome.captions.size} 条最终双语字幕。"
                                CaptionProcessingLevel.FIRST_PASS_REVIEW_REQUIRED ->
                                    "已保留 ${outcome.captions.size} 条首轮完整字幕；局部修复未完成，请人工审核。"
                                CaptionProcessingLevel.LOCAL_FALLBACK ->
                                    "已生成 ${outcome.captions.size} 条本地回退字幕；英文未经过标准歌词校正。"
                                CaptionProcessingLevel.LEGACY_UNKNOWN -> if (outcome.source == CaptionResultSource.CLOUD_AI) {
                                    "DeepSeek 已生成 ${outcome.captions.size} 条最终双语字幕。"
                                } else {
                                    "已生成 ${outcome.captions.size} 条本地回退字幕。"
                                }
                            },
                        ),
                    )
                }
            } catch (_: CancellationException) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        asrRunning = false,
                        enhancementRunning = false,
                        captionWorkflowStage = CaptionWorkflowStage.CANCELLED,
                        status = "字幕生成已取消；不会继续下一阶段。",
                        captionProcessing = it.captionProcessing.copy(state = CaptionEnhancementState.CANCELLED),
                    )
                }
                Log.i(LOG_TAG, "event=caption_workflow_cancelled finalCaptionsCommitted=false")
            } catch (error: CaptionEnhancementException) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        asrRunning = false,
                        enhancementRunning = false,
                        captionWorkflowStage = CaptionWorkflowStage.FAILED,
                        status = when (error.kind) {
                            CaptionEnhancementErrorKind.AUTHENTICATION -> "DeepSeek 验证失败；未提交本次识别的原始字幕。"
                            CaptionEnhancementErrorKind.LOCAL_TRANSLATION -> "本地回退失败；未提交本次识别的原始字幕。"
                            else -> "字幕增强失败；未提交本次识别的原始字幕。"
                        },
                        captionProcessing = it.captionProcessing.copy(
                            lastErrorKind = error.kind,
                        ),
                    )
                }
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        asrRunning = false,
                        enhancementRunning = false,
                        captionWorkflowStage = CaptionWorkflowStage.FAILED,
                        status = "字幕生成失败；未提交本次识别的原始字幕。",
                    )
                }
                Log.e(LOG_TAG, "event=caption_workflow_failed finalCaptionsCommitted=false", error)
            } finally {
                captionWorkflowJob = null
            }
        }
    }

    fun cancelCaptionWorkflow() {
        if (captionWorkflowJob?.isActive == true) {
            Log.i(LOG_TAG, "event=caption_workflow_cancel_requested")
            captionWorkflowJob?.cancel()
        }
    }

    private fun applyEnhancementProgress(processingState: CaptionEnhancementState) {
        mutableState.update { current ->
            current.copy(
                captionProcessing = current.captionProcessing.copy(state = processingState),
                status = when (processingState) {
                    CaptionEnhancementState.SONG_IDENTIFYING -> "正在综合整批字幕识别歌曲..."
                    CaptionEnhancementState.LYRICS_RETRIEVING -> "正在检索并验证标准歌词..."
                    CaptionEnhancementState.FIRST_PASS_ENHANCING -> "正在执行第一次整批字幕增强..."
                    CaptionEnhancementState.AUTO_SPLITTING -> "正在按真实歌词行拆分字幕..."
                    CaptionEnhancementState.LOCAL_REPAIRING -> "正在整批修复拆分后的字幕..."
                    CaptionEnhancementState.FINAL_VALIDATING -> "正在校验双阶段最终字幕..."
                    CaptionEnhancementState.CLOUD_PENDING -> "Sending subtitle cues to DeepSeek..."
                    CaptionEnhancementState.CLOUD_VALIDATING -> "Validating enhanced subtitle batch..."
                    CaptionEnhancementState.LOCAL_FALLBACK_APPLIED -> "DeepSeek unavailable; applying local Chinese fallback..."
                    else -> current.status
                },
            )
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

    fun addCaptionAt(playheadMs: Long) {
        mutableState.update { current ->
            val existingIds = current.captions.mapTo(mutableSetOf()) { it.id }
            var cueId: String
            do {
                cueId = "manual-${System.nanoTime()}"
            } while (cueId in existingIds)
            current.insertCaptionAt(playheadMs, cueId)
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

    fun splitCaption(
        cueId: String,
        firstEnglish: String,
        firstChinese: String,
        secondEnglish: String,
        secondChinese: String,
    ) {
        mutableState.update { current ->
            runCatching {
                current.splitCaptionCue(
                    cueId = cueId,
                    lines = listOf(
                        CaptionSplitLine(firstEnglish, firstChinese),
                        CaptionSplitLine(secondEnglish, secondChinese),
                    ),
                )
            }.getOrElse { error ->
                current.copy(status = "无法拆分字幕：${error.message ?: "输入无效"}")
            }
        }
    }

    fun splitCaptionDraft(cueId: String) {
        mutableState.update { it.splitCaptionCueDraft(cueId) }
        mutableCueSuggestion.value = CaptionCueSuggestionUiState()
    }

    fun requestCueSuggestion(cueId: String) {
        if (cueSuggestionJob?.isActive == true) return
        val snapshot = state.value
        val expectedCaptions = snapshot.captions
        val ordered = expectedCaptions.sortedWith(
            compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.id },
        )
        val index = ordered.indexOfFirst { it.id == cueId }
        if (index < 0) return
        if (deepSeekKeyUi.value.state != DeepSeekKeyState.CONFIGURED) {
            mutableCueSuggestion.value = CaptionCueSuggestionUiState(
                cueId = cueId,
                error = "请先保存并验证 DeepSeek API Key。",
            )
            return
        }
        val target = ordered[index]
        val splitSuffix = target.id.substringAfterLast(':', missingDelimiterValue = "")
        val parentId = target.id.substringBeforeLast(':', missingDelimiterValue = "")
        val siblingId = when (splitSuffix) {
            "1" -> "$parentId:2"
            "2" -> "$parentId:1"
            else -> null
        }
        val sibling = siblingId?.let { id -> ordered.firstOrNull { it.id == id } }
        val request = CaptionCueSuggestionRequest(
            jobId = "cue-suggestion-${elapsedRealtimeMs()}",
            target = target,
            sibling = sibling,
            previous = ordered.getOrNull(index - 1),
            next = ordered.getOrNull(index + 1),
            batch = ordered,
            songMatch = snapshot.captionProcessing.songMatch,
        )
        val generation = ++cueSuggestionGeneration
        mutableCueSuggestion.value = CaptionCueSuggestionUiState(
            cueId = cueId,
            running = true,
            expectedCaptions = expectedCaptions,
        )
        cueSuggestionJob = viewModelScope.launch {
            try {
                val suggestion = cueSuggestionService.suggest(request)
                if (generation != cueSuggestionGeneration) return@launch
                if (state.value.captions == expectedCaptions) {
                    mutableCueSuggestion.value = CaptionCueSuggestionUiState(
                        cueId = cueId,
                        proposal = suggestion,
                        expectedCaptions = expectedCaptions,
                    )
                } else {
                    mutableCueSuggestion.value = CaptionCueSuggestionUiState(
                        cueId = cueId,
                        error = "字幕已发生变化，请重新增强。",
                    )
                }
            } catch (_: CancellationException) {
                if (generation == cueSuggestionGeneration) {
                    mutableCueSuggestion.value = CaptionCueSuggestionUiState()
                }
            } catch (_: Throwable) {
                if (generation == cueSuggestionGeneration) {
                    mutableCueSuggestion.value = CaptionCueSuggestionUiState(
                        cueId = cueId,
                        error = "AI 增强失败，当前字幕未被修改。",
                    )
                }
            } finally {
                if (generation == cueSuggestionGeneration) cueSuggestionJob = null
            }
        }
    }

    fun applyCueSuggestion() {
        val suggestionState = cueSuggestion.value
        val proposal = suggestionState.proposal ?: return
        if (state.value.captions != suggestionState.expectedCaptions) {
            mutableCueSuggestion.value = CaptionCueSuggestionUiState(
                cueId = proposal.cueId,
                error = "字幕或上下文已发生变化，请重新增强。",
            )
            return
        }
        mutableState.update { it.withAppliedCueSuggestion(proposal) }
        mutableCueSuggestion.value = CaptionCueSuggestionUiState()
    }

    fun dismissCueSuggestion() {
        cueSuggestionGeneration += 1L
        cueSuggestionJob?.cancel()
        cueSuggestionJob = null
        mutableCueSuggestion.value = CaptionCueSuggestionUiState()
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
        mutableState.update {
            it.copy(
                isWorking = true,
                exportState = ExportState.RUNNING,
                exportUri = null,
                status = "Preparing video export...",
            )
        }
        exportJob = viewModelScope.launch {
            try {
                executeExportTask(
                    beginSession = { galleryGateway.begin(taskId, sourceUri = uri) },
                    beforeRender = {
                        // The API 36.1 emulator releases PlayerView's decoder surface asynchronously.
                        // Wait for that release before FFmpegKit opens a second video decoder.
                        delay(PREVIEW_RELEASE_DELAY_MS)
                    },
                    render = { exportSession ->
                        pipeline.export(
                            uri,
                            exportSession.destination.uri,
                            current.captions,
                            current.exportProfile,
                            current.captionLayout,
                            current.defaultCaptionStyle,
                        ) { status -> mutableState.update { it.copy(status = status) } }.fileSizeBytes
                    },
                    onRunning = {
                        mutableState.update {
                            it.copy(
                                exportState = ExportState.RUNNING,
                                status = "Rendering burned-in subtitles...",
                            )
                        }
                    },
                    onSucceeded = { published ->
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                exportUri = published.uri,
                                exportState = ExportState.SUCCEEDED,
                                status = "Export saved to system gallery.",
                            )
                        }
                    },
                    onCancelled = {
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = "Video export cancelled.",
                                exportUri = null,
                                exportState = ExportState.CANCELLED,
                            )
                        }
                    },
                    onFailed = { error ->
                        Log.e(
                            LOG_TAG,
                            "event=export_failed destinationUntouched=true " +
                                "errorType=${error.javaClass.simpleName} reasonCode=EXPORT_PIPELINE_FAILURE",
                        )
                        mutableState.update {
                            it.copy(
                                isWorking = false,
                                status = "Video export failed.",
                                exportUri = null,
                                exportState = ExportState.FAILED,
                            )
                        }
                    },
                )
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
        val resolved = resolveCaptionLayout(
            current.captionLayout,
            cue.layoutOverride,
        )
        val nextY = (resolved.yRatio - delta / 100f).coerceIn(0f, 1f)
        updateCue(cueId) { existing ->
            val next = (existing.layoutOverride ?: CaptionLayoutOverride()).copy(yRatio = nextY)
            existing.copy(layoutOverride = next.takeUnless { it.isEmpty })
        }
    }

    fun updateCueDirectPosition(cueId: String, xRatio: Float, yRatio: Float) {
        mutableState.update { it.withCueDirectPosition(cueId, xRatio, yRatio) }
    }

    fun updateCueDirectWidth(cueId: String, widthRatio: Float) {
        mutableState.update { it.withCueDirectWidth(cueId, widthRatio) }
    }

    fun updateCueDirectFontSize(cueId: String, fontSizeRatio: Float) {
        mutableState.update { it.withCueDirectFontSize(cueId, fontSizeRatio) }
    }

    fun applyCueBasicStyle(cueId: String, preset: CaptionBasicStylePreset) {
        mutableState.update { it.withCueBasicStyle(cueId, preset) }
    }

    fun updateCueUnifiedTextColor(cueId: String, colorHex: String) {
        mutableState.update { it.withCueUnifiedTextColor(cueId, colorHex) }
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
                            captionProcessing = snapshot.captionProcessing,
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
        const val PREVIEW_RELEASE_DELAY_MS = 1_500L
        const val MAX_TEXT_FILE_BYTES = 1 * 1_024 * 1_024
    }
}
