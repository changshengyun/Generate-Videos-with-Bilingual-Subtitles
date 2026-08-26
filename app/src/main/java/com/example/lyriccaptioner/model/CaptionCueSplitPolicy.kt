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
        val splitAt = candidates.minByOrNull { kotlin.math.abs(it - midpoint) }
            ?: return trimmed to ""
        return trimmed.substring(0, splitAt).trim() to trimmed.substring(splitAt).trim()
    }

    fun suggestDraftLines(cue: CaptionCue): List<CaptionSplitLine>? {
        val english = cue.english.trim()
        val chinese = cue.chinese.trim()
        if (english.isEmpty() || chinese.isEmpty()) return null
        val boundary = englishBoundary(english) ?: return null
        val firstEnglish = english.substring(0, boundary).trim()
        val secondEnglish = english.substring(boundary).trim()
        if (firstEnglish.isEmpty() || secondEnglish.isEmpty()) return null
        val ratio = firstEnglish.count { !it.isWhitespace() }.toDouble() /
            english.count { !it.isWhitespace() }.coerceAtLeast(1)
        val chineseBoundary = chineseBoundary(chinese, ratio) ?: return null
        val firstChinese = chinese.substring(0, chineseBoundary).trim()
        val secondChinese = chinese.substring(chineseBoundary).trim()
        if (firstChinese.isEmpty() || secondChinese.isEmpty()) return null
        return listOf(
            CaptionSplitLine(firstEnglish, firstChinese),
            CaptionSplitLine(secondEnglish, secondChinese),
        )
    }

    /**
     * Repairs the narrow provider case where one declared line contains two complete sentences.
     * Both languages must expose a strong punctuation boundary; otherwise the cue is left intact
     * for manual review instead of cutting either language mechanically.
     */
    fun suggestStrongBilingualSentenceLines(
        english: String,
        chinese: String,
    ): List<CaptionSplitLine>? {
        val normalizedEnglish = english.trim()
        val normalizedChinese = chinese.trim()
        val englishBoundary = strongEnglishSentenceBoundary(normalizedEnglish) ?: return null
        val firstEnglish = normalizedEnglish.substring(0, englishBoundary).trim()
        val secondEnglish = normalizedEnglish.substring(englishBoundary).trim()
        if (englishWordCount(firstEnglish) < 3 || englishWordCount(secondEnglish) < 3) return null

        val ratio = firstEnglish.count { !it.isWhitespace() }.toDouble() /
            normalizedEnglish.count { !it.isWhitespace() }.coerceAtLeast(1)
        val chineseBoundary = strongChineseSentenceBoundary(normalizedChinese, ratio) ?: return null
        val firstChinese = normalizedChinese.substring(0, chineseBoundary).trim()
        val secondChinese = normalizedChinese.substring(chineseBoundary).trim()
        if (firstChinese.isEmpty() || secondChinese.isEmpty()) return null
        return listOf(
            CaptionSplitLine(firstEnglish, firstChinese),
            CaptionSplitLine(secondEnglish, secondChinese),
        )
    }

    private fun englishBoundary(value: String): Int? {
        val midpoint = value.length / 2
        val strong = Regex("[.!?;,:](?:\\s+|$)").findAll(value)
            .map { it.range.last + 1 }
            .filter { it in 1 until value.length }
            .toList()
        if (strong.isNotEmpty()) return strong.minBy { kotlin.math.abs(it - midpoint) }
        val conjunctions = Regex("(?i)\\s+(?:and|but|or|so|because|when|while|then)\\s+")
            .findAll(value)
            .map { it.range.first + 1 }
            .filter { it in 1 until value.length }
            .toList()
        if (conjunctions.isNotEmpty()) return conjunctions.minBy { kotlin.math.abs(it - midpoint) }
        return Regex("\\s+").findAll(value)
            .map { it.range.last + 1 }
            .filter { it in 1 until value.length }
            .minByOrNull { kotlin.math.abs(it - midpoint) }
    }

    private fun chineseBoundary(value: String, ratio: Double): Int? {
        if (value.length < 2) return null
        val target = (value.length * ratio).roundToLong().toInt().coerceIn(1, value.length - 1)
        val punctuation = Regex("[，。！？；、：]").findAll(value)
            .map { it.range.last + 1 }
            .filter { it in 1 until value.length }
            .toList()
        return punctuation.minByOrNull { kotlin.math.abs(it - target) } ?: target
    }

    private fun strongEnglishSentenceBoundary(value: String): Int? {
        if (value.length < 2) return null
        val midpoint = value.length / 2
        return Regex("[.!?](?:[\\\"']?)(?=\\s+[A-Z])")
            .findAll(value)
            .map { it.range.last + 1 }
            .filter { it in 1 until value.length }
            .minByOrNull { kotlin.math.abs(it - midpoint) }
    }

    private fun strongChineseSentenceBoundary(value: String, ratio: Double): Int? {
        if (value.length < 2) return null
        val target = (value.length * ratio).roundToLong().toInt().coerceIn(1, value.length - 1)
        return Regex("[。！？!?]")
            .findAll(value)
            .map { it.range.last + 1 }
            .filter { it in 1 until value.length }
            .minByOrNull { kotlin.math.abs(it - target) }
    }

    private fun englishWordCount(value: String): Int = Regex("[A-Za-z]+(?:['’-][A-Za-z]+)*")
        .findAll(value)
        .count()

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

fun EditorState.splitCaptionCueDraft(cueId: String): EditorState {
    val source = captions.firstOrNull { it.id == cueId } ?: return this
    val lines = CaptionCueSplitPolicy.suggestDraftLines(source)
        ?: return copy(status = "找不到安全的分句边界；请先补充标点或调整文字。")
    return splitCaptionCue(cueId, lines)
}
