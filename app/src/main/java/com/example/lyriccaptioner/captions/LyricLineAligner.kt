package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import kotlin.math.max

class LyricLineAligner(
    private val gapPenalty: Double = 0.25,
    private val minimumSimilarity: Double = 0.18,
) {
    fun align(captions: List<CaptionCue>, lyricLines: List<String>): Map<String, LyricMatch> {
        if (captions.isEmpty() || lyricLines.isEmpty()) return emptyMap()

        val scores = Array(captions.size + 1) { DoubleArray(lyricLines.size + 1) }
        for (captionIndex in 1..captions.size) scores[captionIndex][0] = -captionIndex * gapPenalty
        for (lyricIndex in 1..lyricLines.size) scores[0][lyricIndex] = -lyricIndex * gapPenalty

        for (captionIndex in 1..captions.size) {
            for (lyricIndex in 1..lyricLines.size) {
                val similarity = similarity(captions[captionIndex - 1].english, lyricLines[lyricIndex - 1])
                scores[captionIndex][lyricIndex] = max(
                    scores[captionIndex - 1][lyricIndex - 1] + similarity,
                    max(
                        scores[captionIndex - 1][lyricIndex] - gapPenalty,
                        scores[captionIndex][lyricIndex - 1] - gapPenalty,
                    ),
                )
            }
        }

        val matches = linkedMapOf<String, LyricMatch>()
        var captionIndex = captions.size
        var lyricIndex = lyricLines.size
        while (captionIndex > 0 && lyricIndex > 0) {
            val similarity = similarity(captions[captionIndex - 1].english, lyricLines[lyricIndex - 1])
            val diagonal = scores[captionIndex - 1][lyricIndex - 1] + similarity
            if (nearlyEqual(scores[captionIndex][lyricIndex], diagonal)) {
                if (similarity >= minimumSimilarity) {
                    matches[captions[captionIndex - 1].id] = LyricMatch(
                        lyric = lyricLines[lyricIndex - 1],
                        similarity = similarity,
                    )
                }
                captionIndex--
                lyricIndex--
            } else if (scores[captionIndex - 1][lyricIndex] >= scores[captionIndex][lyricIndex - 1]) {
                captionIndex--
            } else {
                lyricIndex--
            }
        }
        return matches
    }

    private fun similarity(left: String, right: String): Double {
        val normalizedLeft = normalize(left)
        val normalizedRight = normalize(right)
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) return 0.0
        if (normalizedLeft == normalizedRight) return 1.0

        val distances = IntArray(normalizedRight.length + 1) { it }
        for (leftIndex in normalizedLeft.indices) {
            var previousDiagonal = distances[0]
            distances[0] = leftIndex + 1
            for (rightIndex in normalizedRight.indices) {
                val previousAbove = distances[rightIndex + 1]
                val replacementCost = if (normalizedLeft[leftIndex] == normalizedRight[rightIndex]) 0 else 1
                distances[rightIndex + 1] = minOf(
                    distances[rightIndex + 1] + 1,
                    distances[rightIndex] + 1,
                    previousDiagonal + replacementCost,
                )
                previousDiagonal = previousAbove
            }
        }
        return 1.0 - distances.last().toDouble() / max(normalizedLeft.length, normalizedRight.length)
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun nearlyEqual(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) < 0.000_001
}

data class LyricMatch(
    val lyric: String,
    val similarity: Double,
)
