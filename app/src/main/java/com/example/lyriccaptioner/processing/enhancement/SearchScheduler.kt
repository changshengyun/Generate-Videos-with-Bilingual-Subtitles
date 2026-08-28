package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Core scheduler implementing three-route parallel search, three-interval branching,
 * and failure-feedback re-search loop (max 5 rounds).
 *
 * Flow 1+2 merged: AI with web_search finds song and lyrics, then local DP verifier
 * acts as the sole confirmation gate. Results are classified into three intervals:
 * - CONFIRMED (≥ threshold): song confirmed, proceed with verified lyrics
 * - MIDDLE_ZONE (50% ~ threshold): item-by-item repair, no re-search
 * - RESEARCH (< 50%): re-search with failure feedback, up to MAX_RESEARCH_ROUNDS
 */
class SearchScheduler(
    private val responsesClient: ResponsesApiClient,
    private val verifier: SongLyricsCandidateVerifier = SongLyricsCandidateVerifier(),
    private val byokManager: DeepSeekByokManager,
) {
    suspend fun schedule(request: CaptionEnhancementRequest): SearchResult {
        val excludedSongs = mutableListOf<ExcludedSong>()
        val diagnostics = mutableListOf<SearchRoundDiagnostic>()
        var round = 0

        while (round < MAX_RESEARCH_ROUNDS + 1) {
            currentCoroutineContext().ensureActive()
            round++

            val searchResponse = if (round == 1) {
                executeInitialSearch(request)
            } else {
                executeReSearch(request, excludedSongs)
            }

            val candidate = parseSearchResponse(searchResponse.textOutput)
            if (candidate == null) {
                diagnostics.add(SearchRoundDiagnostic(
                    round = round,
                    parsedSongTitle = null,
                    parsedArtist = null,
                    verifierConfidence = null,
                    rawMatchRate = null,
                    intervalClassification = "PARSE_FAILED",
                    searchActionCount = searchResponse.searchActions.size,
                ))
                if (round > MAX_RESEARCH_ROUNDS) {
                    return buildFallbackResult(diagnostics)
                }
                continue
            }

            val lyricsCandidate = SongLyricsCandidate(
                title = candidate.title,
                artist = candidate.artist,
                completeEnglishLyrics = candidate.fullLyrics,
                sourceId = candidate.sourceUrl ?: "web-search",
            )

            val verified = verifier.verify(request.cues, lyricsCandidate)

            if (verified != null) {
                diagnostics.add(SearchRoundDiagnostic(
                    round = round,
                    parsedSongTitle = candidate.title,
                    parsedArtist = candidate.artist,
                    verifierConfidence = verified.metrics.confidence,
                    rawMatchRate = verified.metrics.coverage,
                    intervalClassification = "CONFIRMED",
                    searchActionCount = searchResponse.searchActions.size,
                ))
                return SearchResult(
                    songMatch = SongMatch(
                        status = SongMatchStatus.CONFIRMED,
                        title = candidate.title,
                        artist = candidate.artist,
                        confidence = verified.metrics.confidence.toFloat(),
                        source = lyricsCandidate.sourceId,
                    ),
                    verifiedLyrics = verified,
                    unconfirmedIdentity = null,
                    canonicalAlignments = null,
                    diagnostics = diagnostics,
                )
            }

            val matchRate = calculateMatchRate(request.cues, lyricsCandidate)

            when {
                matchRate >= MIDDLE_ZONE_THRESHOLD -> {
                    diagnostics.add(SearchRoundDiagnostic(
                        round = round,
                        parsedSongTitle = candidate.title,
                        parsedArtist = candidate.artist,
                        verifierConfidence = null,
                        rawMatchRate = matchRate,
                        intervalClassification = "MIDDLE_ZONE",
                        searchActionCount = searchResponse.searchActions.size,
                    ))
                    val repairedCues = performItemByItemRepair(request.cues, lyricsCandidate)
                    val canonicalAlignments = repairedCues.filter { (id, repaired) ->
                        request.cues.firstOrNull { it.id == id }?.rawEnglish != repaired
                    }
                    return SearchResult(
                        songMatch = SongMatch(
                            status = SongMatchStatus.UNCONFIRMED,
                            title = candidate.title,
                            artist = candidate.artist,
                            confidence = null,
                            source = "middle-zone-repair",
                        ),
                        verifiedLyrics = null,
                        unconfirmedIdentity = SongIdentityCandidate(candidate.title, candidate.artist),
                        canonicalAlignments = canonicalAlignments.ifEmpty { null },
                        diagnostics = diagnostics,
                    )
                }
                else -> {
                    diagnostics.add(SearchRoundDiagnostic(
                        round = round,
                        parsedSongTitle = candidate.title,
                        parsedArtist = candidate.artist,
                        verifierConfidence = null,
                        rawMatchRate = matchRate,
                        intervalClassification = "RESEARCH",
                        searchActionCount = searchResponse.searchActions.size,
                    ))
                    excludedSongs.add(ExcludedSong(candidate.title, candidate.artist, matchRate))
                    if (round > MAX_RESEARCH_ROUNDS) {
                        return buildFallbackResult(diagnostics)
                    }
                }
            }
        }

        return buildFallbackResult(diagnostics)
    }

    private suspend fun executeInitialSearch(request: CaptionEnhancementRequest): ResponsesApiResult {
        val routeA = longestSingleCue(request.cues)
        val routeB = longestThreeCues(request.cues)
        val routeC = allCuesConcatenated(request.cues)

        val prompt = IDENTIFICATION_AND_SEARCH_PROMPT + """

Subtitle batch:
${request.cues.joinToString("\n") { "  ${it.rawEnglish}" }}

Search queries (try all three routes):
  Route A (longest single): $routeA
  Route B (longest three): $routeB
  Route C (all concatenated): $routeC

Remember: search first, then confirm. Lyrics must be quoted verbatim from search results.
""".trimIndent()

        return byokManager.withDecryptedKey { apiKey ->
            responsesClient.callResponses(apiKey = apiKey, input = prompt)
        }
    }

    private suspend fun executeReSearch(
        request: CaptionEnhancementRequest,
        excludedSongs: List<ExcludedSong>,
    ): ResponsesApiResult {
        val feedback = buildFailureFeedback(request, excludedSongs)
        return byokManager.withDecryptedKey { apiKey ->
            responsesClient.callResponses(apiKey = apiKey, input = feedback)
        }
    }

    private fun parseSearchResponse(textOutput: String): SearchCandidate? {
        val jsonText = extractJsonBlock(textOutput) ?: return null
        return try {
            val root = StrictJsonParser(jsonText).parseObjectDocument()
            SearchCandidate(
                title = root.requiredString("song_title").trim(),
                artist = root.requiredString("artist").trim(),
                fullLyrics = root.requiredString("full_lyrics").trim(),
                sourceUrl = (root.values["source_url"] as? JsonValue.StringValue)?.value?.trim(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonBlock(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val ch = text[i]
            when {
                escape -> escape = false
                ch == '\\' && inString -> escape = true
                ch == '"' && !escape -> inString = !inString
                inString -> {}
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    // ---- DP alignment for repair / match-rate (independent of verifier thresholds) ----

    private fun calculateMatchRate(
        cues: List<CaptionEnhancementRequestCue>,
        candidate: SongLyricsCandidate,
    ): Double {
        val alignment = alignCuesToLyrics(cues, candidate.completeEnglishLyrics)
        val matched = alignment.count { it.value.similarity >= REPAIR_SIMILARITY_THRESHOLD }
        return if (cues.isEmpty()) 0.0 else matched.toDouble() / cues.size
    }

    private fun performItemByItemRepair(
        cues: List<CaptionEnhancementRequestCue>,
        candidate: SongLyricsCandidate,
    ): Map<String, String> {
        val alignment = alignCuesToLyrics(cues, candidate.completeEnglishLyrics)
        return cues.associate { cue ->
            val match = alignment[cue.id]
            val repaired = if (match != null && match.similarity >= REPAIR_SIMILARITY_THRESHOLD) {
                match.canonicalEnglish
            } else {
                cue.rawEnglish
            }
            cue.id to repaired
        }
    }

    private fun alignCuesToLyrics(
        cues: List<CaptionEnhancementRequestCue>,
        lyrics: String,
    ): Map<String, RepairSpanMatch> {
        val lyricLines = lyrics.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lyricLines.isEmpty() || cues.isEmpty()) return emptyMap()

        val lyricTokens = mutableListOf<RepairToken>()
        lyricLines.forEachIndexed { lineIdx, line ->
            normalizeRepair(line).split(' ').filter { it.isNotBlank() }.forEach { token ->
                lyricTokens.add(RepairToken(token, lineIdx))
            }
        }
        if (lyricTokens.isEmpty()) return emptyMap()

        val cueCount = cues.size
        val tokenCount = lyricTokens.size
        val scores = Array(cueCount + 1) { DoubleArray(tokenCount + 1) { -1.0e100 } }
        val actions = Array(cueCount + 1) { ByteArray(tokenCount + 1) }
        val spanLengths = Array(cueCount + 1) { ByteArray(tokenCount + 1) }
        for (t in 0..tokenCount) scores[0][t] = 0.0

        for (c in 1..cueCount) {
            scores[c][0] = scores[c - 1][0] - 0.20
            actions[c][0] = ACTION_SKIP_CUE
            val cueText = cues[c - 1].rawEnglish
            val cueTokenCount = normalizeRepair(cueText).split(' ').count { it.isNotBlank() }
            val maxSpan = (cueTokenCount * 2 + 4).coerceIn(8, 96)

            for (t in 1..tokenCount) {
                var best = scores[c][t - 1]
                var action = ACTION_SKIP_TOKEN
                var bestLen = 0

                val skipCue = scores[c - 1][t] - 0.20
                if (skipCue > best + 0.000_001) {
                    best = skipCue
                    action = ACTION_SKIP_CUE
                }

                val upper = minOf(maxSpan, t)
                for (len in 1..upper) {
                    val start = t - len
                    if (lyricTokens[t - 1].lineIndex - lyricTokens[start].lineIndex > 1) continue
                    val canonical = lyricTokens.subList(start, t).joinToString(" ") { it.value }
                    val sim = similarityRepair(cueText, canonical)
                    if (sim < 0.62) continue
                    val matched = scores[c - 1][start] + sim
                    if (matched > best + 0.000_001) {
                        best = matched
                        action = ACTION_MATCH
                        bestLen = len
                    }
                }
                scores[c][t] = best
                actions[c][t] = action
                spanLengths[c][t] = bestLen.toByte()
            }
        }

        val result = mutableMapOf<String, RepairSpanMatch>()
        var c = cueCount
        var t = tokenCount
        while (c > 0) {
            when (actions[c][t]) {
                ACTION_SKIP_TOKEN -> t--
                ACTION_SKIP_CUE -> c--
                ACTION_MATCH -> {
                    val len = spanLengths[c][t].toInt() and 0xff
                    val start = t - len
                    val canonical = lyricTokens.subList(start, t).joinToString(" ") { it.value }
                    result[cues[c - 1].id] = RepairSpanMatch(canonical, similarityRepair(cues[c - 1].rawEnglish, canonical))
                    c--
                    t = start
                }
                else -> c--
            }
        }
        return result
    }

    private fun similarityRepair(left: String, right: String): Double {
        val nl = normalizeRepair(left)
        val nr = normalizeRepair(right)
        if (nl.isEmpty() || nr.isEmpty()) return 0.0
        if (nl == nr) return 1.0
        val charSim = editSimilarityRepair(nl, nr)
        val lt = nl.split(' ').filter { it.isNotBlank() }.toSet()
        val rt = nr.split(' ').filter { it.isNotBlank() }.toSet()
        val inter = lt.intersect(rt).size
        val dice = if (lt.isEmpty() || rt.isEmpty()) 0.0 else 2.0 * inter / (lt.size + rt.size)
        return maxOf(charSim, 0.65 * charSim + 0.35 * dice)
    }

    private fun editSimilarityRepair(left: String, right: String): Double {
        val d = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            var prev = d[0]
            d[0] = i + 1
            for (j in right.indices) {
                val tmp = d[j + 1]
                val cost = if (left[i] == right[j]) 0 else 1
                d[j + 1] = minOf(d[j + 1] + 1, d[j] + 1, prev + cost)
                prev = tmp
            }
        }
        return 1.0 - d.last().toDouble() / maxOf(left.length, right.length)
    }

    private fun normalizeRepair(value: String): String =
        value.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim()

    private fun buildFallbackResult(
        diagnostics: List<SearchRoundDiagnostic> = emptyList(),
    ): SearchResult = SearchResult(
        songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND),
        verifiedLyrics = null,
        unconfirmedIdentity = null,
        canonicalAlignments = null,
        diagnostics = diagnostics,
    )

    private fun longestSingleCue(cues: List<CaptionEnhancementRequestCue>): String =
        cues.maxByOrNull { it.rawEnglish.length }?.rawEnglish ?: ""

    private fun longestThreeCues(cues: List<CaptionEnhancementRequestCue>): String =
        cues.sortedByDescending { it.rawEnglish.length }.take(3)
            .joinToString(" ") { it.rawEnglish }

    private fun allCuesConcatenated(cues: List<CaptionEnhancementRequestCue>): String =
        cues.joinToString(" ") { it.rawEnglish }

    private fun buildFailureFeedback(
        request: CaptionEnhancementRequest,
        excludedSongs: List<ExcludedSong>,
    ): String {
        val excludedList = excludedSongs.joinToString("\n") { "  - ${it.title} by ${it.artist} (match rate: ${"%.2f".format(it.matchRate)})" }
        return FAILURE_FEEDBACK_PROMPT + """

Excluded songs (do NOT search these again):
$excludedList

Subtitle batch:
${request.cues.joinToString("\n") { "  ${it.rawEnglish}" }}

Try a different direction. Use high-confidence cues that didn't match as search keywords.
""".trimIndent()
    }

    companion object {
        const val MIDDLE_ZONE_THRESHOLD = 0.50
        const val MAX_RESEARCH_ROUNDS = 5

        val IDENTIFICATION_AND_SEARCH_PROMPT = """
你是一个歌词搜索助手。你的任务是根据提供的英文字幕找到对应的歌曲，并获取完整歌词。

规则：
1. 不许先猜歌名。先用字幕里的歌词句子直接搜索。
2. 三路搜索策略：
   - 路线A：用最长的一句字幕当搜索词
   - 路线B：用最长的三句字幕拼起来搜索
   - 路线C：把所有字幕拼起来搜索
3. 交叉验证：搜到候选歌曲后，用剩余的字幕核对，对不上就换句子再搜。
4. 歌词必须逐字抄自搜索结果，不许凭记忆脑补。
5. 如果找不到，如实回答无法确认，禁止编造。

输出格式（严格JSON）：
{
  "song_title": "歌名",
  "artist": "歌手",
  "full_lyrics": "完整英文歌词（逐字抄自搜索结果）",
  "source_url": "来源网址"
}
""".trimIndent()

        val FAILURE_FEEDBACK_PROMPT = """
你是一个歌词搜索助手（第二轮）。上一轮搜索失败了，现在需要换方向。

规则：
1. 绝对不许搜索排除名单里的歌曲及其版本。
2. 优先用"置信度高但没对上"的字幕句子当搜索词。
3. 如果还是找不到，如实回答无法确认，禁止编造。

输出格式（严格JSON）：
{
  "song_title": "歌名",
  "artist": "歌手",
  "full_lyrics": "完整英文歌词（逐字抄自搜索结果）",
  "source_url": "来源网址"
}
""".trimIndent()
    }
}

/** Result from the search scheduler. */
data class SearchResult(
    val songMatch: SongMatch,
    val verifiedLyrics: VerifiedSongLyrics?,
    val unconfirmedIdentity: SongIdentityCandidate?,
    val canonicalAlignments: Map<String, String>?,
    val diagnostics: List<SearchRoundDiagnostic> = emptyList(),
)

data class SearchCandidate(
    val title: String,
    val artist: String,
    val fullLyrics: String,
    val sourceUrl: String?,
)

data class ExcludedSong(
    val title: String,
    val artist: String,
    val matchRate: Double,
)

data class SearchRoundDiagnostic(
    val round: Int,
    val parsedSongTitle: String?,
    val parsedArtist: String?,
    val verifierConfidence: Double?,
    val rawMatchRate: Double?,
    val intervalClassification: String,
    val searchActionCount: Int,
)

private data class RepairToken(val value: String, val lineIndex: Int)

data class RepairSpanMatch(val canonicalEnglish: String, val similarity: Double)

private const val ACTION_SKIP_TOKEN: Byte = 1
private const val ACTION_SKIP_CUE: Byte = 2
private const val ACTION_MATCH: Byte = 3
private const val REPAIR_SIMILARITY_THRESHOLD = 0.62
