package com.example.lyriccaptioner

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lyriccaptioner.captions.CaptionTimingEditor
import com.example.lyriccaptioner.captions.LyricLineAligner
import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.EditorState
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SubtitleStyle
import com.example.lyriccaptioner.processing.AppPipelineFactory
import com.example.lyriccaptioner.processing.CaptionPipeline
import com.example.lyriccaptioner.processing.MlKitLocalTranslator
import com.example.lyriccaptioner.processing.WhisperModelStore
import com.example.lyriccaptioner.project.ProjectArchive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(
    context: Context,
    private var pipeline: CaptionPipeline,
    private val srtParser: SrtParser = SrtParser(),
    private val projectArchive: ProjectArchive = ProjectArchive(),
    private val timingEditor: CaptionTimingEditor = CaptionTimingEditor(),
    private val lyricLineAligner: LyricLineAligner = LyricLineAligner(),
) : ViewModel() {
    private val appContext = context.applicationContext
    private val whisperModelStore = WhisperModelStore(appContext)
    private val manualTranslator = MlKitLocalTranslator()
    private val mutableState = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutableState.asStateFlow()

    init {
        refreshSpeechRuntimeStatus()
    }

    fun importVideo(uri: Uri, durationMs: Long?) {
        val maxDurationMs = state.value.modelState.maxVideoDurationMs
        if (durationMs != null && durationMs > maxDurationMs) {
            mutableState.update {
                it.copy(
                    status = "Video is longer than 5 minutes. Please import a shorter clip.",
                )
            }
            return
        }

        mutableState.update {
            it.copy(
                videoUri = uri,
                videoDurationMs = durationMs,
                status = "Video imported. Ready to generate subtitles.",
                exportUri = null,
                pendingSidecarSrt = null,
                pendingProjectArchive = null,
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
                pendingProjectArchive = null,
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
                pendingProjectArchive = null,
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
                pendingProjectArchive = null,
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
                        pendingProjectArchive = null,
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
        val uri = state.value.videoUri ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Extracting audio...") }
            runCatching {
                pipeline.generateDraft(uri) { status ->
                    mutableState.update { it.copy(status = status) }
                }
            }.onSuccess { cues ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        captions = cues,
                        selectedCaptionId = cues.firstOrNull()?.id,
                        status = "Draft subtitles generated. Review highlighted low-confidence lines.",
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Caption generation failed: ${error.message ?: "unknown error"}",
                    )
                }
            }
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
                pendingProjectArchive = null,
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
                pendingProjectArchive = null,
                status = "Caption removed.",
            )
        }
    }

    fun exportVideo(destinationUri: Uri) {
        val current = state.value
        val uri = current.videoUri ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isWorking = true, status = "Rendering burned-in subtitles...") }
            // The API 36.1 emulator releases PlayerView's decoder surface asynchronously.
            // Wait for that release before Transformer opens a second video decoder.
            delay(PREVIEW_RELEASE_DELAY_MS)
            runCatching {
                pipeline.export(
                    uri,
                    destinationUri,
                    current.captions,
                    current.exportProfile,
                ) { status ->
                    mutableState.update { it.copy(status = status) }
                }
            }.onSuccess { exportUri ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        exportUri = exportUri,
                        status = "Export complete: $exportUri",
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isWorking = false,
                        status = "Video export failed: ${error.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun exportSidecarSrt() {
        val srt = pipeline.exportSidecarSrt(state.value.captions)
        mutableState.update {
            it.copy(
                status = "SRT sidecar is ready to save.",
                pendingSidecarSrt = srt,
                pendingProjectArchive = null,
            )
        }
    }

    fun sidecarSrtSaved(uri: Uri) {
        mutableState.update {
            it.copy(
                status = "SRT sidecar saved: $uri",
                pendingSidecarSrt = null,
                pendingProjectArchive = null,
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

    fun exportProjectArchive(): String {
        val archive = projectArchive.write(currentSnapshot())
        mutableState.update {
            it.copy(
                status = "Project archive is ready to save.",
                pendingProjectArchive = archive,
                pendingSidecarSrt = null,
            )
        }
        return archive
    }

    fun importProjectArchive(raw: String) {
        val snapshot = runCatching { projectArchive.read(raw) }.getOrElse { error ->
            mutableState.update { it.copy(status = "Could not import project: ${error.message}") }
            return
        }

        mutableState.update {
            it.copy(
                videoUri = snapshot.videoUri?.let(Uri::parse),
                videoDurationMs = snapshot.videoDurationMs,
                captions = snapshot.captions,
                selectedCaptionId = snapshot.captions.firstOrNull()?.id,
                exportProfile = snapshot.exportProfile,
                status = "Project archive imported.",
                pendingSidecarSrt = null,
                pendingProjectArchive = null,
            )
        }
    }

    fun projectArchiveSaved(uri: Uri) {
        mutableState.update {
            it.copy(
                status = "Project archive saved: $uri",
                pendingProjectArchive = null,
            )
        }
    }

    fun projectArchiveSaveFailed(message: String) {
        mutableState.update {
            it.copy(status = "Could not save project archive: $message")
        }
    }

    private fun updateCue(cueId: String, transform: (CaptionCue) -> CaptionCue) {
        mutableState.update { current ->
            current.copy(
                captions = current.captions.map { cue ->
                    if (cue.id == cueId) transform(cue) else cue
                },
                exportUri = null,
                pendingSidecarSrt = null,
                pendingProjectArchive = null,
            )
        }
    }

    private fun refreshSpeechRuntimeStatus() {
        updateSpeechRuntime(whisperModelStore.status())
    }

    private fun updateSpeechRuntime(runtime: com.example.lyriccaptioner.processing.WhisperRuntimeStatus) {
        mutableState.update { current ->
            current.copy(
                modelState = current.modelState.copy(
                    speechModelReady = runtime.localRecognitionReady,
                    speechModelInstalled = runtime.modelInstalled,
                    speechNativeLibraryReady = runtime.nativeLibraryReady,
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
        const val DEFAULT_CAPTION_DURATION_MS = 2_000L
        const val MIN_CAPTION_DURATION_MS = 100L
        const val PREVIEW_RELEASE_DELAY_MS = 1_500L
    }
}
