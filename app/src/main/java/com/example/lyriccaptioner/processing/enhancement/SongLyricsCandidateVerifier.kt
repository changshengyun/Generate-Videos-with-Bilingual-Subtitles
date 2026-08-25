package com.example.lyriccaptioner.processing.enhancement

import kotlin.math.ceil
import kotlin.math.max

data class SongLyricsVerificationMetrics(
    val eligibleCueCount: Int,
    val matchedCueCount: Int,
    val coverage: Double,
    val averageSimilarity: Double,
    val medianSimilarity: Double,
    val confidence: Double,
)

data class VerifiedSongLyrics(
    val candidate: SongLyricsCandidate,
    val metrics: SongLyricsVerificationMetrics,
    val cueCanonicalLines: Map<String, List<String>>,
    val canonicalRange: CanonicalLyricsRange,
) {
    val cueCanonicalEnglish: Map<String, String>
        get() = cueCanonicalLines.mapValues { (_, lines) -> lines.joinToString(" ") }

    override fun toString(): String =
        "VerifiedSongLyrics(sourceId=${candidate.sourceId}, matchedCueCount=${metrics.matchedCueCount}, confidence=${metrics.confidence})"
}

/**
 * Monotonic token-span verifier for a complete cue batch and searched lyrics.
 *
 * Each cue may consume a bounded contiguous token span crossing at most two lyric lines. Adjacent
 * cues may therefore split one lyric line, while one cue may merge two lyric lines. Dynamic
 * programming keeps spans ordered and non-overlapping in O(cues * tokens * maxSpanTokens).
 * Confirmation uses one score for every eligible cue; skipped cues remain zero-valued negative
 * evidence instead of disappearing from averages and medians.
 */
class SongLyricsCandidateVerifier {
    fun verify(
        cues: List<CaptionEnhancementRequestCue>,
        candidate: SongLyricsCandidate,
    ): VerifiedSongLyrics? {
        val eligible = cues.filter { it.rawEnglish.isNotBlank() }
        if (eligible.size < MIN_ELIGIBLE_CUES) return null
        val lyricLines = candidate.completeEnglishLyrics.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(MAX_LYRIC_LINES + 1)
            .toList()
        if (lyricLines.size < MIN_ELIGIBLE_CUES || lyricLines.size > MAX_LYRIC_LINES) return null

        val lyricTokens = tokenizeLyrics(lyricLines) ?: return null
        val alignment = align(eligible, lyricTokens)
        val allSimilarities = eligible.map { alignment[it.id]?.similarity ?: 0.0 }.sorted()
        val matchedCueCount = alignment.size
        val minimumMatches = max(MIN_MATCHED_CUES, ceil(eligible.size * MIN_COVERAGE).toInt())
        if (matchedCueCount < minimumMatches) return null

        val coverage = matchedCueCount.toDouble() / eligible.size
        val average = allSimilarities.average()
        val median = median(allSimilarities)
        val confidence = 0.55 * average + 0.25 * median + 0.20 * coverage
        if (coverage < MIN_COVERAGE || average < MIN_AVERAGE_SIMILARITY ||
            median < MIN_MEDIAN_SIMILARITY || confidence < MIN_CONFIDENCE
        ) {
            return null
        }

        val matchedLineIndices = alignment.values.flatMap { it.lineIndices.toList() }
        if (matchedLineIndices.isEmpty()) return null

        return VerifiedSongLyrics(
            candidate = candidate,
            metrics = SongLyricsVerificationMetrics(
                eligibleCueCount = eligible.size,
                matchedCueCount = matchedCueCount,
                coverage = coverage,
                averageSimilarity = average,
                medianSimilarity = median,
                confidence = confidence,
            ),
            cueCanonicalLines = eligible.mapNotNull { cue ->
                alignment[cue.id]?.let { cue.id to it.canonicalLines }
            }.toMap(linkedMapOf()),
            canonicalRange = CanonicalLyricsRange(
                startLineInclusive = matchedLineIndices.min(),
                endLineInclusive = matchedLineIndices.max(),
            ),
        )
    }

    private fun tokenizeLyrics(lines: List<String>): List<LyricToken>? {
        val result = ArrayList<LyricToken>()
        lines.forEachIndexed { lineIndex, line ->
            val matches = WORD_PATTERN.findAll(line).toList()
            matches.forEachIndexed { tokenIndex, match ->
                val display = match.value
                val token = normalize(display)
                if (token.isBlank()) return@forEachIndexed
                result += LyricToken(
                    value = token,
                    lineIndex = lineIndex,
                    sourceLine = line,
                    startIndex = match.range.first,
                    nextTokenStart = matches.getOrNull(tokenIndex + 1)?.range?.first ?: line.length,
                    isFirstInLine = tokenIndex == 0,
                    isLastInLine = tokenIndex == matches.lastIndex,
                )
                if (result.size > MAX_LYRIC_TOKENS) return null
            }
        }
        return result.takeIf { it.size >= MIN_ELIGIBLE_CUES }
    }

    private fun align(
        cues: List<CaptionEnhancementRequestCue>,
        lyricTokens: List<LyricToken>,
    ): Map<String, SpanMatch> {
        val cueCount = cues.size
        val tokenCount = lyricTokens.size
        val scores = Array(cueCount + 1) { DoubleArray(tokenCount + 1) { NEGATIVE_INFINITY } }
        val actions = Array(cueCount + 1) { ByteArray(tokenCount + 1) }
        val spanLengths = Array(cueCount + 1) { ByteArray(tokenCount + 1) }
        for (tokenIndex in 0..tokenCount) scores[0][tokenIndex] = 0.0

        for (cueIndex in 1..cueCount) {
            scores[cueIndex][0] = scores[cueIndex - 1][0] - UNMATCHED_CUE_PENALTY
            actions[cueIndex][0] = ACTION_SKIP_CUE
            val cueText = cues[cueIndex - 1].rawEnglish
            val cueTokenCount = normalize(cueText).split(' ').count(String::isNotBlank)
            val minimumSpan = max(1, cueTokenCount / 2)
            val maximumSpan = (cueTokenCount * 2 + 4).coerceIn(MIN_MAX_SPAN_TOKENS, MAX_SPAN_TOKENS)

            for (tokenIndex in 1..tokenCount) {
                var best = scores[cueIndex][tokenIndex - 1]
                var action = ACTION_SKIP_TOKEN
                var bestSpanLength = 0

                val skipCue = scores[cueIndex - 1][tokenIndex] - UNMATCHED_CUE_PENALTY
                if (skipCue > best + SCORE_EPSILON) {
                    best = skipCue
                    action = ACTION_SKIP_CUE
                }

                val upperSpan = minOf(maximumSpan, tokenIndex)
                for (length in minimumSpan..upperSpan) {
                    val start = tokenIndex - length
                    if (lyricTokens[tokenIndex - 1].lineIndex - lyricTokens[start].lineIndex >= MAX_LINES_PER_SPAN) {
                        continue
                    }
                    val canonical = lyricTokens.subList(start, tokenIndex).joinToString(" ") { it.value }
                    val similarity = similarity(cueText, canonical)
                    if (similarity < MIN_CUE_SIMILARITY) continue
                    val matched = scores[cueIndex - 1][start] + similarity
                    if (matched > best + SCORE_EPSILON ||
                        nearlyEqual(matched, best) && action != ACTION_MATCH
                    ) {
                        best = matched
                        action = ACTION_MATCH
                        bestSpanLength = length
                    }
                }

                scores[cueIndex][tokenIndex] = best
                actions[cueIndex][tokenIndex] = action
                spanLengths[cueIndex][tokenIndex] = bestSpanLength.toByte()
            }
        }

        val reversed = mutableListOf<Pair<String, SpanMatch>>()
        var cueIndex = cueCount
        var tokenIndex = tokenCount
        while (cueIndex > 0) {
            when (actions[cueIndex][tokenIndex]) {
                ACTION_SKIP_TOKEN -> tokenIndex--
                ACTION_SKIP_CUE -> cueIndex--
                ACTION_MATCH -> {
                    val length = spanLengths[cueIndex][tokenIndex].toInt() and 0xff
                    val start = tokenIndex - length
                    val canonical = lyricTokens.subList(start, tokenIndex).joinToString(" ") { it.value }
                    val cue = cues[cueIndex - 1]
                    val span = lyricTokens.subList(start, tokenIndex)
                    val canonicalLines = span
                        .groupBy(LyricToken::lineIndex)
                        .values
                        .map { lineTokens ->
                            val first = lineTokens.first()
                            val last = lineTokens.last()
                            val startIndex = if (first.isFirstInLine) 0 else first.startIndex
                            val endExclusive = if (last.isLastInLine) {
                                last.sourceLine.length
                            } else {
                                last.nextTokenStart
                            }
                            first.sourceLine.substring(startIndex, endExclusive).trim()
                        }
                    reversed += cue.id to SpanMatch(
                        canonicalEnglish = canonical,
                        canonicalLines = canonicalLines,
                        similarity = similarity(cue.rawEnglish, canonical),
                        lineIndices = span.first().lineIndex..span.last().lineIndex,
                    )
                    cueIndex--
                    tokenIndex = start
                }
                else -> cueIndex--
            }
        }
        return reversed.asReversed().toMap(linkedMapOf())
    }

    private fun similarity(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) return 0.0
        if (normalizedLeft == normalizedRight) return 1.0
        val characterScore = editSimilarity(normalizedLeft, normalizedRight)
        val leftTokens = normalizedLeft.split(' ').filter(String::isNotBlank).toSet()
        val rightTokens = normalizedRight.split(' ').filter(String::isNotBlank).toSet()
        val tokenIntersection = leftTokens.intersect(rightTokens).size
        val tokenDice = if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            0.0
        } else {
            2.0 * tokenIntersection / (leftTokens.size + rightTokens.size)
        }
        return max(characterScore, 0.65 * characterScore + 0.35 * tokenDice)
    }

    private fun editSimilarity(left: String, right: String): Double {
        val distances = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            var previousDiagonal = distances[0]
            distances[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val previousAbove = distances[rightIndex + 1]
                val replacementCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                distances[rightIndex + 1] = minOf(
                    distances[rightIndex + 1] + 1,
                    distances[rightIndex] + 1,
                    previousDiagonal + replacementCost,
                )
                previousDiagonal = previousAbove
            }
        }
        return 1.0 - distances.last().toDouble() / max(left.length, right.length)
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun median(sorted: List<Double>): Double {
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun nearlyEqual(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) < SCORE_EPSILON

    private data class LyricToken(
        val value: String,
        val lineIndex: Int,
        val sourceLine: String,
        val startIndex: Int,
        val nextTokenStart: Int,
        val isFirstInLine: Boolean,
        val isLastInLine: Boolean,
    )

    private data class SpanMatch(
        val canonicalEnglish: String,
        val canonicalLines: List<String>,
        val similarity: Double,
        val lineIndices: IntRange,
    )

    companion object {
        const val MIN_ELIGIBLE_CUES = 3
        const val MIN_MATCHED_CUES = 3
        const val MIN_COVERAGE = 0.75
        const val MIN_CUE_SIMILARITY = 0.62
        const val MIN_AVERAGE_SIMILARITY = 0.78
        const val MIN_MEDIAN_SIMILARITY = 0.78
        const val MIN_CONFIDENCE = 0.30
        const val MAX_LYRIC_LINES = 5_000
        const val MAX_LYRIC_TOKENS = 50_000
        const val MAX_LINES_PER_SPAN = 2
        const val MAX_SPAN_TOKENS = 96
        private const val MIN_MAX_SPAN_TOKENS = 8
        private const val UNMATCHED_CUE_PENALTY = 0.20
        private const val NEGATIVE_INFINITY = -1.0e100
        private const val SCORE_EPSILON = 0.000_001
        private const val ACTION_SKIP_TOKEN: Byte = 1
        private const val ACTION_SKIP_CUE: Byte = 2
        private const val ACTION_MATCH: Byte = 3
        private val WORD_PATTERN = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    }
}

data class CanonicalLyricsRange(
    val startLineInclusive: Int,
    val endLineInclusive: Int,
)
