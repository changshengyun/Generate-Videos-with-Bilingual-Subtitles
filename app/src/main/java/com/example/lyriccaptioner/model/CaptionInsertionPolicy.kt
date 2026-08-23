package com.example.lyriccaptioner.model

internal sealed interface CaptionInsertionDecision {
    data class Allowed(val startMs: Long, val endMs: Long) : CaptionInsertionDecision
    data class Rejected(val message: String) : CaptionInsertionDecision
}

internal object CaptionInsertionPolicy {
    fun resolve(
        captions: List<CaptionCue>,
        playheadMs: Long,
        videoDurationMs: Long?,
    ): CaptionInsertionDecision {
        val durationMs = videoDurationMs
            ?: return CaptionInsertionDecision.Rejected("无法新增字幕：视频总时长未知。")
        if (captions.isEmpty()) {
            return CaptionInsertionDecision.Rejected("无法新增字幕：请先生成字幕。")
        }
        if (playheadMs !in 0L..durationMs) {
            return CaptionInsertionDecision.Rejected("无法新增字幕：当前播放位置超出视频范围。")
        }

        val sorted = captions.sortedWith(compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs })
        if (sorted.zipWithNext().any { (previous, next) -> previous.endMs > next.startMs }) {
            return CaptionInsertionDecision.Rejected("无法新增字幕：相邻字幕时间存在重叠。")
        }
        if (sorted.any { cue -> playheadMs >= cue.startMs && playheadMs < cue.endMs }) {
            return CaptionInsertionDecision.Rejected("无法新增字幕：当前位置已有字幕。")
        }

        val slot = when {
            playheadMs < sorted.first().startMs -> 0L to sorted.first().startMs
            playheadMs >= sorted.last().endMs -> sorted.last().endMs to durationMs
            else -> sorted.zipWithNext()
                .firstOrNull { (previous, next) ->
                    playheadMs >= previous.endMs && playheadMs < next.startMs
                }
                ?.let { (previous, next) -> previous.endMs to next.startMs }
        } ?: return CaptionInsertionDecision.Rejected("无法新增字幕：当前位置没有可用空档。")

        if (slot.second - slot.first < MIN_INSERTION_DURATION_MS) {
            return CaptionInsertionDecision.Rejected("无法新增字幕：可用空档不足 100ms。")
        }
        return CaptionInsertionDecision.Allowed(slot.first, slot.second)
    }

    private const val MIN_INSERTION_DURATION_MS = 100L
}

internal fun EditorState.insertCaptionAt(playheadMs: Long, cueId: String): EditorState =
    when (val decision = CaptionInsertionPolicy.resolve(captions, playheadMs, videoDurationMs)) {
        is CaptionInsertionDecision.Rejected -> copy(status = decision.message)
        is CaptionInsertionDecision.Allowed -> {
            val cue = CaptionCue(
                id = cueId,
                startMs = decision.startMs,
                endMs = decision.endMs,
                english = "",
                chinese = "",
                confidence = 1f,
                confirmed = false,
            )
            DerivedOutputPolicy.invalidateDerivedOutputs(
                copy(
                    captions = (captions + cue).sortedWith(
                        compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs },
                    ),
                    selectedCaptionId = cue.id,
                    status = "已在当前播放位置的空档新增字幕，请输入英文和中文。",
                ),
            )
        }
    }
