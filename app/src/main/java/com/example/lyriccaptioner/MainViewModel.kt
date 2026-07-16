package com.example.lyriccaptioner

import android.net.Uri
import android.content.Context
import android.util.Log
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lyriccaptioner.captions.CaptionTimingEditor
import com.example.lyriccaptioner.captions.LyricLineAligner
import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.MediaState
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SpeechMode
import com.example.lyriccaptioner.model.SubtitleStyle
import com.example.lyriccaptioner.processing.AsrModule
import com.example.lyriccaptioner.processing.AppPipelineFactory
import com.example.lyriccaptioner.processing.CaptionPipeline
import com.example.lyriccaptioner.processing.MlKitLocalTranslator
import com.example.lyriccaptioner.processing.WhisperRuntimeStatus
import com.example.lyriccaptioner.processing.WhisperModelStore
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    context: Context,
    private var pipeline: CaptionPipeline,
    private val srtParser: SrtParser = SrtParser(),
    private val projectRepository: ProjectRepository = AndroidProjectRepository(context),
    private var asrModule: AsrModule = AppPipelineFactory.createAsrDefault(context),
    private val timingEditor: CaptionTimingEditor = CaptionTimingEditor(),
    private val lyricLineAligner: LyricLineAligner = LyricLineAligner(),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val whisperModelStore = WhisperModelStore(appContext)
    private val manualTranslator = MlKitLocalTranslator()
    private val mutableState = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutableState.asStateFlow()
    private var exportJob: Job? = null
    private var asrJob: Job? = null

    init {
        refreshSpeechRuntimeStatus()
    }

    fun importVideo(uri: Uri) {
        mutableState.update { it.copy(isWorking = true, status = "Checking video access...") }
        viewModelScope.launch {
            val access = withContext(Dispatchers.IO) { projectRepository.retainMediaReadAccess(uri) }
            if (access is MediaAccessResult.Unavailable) {
                Log.w(LOG_TAG, "event=video_import_failed reason=${access.reason}")
                mutableState.update { it.copy(isWorking = false, status = "Could not import video: ${access.reason}") }
                return@launch
            }
            val wasRelink = state.value.mediaState == MediaState.UNAVAILABLE && state.value.captions.isNotEmpty()
            val status = when (access) {
                is MediaAccessResult.Persisted -> if (wasRelink) "Video re-associated and persisted." else "Video imported with persistent access."
                is MediaAccessResult.SessionOnly -> "Video imported for this session only: ${access.reason}"
                is MediaAccessResult.ProviderUnsupported -> "Video imported: ${access.reason}"
                is MediaAccessResult.Unavailable -> access.reason
            }
            val maxDurationMs = state.value.modelState.maxVideoDurationMs
            val durationMs = access.durationMs
            if (durationMs != null && durationMs > maxDurationMs) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video is longer than 5 minutes. Please import a shorter clip.",
                    )
                }
                return@launch
            }

            mutableState.update {
                it.copy(
                    isWorking = false,
                    videoUri = uri,
                    videoDurationMs = durationMs,
                    mediaState = access.toEditorMediaState(),
                    status = status,
                    exportUri = null,
                    pendingSidecarSrt = null,
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
            it.copy(
                captions = cues,
                selectedCaptionId = cues.firstOrNull()?.id,
                status = if (cues.isEmpty()) {
                    "No valid SRT subtitles found."
                } else {
                    "Imported ${cues.size} subtitle cues from SRT."
                },
                pendingSidecarSrt = null,
            )
        }
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
            current.copy(
                captions = updated,
                status = "Matched ${matches.size} lyric lines. Review the suggested corrections.",
                pendingSidecarSrt = null,
            )
        }
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
            it.copy(
                captions = cues,
                selectedCaptionId = cues.firstOrNull()?.id,
                exportUri = null,
                pendingSidecarSrt = null,
                status = "Created ${cues.size} lyric captions. Adjust timing as needed.",
            )
        }
    }

    fun translateMissingChinese() {
        val snapshot = state.value
        val targets = snapshot.captions.filter { it.english.isNotBlank() && it.chinese.isBlank() }
        if (targets.isEmpty()) {
            mutableState.update { it.copy(status = "All English captions already have Chinese text.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Downloading translation model if needed...") }
            runCatching {
                manualTranslator.prepareBatch()
                targets.associate { cue ->
                    cue.id to manualTranslator.translateEnglishToChinese(cue.english)
                }
            }.onSuccess { translations ->
                mutableState.update { current ->
                    current.copy(
                        isWorking = false,
                        captions = current.captions.map { cue ->
                            translations[cue.id]?.let { chinese ->
                                cue.copy(chinese = chinese, confirmed = false)
                            } ?: cue
                        },
                        exportUri = null,
                        pendingSidecarSrt = null,
                        status = "Translated ${translations.size} captions to Chinese.",
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Chinese translation failed: ${error.message ?: "unknown error"}",
                    )
                }
            }
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
                    it.copy(
                        isWorking = false,
                        asrRunning = false,
                        captions = cues,
                        selectedCaptionId = cues.firstOrNull()?.id,
                        status = when (mode) {
                            SpeechMode.LOCAL -> "Local Whisper JNI generated ${cues.size} English captions."
                            SpeechMode.DEMO -> "Demo ASR generated ${cues.size} English captions; Local was not used."
                            SpeechMode.UNAVAILABLE -> module.runtimeStatus.detail
                        },
                    )
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
        viewModelScope.launch {
            mutableState.update {
                it.copy(isWorking = true, status = "Importing Whisper model...")
            }
            runCatching {
                whisperModelStore.install(uri)
            }.onSuccess { runtime ->
                pipeline = AppPipelineFactory.createDefault(appContext)
                asrModule = AppPipelineFactory.createAsrDefault(appContext)
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
            current.copy(
                captions = current.captions + cue,
                selectedCaptionId = cue.id,
                exportUri = null,
                pendingSidecarSrt = null,
                status = "Added a caption. Edit its text and timing.",
            )
        }
    }

    fun updateEnglishText(cueId: String, text: String) {
        updateCue(cueId) {
            it.copy(
                english = text,
                correctionCandidates = emptyList(),
                confirmed = false,
            )
        }
    }

    fun updateChineseText(cueId: String, text: String) {
        updateCue(cueId) { it.copy(chinese = text, confirmed = false) }
    }

    fun confirmCue(cueId: String) {
        updateCue(cueId) { it.copy(confirmed = true) }
    }

    fun applyCorrectionCandidate(cueId: String, candidate: String) {
        updateCue(cueId) { cue ->
            if (candidate !in cue.correctionCandidates) {
                cue
            } else {
                cue.copy(
                    english = candidate,
                    correctionCandidates = emptyList(),
                    confirmed = false,
                )
            }
        }
    }

    fun shiftCueStart(cueId: String, deltaMs: Long) {
        val captions = state.value.captions
        val index = captions.indexOfFirst { it.id == cueId }
        if (index < 0) return
        val earliestStartMs = captions.getOrNull(index - 1)?.endMs ?: 0L
        updateCue(cueId) {
            timingEditor.shiftStart(it, deltaMs, earliestStartMs).copy(confirmed = false)
        }
    }

    fun shiftCueEnd(cueId: String, deltaMs: Long) {
        val current = state.value
        val index = current.captions.indexOfFirst { it.id == cueId }
        if (index < 0) return
        val latestEndMs = current.captions.getOrNull(index + 1)?.startMs
        updateCue(cueId) {
            timingEditor.shiftEnd(it, deltaMs, current.videoDurationMs, latestEndMs)
                .copy(confirmed = false)
        }
    }

    fun selectCue(cueId: String) {
        mutableState.update { it.copy(selectedCaptionId = cueId) }
    }

    fun deleteCaption(cueId: String) {
        mutableState.update { current ->
            val remaining = current.captions.filterNot { it.id == cueId }
            current.copy(
                captions = remaining,
                selectedCaptionId = remaining.firstOrNull()?.id,
                exportUri = null,
                pendingSidecarSrt = null,
                status = "Caption removed.",
            )
        }
    }

    fun exportVideo(destinationUri: Uri) {
        val current = state.value
        val rejection = when {
            current.videoUri == null -> "no_video" to "No video selected. Import a video before exporting."
            current.captions.isEmpty() -> "no_captions" to "No subtitles available. Import or create subtitles before exporting."
            else -> null
        }
        if (rejection != null) {
            val destinationDeleted = deleteExportDestination(destinationUri)
            Log.w(
                LOG_TAG,
                "event=export_rejected reason=${rejection.first} destinationDeleted=$destinationDeleted",
            )
            mutableState.update { it.copy(status = rejection.second) }
            return
        }

        val uri = checkNotNull(current.videoUri)
        Log.i(LOG_TAG, "event=export_started captionCount=${current.captions.size}")
        exportJob = viewModelScope.launch {
            try {
                mutableState.update { it.copy(isWorking = true, status = "Rendering burned-in subtitles...") }
                // The API 36.1 emulator releases PlayerView's decoder surface asynchronously.
                // Wait for that release before Transformer opens a second video decoder.
                delay(PREVIEW_RELEASE_DELAY_MS)
                pipeline.export(
                    uri,
                    destinationUri,
                    current.captions,
                    current.exportProfile,
                ) { status ->
                    mutableState.update { it.copy(status = status) }
                }.also { exportUri ->
                    mutableState.update {
                        it.copy(
                            isWorking = false,
                            exportUri = exportUri,
                            status = "Export complete: $exportUri",
                        )
                    }
                }
            } catch (error: CancellationException) {
                deleteExportDestination(destinationUri)
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video export cancelled.",
                    )
                }
                throw error
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video export failed: ${error.message ?: "unknown error"}",
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

    private fun deleteExportDestination(destinationUri: Uri): Boolean {
        val deletedByDocumentApi = runCatching {
            DocumentsContract.deleteDocument(appContext.contentResolver, destinationUri)
        }.getOrDefault(false)
        if (deletedByDocumentApi) return true
        return runCatching {
            appContext.contentResolver.delete(destinationUri, null, null) > 0
        }.getOrElse { error ->
            Log.w(LOG_TAG, "event=export_destination_cleanup_failed", error)
            false
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

    fun updateFontSize(delta: Int) {
        updateSubtitleStyle { style ->
            style.copy(fontSizeSp = (style.fontSizeSp + delta).coerceIn(14, 48))
        }
    }

    fun updateBottomMargin(delta: Int) {
        updateSubtitleStyle { style ->
            style.copy(bottomMarginPercent = (style.bottomMarginPercent + delta).coerceIn(4, 28))
        }
    }

    fun updateEnglishColor(colorHex: String) {
        updateSubtitleStyle { style -> style.copy(primaryColorHex = colorHex) }
    }

    fun updateChineseColor(colorHex: String) {
        updateSubtitleStyle { style -> style.copy(secondaryColorHex = colorHex) }
    }

    fun updateOutlineColor(colorHex: String) {
        updateSubtitleStyle { style -> style.copy(outlineColorHex = colorHex) }
    }

    fun saveProjectArchive(destinationUri: Uri) {
        val snapshot = currentSnapshot()
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Saving project archive...") }
            when (val result = projectRepository.save(snapshot, destinationUri)) {
                is ProjectSaveResult.Success -> mutableState.update {
                    Log.i(LOG_TAG, "event=project_save_completed captionCount=${snapshot.captions.size}")
                    it.copy(isWorking = false, status = "Project archive saved: ${result.destinationUri}")
                }
                is ProjectSaveResult.Failure -> mutableState.update {
                    Log.w(LOG_TAG, "event=project_save_failed kind=${result.error.kind}")
                    it.copy(isWorking = false, status = "Could not save project: ${result.error.message}")
                }
            }
        }
    }

    fun importProjectArchive(sourceUri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Opening project archive...") }
            when (val result = projectRepository.load(sourceUri)) {
                is ProjectLoadResult.Success -> {
                   val snapshot = result.snapshot
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
                        is MediaAccessResult.ProviderUnsupported -> "Project restored; provider cannot persist video access."
                        is MediaAccessResult.Unavailable -> "Project restored; video is unavailable. Select a new video to re-associate it."
                    }
                    mutableState.update {
                        Log.i(
                            LOG_TAG,
                            "event=project_load_completed mediaState=$mediaState captionCount=${snapshot.captions.size}",
                        )
                        it.copy(
                            isWorking = false,
                            videoUri = snapshot.videoUri?.let(Uri::parse),
                            videoDurationMs = snapshot.videoDurationMs ?: result.mediaAccess.durationMs,
                            mediaState = mediaState,
                            captions = snapshot.captions,
                            selectedCaptionId = snapshot.captions.firstOrNull()?.id,
                            exportProfile = snapshot.exportProfile,
                            status = status,
                            pendingSidecarSrt = null,
                        )
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
            current.copy(
                captions = current.captions.map { cue ->
                    if (cue.id == cueId) transform(cue) else cue
                },
                exportUri = null,
                pendingSidecarSrt = null,
            )
        }
    }

    private fun refreshSpeechRuntimeStatus() {
        updateSpeechRuntime(asrModule.runtimeStatus)
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

    private fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        mutableState.update { current ->
            current.copy(
                exportProfile = current.exportProfile.copy(
                    subtitleStyle = transform(current.exportProfile.subtitleStyle),
                ),
            )
        }
    }

    private fun currentSnapshot(): ProjectSnapshot {
        val current = state.value
        return ProjectSnapshot(
            videoUri = current.videoUri?.toString(),
            videoDurationMs = current.videoDurationMs,
            captions = current.captions,
            exportProfile = current.exportProfile,
        )
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val pipeline = AppPipelineFactory.createDefault(context)
            return MainViewModel(context, pipeline) as T
        }
    }

    private companion object {
        const val LOG_TAG = "MainViewModel"
        const val DEFAULT_CAPTION_DURATION_MS = 2_000L
        const val MIN_CAPTION_DURATION_MS = 100L
        const val PREVIEW_RELEASE_DELAY_MS = 1_500L
    }
}
