package com.example.lyriccaptioner.processing

import org.json.JSONArray
import org.json.JSONObject

data class SentencePieceToken(
    val text: String,
    val score: Float,
    val id: Long,
)

class SentencePieceTokenizer private constructor(
    private val tokens: List<SentencePieceToken>,
    private val tokenByText: Map<String, SentencePieceToken>,
    private val unkId: Long,
    private val eosId: Long,
    private val languageId: Long,
) {
    fun encode(text: String): LongArray {
        if (text.isBlank()) return longArrayOf(languageId, eosId)
        val normalized = "\u2581" + text.trim().split(Regex("\\s+")).joinToString("\u2581")
        val codePointStarts: List<Int> = normalized.indices.filter { index ->
            index == 0 || Character.isLowSurrogate(normalized[index]).not()
        } + normalized.length
        val bestScore = FloatArray(codePointStarts.size) { Float.NEGATIVE_INFINITY }
        val bestPiece = arrayOfNulls<SentencePieceToken>(codePointStarts.size)
        val bestPrevious = IntArray(codePointStarts.size) { -1 }
        bestScore[0] = 0f
        for (startIndex in 0 until codePointStarts.lastIndex) {
            if (!bestScore[startIndex].isFinite()) continue
            val start = codePointStarts[startIndex]
            val firstChar = normalized[start]
            val candidates = tokensByFirstChar[firstChar].orEmpty()
            var matched = false
            candidates.forEach { piece ->
                val end = start + piece.text.length
                if (end <= normalized.length && normalized.regionMatches(start, piece.text, 0, piece.text.length)) {
                    val endIndex = codePointStarts.indexOf(end)
                    if (endIndex >= 0) {
                        matched = true
                        val score = bestScore[startIndex] + piece.score
                        if (score > bestScore[endIndex]) {
                            bestScore[endIndex] = score
                            bestPiece[endIndex] = piece
                            bestPrevious[endIndex] = startIndex
                        }
                    }
                }
            }
            if (!matched) {
                val end = Character.offsetByCodePoints(normalized, start, 1)
                val endIndex = codePointStarts.indexOf(end)
                val unknown = tokenByText["<unk>"] ?: SentencePieceToken("<unk>", -100f, unkId)
                val score = bestScore[startIndex] - 100f
                if (endIndex >= 0 && score > bestScore[endIndex]) {
                    bestScore[endIndex] = score
                    bestPiece[endIndex] = unknown
                    bestPrevious[endIndex] = startIndex
                }
            }
        }
        val result = ArrayList<Long>()
        var cursor = codePointStarts.lastIndex
        while (cursor > 0) {
            val piece = bestPiece[cursor] ?: error("SentencePiece tokenizer could not cover input.")
            result += piece.id
            cursor = bestPrevious[cursor]
        }
        result.reverse()
        return (listOf(languageId) + result + eosId).toLongArray()
    }

    fun decode(ids: LongArray): String {
        return ids.asSequence()
            .filter { it != eosId && it != 65000L && it != languageId }
            .mapNotNull { id -> tokens.getOrNull(id.toInt())?.text }
            .filter { it != "<unk>" }
            .joinToString("")
            .replace('\u2581', ' ')
            .trim()
    }

    private val tokensByFirstChar: Map<Char, List<SentencePieceToken>> = tokens.groupBy { it.text.firstOrNull() ?: '\u0000' }

    companion object {
        fun fromJson(json: String): SentencePieceTokenizer {
            val root = JSONObject(json)
            val model = root.getJSONObject("model")
            val vocab = model.getJSONArray("vocab")
            val tokenList = buildList(vocab.length()) {
                for (id in 0 until vocab.length()) {
                    val item: JSONArray = vocab.getJSONArray(id)
                    add(SentencePieceToken(item.getString(0), item.getDouble(1).toFloat(), id.toLong()))
                }
            }
            val byText = tokenList.associateBy { it.text }
            return SentencePieceTokenizer(
                tokens = tokenList,
                tokenByText = byText,
                unkId = model.optLong("unk_id", 1L),
                eosId = byText["</s>"]?.id ?: 0L,
                languageId = byText[">>cmn_Hans<<"]?.id
                    ?: error("OPUS-MT language token >>cmn_Hans<< is missing."),
            )
        }

        fun forTest(tokens: List<Pair<String, Float>>): SentencePieceTokenizer {
            val tokenList = tokens.mapIndexed { id, (text, score) -> SentencePieceToken(text, score, id.toLong()) }
            val byText = tokenList.associateBy { it.text }
            return SentencePieceTokenizer(tokenList, byText, 1L, byText["</s>"]?.id ?: 0L, byText[">>cmn_Hans<<"]?.id ?: 5L)
        }
    }
}
