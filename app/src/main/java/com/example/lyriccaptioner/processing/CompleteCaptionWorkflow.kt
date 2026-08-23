package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionWorkflowStage
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementOutcome
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class CompleteCaptionWorkflowPreflight(
    val hasVideo: Boolean,
    val localRecognitionReady: Boolean,
    val deepSeekKeyConfigured: Boolean,
    val alreadyRunning: Boolean,
)

internal fun CompleteCaptionWorkflowPreflight.blockingMessage(): String? = when {
    alreadyRunning -> "字幕生成正在进行中。"
    !hasVideo -> "请先导入视频。"
    !localRecognitionReady -> "本地 Whisper 模型尚未就绪。"
    !deepSeekKeyConfigured -> "请先在\"AI 服务配置\"中保存并验证 DeepSeek API Key。"
    else -> null
}

/** Runs ASR and enhancement once, in order, without committing partial AI output. */
internal class CompleteCaptionWorkflowRunner {
    suspend fun run(
        recognize: suspend ((String) -> Unit) -> List<CaptionCue>,
        enhance: suspend (List<CaptionCue>, (CaptionEnhancementState) -> Unit) -> CaptionEnhancementOutcome,
        onStageChanged: (CaptionWorkflowStage) -> Unit,
        onRecognitionStatus: (String) -> Unit,
        onEnhancementState: (CaptionEnhancementState) -> Unit,
        onRawCaptionsReady: (List<CaptionCue>) -> Unit,
    ): CaptionEnhancementOutcome {
        onStageChanged(CaptionWorkflowStage.LOCAL_RECOGNIZING)
        val rawCaptions = recognize(onRecognitionStatus)
        currentCoroutineContext().ensureActive()
        onRawCaptionsReady(rawCaptions)

        onStageChanged(CaptionWorkflowStage.AI_ENHANCING)
        val outcome = enhance(rawCaptions, onEnhancementState)
        currentCoroutineContext().ensureActive()
        return outcome
    }
}
