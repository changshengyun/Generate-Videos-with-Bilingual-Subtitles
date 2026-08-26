package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Production two-stage DeepSeek adapter: identify candidates, verify searched lyrics locally,
 * then generate bilingual cues using the complete song as context.
 */
class DeepSeekCaptionEnhancementProvider(
    private val byokManager: DeepSeekByokManager,
    private val searchTool: SongLyricsSearchTool = LrclibSongLyricsSearchTool(),
    private val verifier: SongLyricsCandidateVerifier = SongLyricsCandidateVerifier(),
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val onDiagnosticStage: (DeepSeekEnhancementStage) -> Unit = {},
) : CaptionEnhancementProvider, CaptionCueSuggestionService {
    override suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse {
        currentCoroutineContext().ensureActive()
        val identities = if (request.cues.size >= SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES) {
            onDiagnosticStage(DeepSeekEnhancementStage.CANDIDATE_REQUEST)
            val body = byokManager.withDecryptedKey { apiKey ->
                executeRequest(
                    apiKey = apiKey,
                    requestBody = DeepSeekCaptionEnhancementJson.songIdentificationRequestBody(request),
                )
            }
            onDiagnosticStage(DeepSeekEnhancementStage.CANDIDATE_PARSE)
            parseProviderJson { DeepSeekCaptionEnhancementJson.parseSongCandidates(body) }
        } else {
            emptyList()
        }
        currentCoroutineContext().ensureActive()

        onDiagnosticStage(DeepSeekEnhancementStage.LYRICS_SEARCH)
        val lookup = findVerifiedLyrics(request, identities)
        currentCoroutineContext().ensureActive()
        onDiagnosticStage(DeepSeekEnhancementStage.WHOLE_SONG_REQUEST)
        val finalBody = byokManager.withDecryptedKey { apiKey ->
            executeRequest(
                apiKey = apiKey,
                requestBody = DeepSeekCaptionEnhancementJson.contextualEnhancementRequestBody(
                    request = request,
                    verified = lookup.verified,
                    unconfirmedIdentity = lookup.unconfirmedIdentity,
                ),
            )
        }
        onDiagnosticStage(DeepSeekEnhancementStage.WHOLE_SONG_PARSE)
        return parseProviderJson { DeepSeekCaptionEnhancementJson.parseEnhancementResponse(finalBody) }.copy(
            processingVersion = PROCESSING_VERSION,
            songMatch = lookup.songMatch,
        )
    }

    override suspend fun suggest(request: CaptionCueSuggestionRequest): CaptionCueSuggestion {
        currentCoroutineContext().ensureActive()
        val body = byokManager.withDecryptedKey { apiKey ->
            executeRequest(
                apiKey = apiKey,
                requestBody = DeepSeekCaptionEnhancementJson.cueSuggestionRequestBody(request),
            )
        }
        return parseProviderJson {
            DeepSeekCaptionEnhancementJson.parseCueSuggestionResponse(body, request)
        }
    }

    private suspend fun findVerifiedLyrics(
        request: CaptionEnhancementRequest,
        identities: List<SongIdentityCandidate>,
    ): LyricsLookupOutcome {
        var best: VerifiedSongLyrics? = null
        var foundLyrics = false
        var searchUnavailable = false
        identities.forEachIndexed { index, identity ->
            currentCoroutineContext().ensureActive()
            if (index > 0) delay(LRCLIB_BATCH_DELAY_MS)
            val candidates = try {
                searchTool.search(identity)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SongLyricsSearchException) {
                searchUnavailable = true
                return@forEachIndexed
            } catch (_: Exception) {
                searchUnavailable = true
                return@forEachIndexed
            }
            if (candidates.isNotEmpty()) foundLyrics = true
            onDiagnosticStage(DeepSeekEnhancementStage.LYRICS_VERIFY)
            candidates.forEach { candidate ->
                val verified = verifier.verify(request.cues, candidate) ?: return@forEach
                if (best == null || verified.metrics.confidence > requireNotNull(best).metrics.confidence) {
                    best = verified
                }
            }
        }

        // Metadata search can miss the real song when the ASR-mangled title does not exist
        // (e.g. "Sadie of stars" instead of "City of Stars"). Retry with raw lyric text so the
        // correct track can still be found and verified; the DP verifier stays the gatekeeper.
        if (best == null && !foundLyrics && !searchUnavailable &&
            request.cues.size >= SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES
        ) {
            currentCoroutineContext().ensureActive()
            val candidates = try {
                searchTool.searchByLyricText(lyricTextFallbackQuery(request))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (candidates.isNotEmpty()) foundLyrics = true
            onDiagnosticStage(DeepSeekEnhancementStage.LYRICS_VERIFY)
            candidates.forEach { candidate ->
                val verified = verifier.verify(request.cues, candidate) ?: return@forEach
                if (best == null || verified.metrics.confidence > requireNotNull(best).metrics.confidence) {
                    best = verified
                }
            }
        }

        best?.let { verified ->
            onDiagnosticStage(DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED)
            return LyricsLookupOutcome(
                verified = verified,
                songMatch = SongMatch(
                    status = SongMatchStatus.CONFIRMED,
                    title = verified.candidate.title,
                    artist = verified.candidate.artist,
                    confidence = verified.metrics.confidence.toFloat(),
                    source = verified.candidate.sourceId,
                ),
            )
        }

        val firstIdentity = identities.firstOrNull()
        return if (firstIdentity != null && (foundLyrics || searchUnavailable)) {
            LyricsLookupOutcome(
                unconfirmedIdentity = firstIdentity,
                songMatch = SongMatch(
                    status = SongMatchStatus.UNCONFIRMED,
                    title = firstIdentity.title,
                    artist = firstIdentity.artist,
                    confidence = null,
                    source = if (searchUnavailable) "lyrics-search-unavailable" else "lyrics-candidate-unverified",
                ),
            )
        } else {
            LyricsLookupOutcome(songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND))
        }
    }

    private suspend fun executeRequest(
        apiKey: String,
        requestBody: String,
    ): String = withContext(Dispatchers.IO) {
        val connection = try {
            connectionFactory(URL(ENDPOINT))
        } catch (error: IOException) {
            throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { output -> output.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw httpFailure(status)
            decodeUtf8(readBounded(connection.inputStream, MAX_RESPONSE_BYTES))
        } catch (error: CancellationException) {
            throw error
        } catch (error: CaptionEnhancementProviderException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw providerFailure(CaptionEnhancementErrorKind.TIMEOUT, error)
        } catch (error: JsonParseException) {
            throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
        } catch (error: IOException) {
            throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray = input.use { source ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximum) throw JsonParseException("Provider response is too large")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: java.nio.charset.CharacterCodingException) {
        throw JsonParseException("Provider response is not valid UTF-8")
    }

    private fun httpFailure(status: Int): CaptionEnhancementProviderException {
        val kind = when {
            status == 401 || status == 403 -> CaptionEnhancementErrorKind.AUTHENTICATION
            status == 408 -> CaptionEnhancementErrorKind.TIMEOUT
            status == 429 || status >= 500 -> CaptionEnhancementErrorKind.RETRYABLE_SERVER
            else -> CaptionEnhancementErrorKind.INVALID_RESPONSE
        }
        return providerFailure(kind, null)
    }

    private fun providerFailure(
        kind: CaptionEnhancementErrorKind,
        cause: Throwable?,
    ): CaptionEnhancementProviderException =
        CaptionEnhancementProviderException(kind, "DeepSeek request failed.", cause)

    private inline fun <T> parseProviderJson(block: () -> T): T = try {
        block()
    } catch (error: JsonParseException) {
        throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
    } catch (error: IllegalArgumentException) {
        throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
    }

    private data class LyricsLookupOutcome(
        val verified: VerifiedSongLyrics? = null,
        val unconfirmedIdentity: SongIdentityCandidate? = null,
        val songMatch: SongMatch,
    )

    /** Collapse the raw ASR lines into one bounded query string for the lyric-text fallback. */
    internal fun lyricTextFallbackQuery(request: CaptionEnhancementRequest): String =
        request.cues.map { it.rawEnglish }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(FALLBACK_QUERY_MAX_CHARS)

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-v4-pro"
        const val PROCESSING_VERSION = "deepseek-v4-pro-lyrics-search-context.v4"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_SONG_CANDIDATES = 2
        const val LRCLIB_BATCH_DELAY_MS = 250L
        const val FALLBACK_QUERY_MAX_CHARS = 300

        val IDENTIFICATION_SYSTEM_PROMPT = """
输入是同一首英文歌曲的整批 Whisper 识别字幕，内容可能包含错词、漏词和错误断句。
1.必须综合整批字幕中的多条歌词识别对应歌曲，不能只凭单句判断，也不能声称候选已经确认。
2.Whisper 可能把歌词中的关键词或歌名本身识别错（例如把歌名唱词听成形近词）。识别时必须依据整批歌词的语义、意象、句式和用词推断真实歌曲；不得直接照抄 Whisper 原文中疑似歌名的字面拼写作为候选歌名。
3.单条字幕可能包含同一歌曲的两句歌词，这不是异常输入，综合判断时按两句理解。
4.按可能性从高到低返回最多 $MAX_SONG_CANDIDATES 个候选，每个候选必须包含非空的英文歌名和歌手。
5.如果整批证据不足或内容无法识别为歌词，返回空 candidates 数组，不得编造候选。
只返回 JSON，格式必须严格为：{"candidates":[{"title":"...","artist":"..."}]}。
""".trimIndent()

        val VERIFIED_LYRICS_SYSTEM_PROMPT = """
输入已经包含由外部歌词检索工具取得并经多条 Whisper 字幕验证的歌曲信息、完整英文歌词和可用的 canonical cue 对齐。
任务必须按以下顺序完成：
1.先通读整首英文歌词，依据完整歌词和 canonical cue 对齐完成整批英文纠错，确定每个 cue 的 corrected_english。没有对齐的内容只能依据完整歌词保守纠错，不得凭模型记忆补写歌词。
2.整批英文纠错完成后，再根据 corrected_english 和整首歌曲上下文生成对应的中文歌词。中文应忠实表达歌曲原意，同时采用自然的中文歌词表达；不要逐词直译，也不能把每条字幕孤立翻译。
3.保持意象、情绪、语气、代词、跨行语义和重复副歌译法一致；不得为了押韵改变原意，不得输出解释、注释或歌词以外的内容。相同 canonical 英文歌词必须返回完全相同的中文。
只能使用请求中实际提供并经过验证的歌词内容。只有请求明确提供了经过验证的网易云中英对照歌词及来源时，才能采用并声称为网易云版本；未提供时不得凭模型记忆编造或冒充网易云译文。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。
cues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
""".trimIndent()

        val UNCONFIRMED_SYSTEM_PROMPT = """
当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。
必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。
中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。
cues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
""".trimIndent()

        val CUE_SUGGESTION_SYSTEM_PROMPT = """
你是人工复核阶段的单条字幕建议助手。只依据目标 cue、兄弟 cue、前后 cue 和整批当前字幕，保守修复目标英文并生成自然、忠实、连贯的中文歌词。
不得搜索、声称或强制采用标准歌词；不得修改其他 cue；不得输出解释。
只返回 JSON：{"schema_version":"${CaptionCueSuggestionContract.SCHEMA_VERSION}","job_id":"<copy input>","cue":{"cue_id":"<target id>","english":"...","chinese":"..."}}。
""".trimIndent()
    }
}

/** Non-sensitive lifecycle markers for diagnostics; no request or response data is exposed. */
enum class DeepSeekEnhancementStage {
    CANDIDATE_REQUEST,
    CANDIDATE_PARSE,
    LYRICS_SEARCH,
    LYRICS_VERIFY,
    VERIFIED_LYRICS_SELECTED,
    WHOLE_SONG_REQUEST,
    WHOLE_SONG_PARSE,
}

internal object DeepSeekCaptionEnhancementJson {
    fun songIdentificationRequestBody(request: CaptionEnhancementRequest): String = completionRequest(
        systemPrompt = DeepSeekCaptionEnhancementProvider.IDENTIFICATION_SYSTEM_PROMPT,
        userPayload = requestPayload(request),
        maxTokens = 512,
    )

    fun contextualEnhancementRequestBody(
        request: CaptionEnhancementRequest,
        verified: VerifiedSongLyrics?,
        unconfirmedIdentity: SongIdentityCandidate?,
    ): String {
        val context = if (verified != null) {
            mapOf(
                "mode" to "verified_complete_lyrics",
                "song" to mapOf(
                    "title" to verified.candidate.title,
                    "artist" to verified.candidate.artist,
                    "source_id" to verified.candidate.sourceId,
                ),
                "complete_english_lyrics" to verified.candidate.completeEnglishLyrics,
                "cue_canonical_alignments" to verified.cueCanonicalEnglish.map { (id, english) ->
                    mapOf("id" to id, "canonical_english" to english)
                },
                "request" to requestPayload(request),
            )
        } else {
            mapOf(
                "mode" to "unconfirmed_full_batch",
                "unconfirmed_candidate" to unconfirmedIdentity?.let {
                    mapOf("title" to it.title, "artist" to it.artist)
                },
                "request" to requestPayload(request),
            )
        }
        return completionRequest(
            systemPrompt = if (verified != null) {
                DeepSeekCaptionEnhancementProvider.VERIFIED_LYRICS_SYSTEM_PROMPT
            } else {
                DeepSeekCaptionEnhancementProvider.UNCONFIRMED_SYSTEM_PROMPT
            },
            userPayload = context,
            maxTokens = (request.cues.size * 192).coerceIn(1_024, 16_384),
        )
    }

    /** Kept as a compatibility shim for focused tests and probe tooling. */
    fun requestBody(request: CaptionEnhancementRequest): String =
        contextualEnhancementRequestBody(request, verified = null, unconfirmedIdentity = null)

    fun parseSongCandidates(body: String): List<SongIdentityCandidate> {
        val root = parseAssistantJson(body)
        val candidates = root.requiredArray("candidates").values
        if (candidates.size > DeepSeekCaptionEnhancementProvider.MAX_SONG_CANDIDATES) {
            throw JsonParseException("Too many song candidates")
        }
        return candidates.map { value ->
            val item = value.asObject()
            val title = item.requiredString("title").trim()
            val artist = item.requiredString("artist").trim()
            if (title.isBlank() || artist.isBlank() ||
                title.length > LrclibSongLyricsSearchTool.MAX_METADATA_LENGTH ||
                artist.length > LrclibSongLyricsSearchTool.MAX_METADATA_LENGTH
            ) {
                throw JsonParseException("Invalid song candidate")
            }
            SongIdentityCandidate(title, artist)
        }.distinctBy { it.title.lowercase() to it.artist.lowercase() }
    }

    fun parseEnhancementResponse(body: String): CaptionEnhancementResponse {
        val root = parseAssistantJson(body)
        val cues = root.requiredArray("cues").values.map { value ->
            val item = value.asObject()
            CaptionEnhancementResponseCue(
                id = item.requiredString("id"),
                startMs = item.requiredLong("start_ms"),
                endMs = item.requiredLong("end_ms"),
                correctedEnglish = item.requiredString("corrected_english"),
                chinese = item.requiredString("chinese"),
            )
        }
        return CaptionEnhancementResponse(
            schemaVersion = root.requiredString("schema_version"),
            jobId = root.requiredString("job_id"),
            processingVersion = root.requiredString("processing_version"),
            cues = cues,
            songMatch = null,
        )
    }

    fun parseResponse(body: String): CaptionEnhancementResponse = parseEnhancementResponse(body)

    fun cueSuggestionRequestBody(request: CaptionCueSuggestionRequest): String = completionRequest(
        systemPrompt = DeepSeekCaptionEnhancementProvider.CUE_SUGGESTION_SYSTEM_PROMPT,
        userPayload = mapOf(
            "schema_version" to CaptionCueSuggestionContract.SCHEMA_VERSION,
            "job_id" to request.jobId,
            "target" to suggestionCuePayload(request.target),
            "sibling" to request.sibling?.let(::suggestionCuePayload),
            "previous" to request.previous?.let(::suggestionCuePayload),
            "next" to request.next?.let(::suggestionCuePayload),
            "batch" to request.batch.map(::suggestionCuePayload),
        ),
        maxTokens = 512,
    )

    fun parseCueSuggestionResponse(
        body: String,
        request: CaptionCueSuggestionRequest,
    ): CaptionCueSuggestion {
        val root = parseAssistantJson(body)
        if (root.requiredString("schema_version") != CaptionCueSuggestionContract.SCHEMA_VERSION ||
            root.requiredString("job_id") != request.jobId
        ) {
            throw JsonParseException("Invalid cue suggestion contract")
        }
        val cue = root.requiredObject("cue")
        val cueId = cue.requiredString("cue_id")
        val english = cue.requiredString("english").trim()
        val chinese = cue.requiredString("chinese").trim()
        if (cueId != request.target.id) throw JsonParseException("Cue suggestion target changed")
        requireIdentifier(cueId, "suggestion cue id")
        requireText(english, allowBlank = false, "suggested English")
        requireText(chinese, allowBlank = false, "suggested Chinese")
        return CaptionCueSuggestion(cueId, english, chinese)
    }

    private fun suggestionCuePayload(cue: CaptionCue): Map<String, Any> = mapOf(
        "cue_id" to cue.id,
        "english" to cue.english,
        "chinese" to cue.chinese,
    )

    private fun parseAssistantJson(body: String): JsonObject {
        val envelope = StrictJsonParser(body).parseObjectDocument()
        val choice = envelope.requiredArray("choices").firstOrThrow().asObject()
        val content = choice.requiredObject("message").requiredString("content")
        return StrictJsonParser(content).parseObjectDocument()
    }

    private fun completionRequest(
        systemPrompt: String,
        userPayload: Any,
        maxTokens: Int,
    ): String = encodeJson(
        mapOf(
            "model" to DeepSeekCaptionEnhancementProvider.MODEL,
            "temperature" to 0,
            "max_tokens" to maxTokens,
            "stream" to false,
            "thinking" to mapOf("type" to "disabled"),
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to encodeJson(userPayload)),
            ),
        ),
    )

    private fun requestPayload(request: CaptionEnhancementRequest): Map<String, Any> {
        val cues = request.cues.map { cue ->
            val cuePayload = mutableMapOf<String, Any>(
                "id" to cue.id,
                "start_ms" to cue.startMs,
                "end_ms" to cue.endMs,
                "raw_english" to cue.rawEnglish,
            )
            cue.confidence?.let { cuePayload["confidence"] = it }
            cuePayload
        }
        val payload = mutableMapOf<String, Any>(
            "schema_version" to request.schemaVersion,
            "job_id" to request.jobId,
            "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
            "cues" to cues,
        )
        request.mediaDurationMs?.let { payload["media_duration_ms"] = it }
        return payload
    }
}
