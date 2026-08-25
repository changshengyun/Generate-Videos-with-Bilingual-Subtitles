package com.example.lyriccaptioner.model

import kotlin.math.roundToLong

data class CaptionSplitLine(
    val english: String,
    val chinese: String,
)

/** Deterministic one-to-two cue split shared by cloud enhancement and manual editing. */
object CaptionCueSplitPolicy {
    const val PREFERRED_MIN_DURATION_MS = 833L
    const val PREFERRED_GAP_MS = 84L

    fun apply(parent: CaptionCue, lines: List<CaptionSplitLine>): List<CaptionCue> {
        require(lines.size in 1..2) { "A caption may resolve to one or two lines." }
        val normalized = lines.map { line ->
            CaptionSplitLine(
                english = line.english.trim(),
                chinese = line.chinese.trim(),
            ).also {
                require(it.english.isNotBlank()) { "English caption text is required." }
                require(it.chinese.isNotBlank()) { "Chinese caption text is required." }
            }
        }
        if (normalized.size == 1) {
            return listOf(parent.copy(english = normalized.single().english, chinese = normalized.single().chinese))
        }

        val durationMs = parent.endMs - parent.startMs
        require(durationMs >= 2L) { "The source cue is too short to split." }
        val gapMs = if (durationMs >= PREFERRED_MIN_DURATION_MS * 2L + PREFERRED_GAP_MS) {
            PREFERRED_GAP_MS
        } else {
            0L
        }
        val availableMs = durationMs - gapMs
        val firstWeight = textWeight(normalized[0].english)
        val secondWeight = textWeight(normalized[1].english)
        var firstDurationMs = (availableMs * firstWeight.toDouble() / (firstWeight + secondWeight))
            .roundToLong()
            .coerceIn(1L, availableMs - 1L)
        if (availableMs >= PREFERRED_MIN_DURATION_MS * 2L) {
            firstDurationMs = firstDurationMs.coerceIn(
                PREFERRED_MIN_DURATION_MS,
                availableMs - PREFERRED_MIN_DURATION_MS,
            )
        }
        val splitMs = parent.startMs + firstDurationMs
        return listOf(
            parent.copy(
                id = "${parent.id}:1",
                startMs = parent.startMs,
                endMs = splitMs,
                english = normalized[0].english,
                chinese = normalized[0].chinese,
            ),
            parent.copy(
                id = "${parent.id}:2",
                startMs = splitMs + gapMs,
                endMs = parent.endMs,
                english = normalized[1].english,
                chinese = normalized[1].chinese,
            ),
        )
    }

    fun suggestTextParts(value: String): Pair<String, String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "" to ""
        val candidates = Regex("[,，;；.!?。！？:]|\\s+")
            .findAll(trimmed)
            .map { it.range.last + 1 }
            .filter { it in 1 until trimmed.length }
            .toList()
        val midpoint = trimmed.length / 2
        val splitAt = candidates.minByOrNull { kotlin.math.abs(it - midpoint) } ?: midpoint
        return trimmed.substring(0, splitAt).trim() to trimmed.substring(splitAt).trim()
    }

    private fun textWeight(value: String): Int = value.count { !it.isWhitespace() }.coerceAtLeast(1)
}

fun EditorState.splitCaptionCue(
    cueId: String,
    lines: List<CaptionSplitLine>,
): EditorState {
    val sourceIndex = captions.indexOfFirst { it.id == cueId }
    if (sourceIndex < 0) return this
    val children = CaptionCueSplitPolicy.apply(captions[sourceIndex], lines)
    val updated = captions.toMutableList().apply {
        removeAt(sourceIndex)
        addAll(sourceIndex, children)
    }.sortedWith(compareBy<CaptionCue> { it.startMs }.thenBy { it.endMs }.thenBy { it.id })
    return DerivedOutputPolicy.invalidateDerivedOutputs(
        copy(
            captions = updated,
            selectedCaptionId = children.first().id,
            status = "字幕已拆分为两个独立时段。",
        ),
    )
}
