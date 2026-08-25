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
) : StagedCaptionEnhancementProvider, CaptionCueSuggestionService {
    override suspend fun enhance(
        request: CaptionEnhancementRequest,
        onStateChanged: (CaptionEnhancementState) -> Unit,
    ): CaptionEnhancementResponse {
        currentCoroutineContext().ensureActive()
        val identities = if (request.cues.size >= SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES) {
            onStateChanged(CaptionEnhancementState.SONG_IDENTIFYING)
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

        onStateChanged(CaptionEnhancementState.LYRICS_RETRIEVING)
        onDiagnosticStage(DeepSeekEnhancementStage.LYRICS_SEARCH)
        val lookup = findVerifiedLyrics(request, identities)
        currentCoroutineContext().ensureActive()
        onStateChanged(CaptionEnhancementState.FIRST_PASS_ENHANCING)
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
        val parsed = parseProviderJson { DeepSeekCaptionEnhancementJson.parseEnhancementResponse(finalBody) }
        val firstPass = applyCanonicalLineContract(parsed, lookup.verified).copy(
            processingVersion = FIRST_PASS_PROCESSING_VERSION,
            songMatch = lookup.songMatch,
            processingLevel = CaptionProcessingLevel.FIRST_PASS_REVIEW_REQUIRED,
        )
        validateFirstPassStructure(request, firstPass)
        onStateChanged(CaptionEnhancementState.AUTO_SPLITTING)
        val repairRequest = CaptionLocalRepairBatchPolicy.build(request, firstPass, lookup.verified)
            ?: return firstPass.copy(
                processingVersion = PROCESSING_VERSION,
                processingLevel = CaptionProcessingLevel.TWO_PASS_COMPLETE,
            )
        return try {
            currentCoroutineContext().ensureActive()
            onStateChanged(CaptionEnhancementState.LOCAL_REPAIRING)
            onDiagnosticStage(DeepSeekEnhancementStage.LOCAL_REPAIR_REQUEST)
            val repairBody = byokManager.withDecryptedKey { apiKey ->
                executeRequest(
                    apiKey = apiKey,
                    requestBody = DeepSeekCaptionEnhancementJson.localRepairRequestBody(
                        request = repairRequest,
                        verified = lookup.verified,
                    ),
                )
            }
            onDiagnosticStage(DeepSeekEnhancementStage.LOCAL_REPAIR_PARSE)
            val repairResponse = parseProviderJson {
                DeepSeekCaptionEnhancementJson.parseLocalRepairResponse(repairBody)
            }
            onStateChanged(CaptionEnhancementState.FINAL_VALIDATING)
            CaptionLocalRepairBatchPolicy.apply(repairRequest, repairResponse, firstPass)
        } catch (error: CancellationException) {
            throw error
        } catch (_: CaptionEnhancementException) {
            firstPass
        }
    }

    override suspend fun suggest(request: CaptionCueSuggestionRequest): CaptionCueSuggestion {
        currentCoroutineContext().ensureActive()
        val batchRequest = CaptionEnhancementRequest(
            jobId = request.jobId,
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = request.batch.map { cue ->
                CaptionEnhancementRequestCue(cue.id, cue.startMs, cue.endMs, cue.english)
            },
        )
        val confirmed = request.songMatch?.takeIf { it.status == SongMatchStatus.CONFIRMED }
        val verified = if (confirmed?.title != null && confirmed.artist != null) {
            findVerifiedLyrics(
                request = batchRequest,
                identities = listOf(SongIdentityCandidate(confirmed.title, confirmed.artist)),
            ).verified
        } else {
            null
        }
        val canonical = verified?.cueCanonicalLines?.get(request.target.id)?.singleOrNull()
        val body = byokManager.withDecryptedKey { apiKey ->
            executeRequest(
                apiKey = apiKey,
                requestBody = DeepSeekCaptionEnhancementJson.cueSuggestionRequestBody(
                    request = request,
                    verified = verified,
                    canonical = canonical,
                ),
            )
        }
        val parsed = parseProviderJson { DeepSeekCaptionEnhancementJson.parseLocalRepairResponse(body) }
        if (parsed.schemaVersion != CaptionLocalRepairContract.SCHEMA_VERSION ||
            parsed.jobId != request.jobId || parsed.cues.size != 1 ||
            parsed.cues.single().id != request.target.id
        ) {
            throw invalidLineContract()
        }
        val cue = parsed.cues.single()
        requireText(cue.correctedEnglish, allowBlank = false, "suggested English")
        requireText(cue.chinese, allowBlank = false, "suggested Chinese")
        if (canonical != null && normalizeCanonical(cue.correctedEnglish) != normalizeCanonical(canonical)) {
            throw invalidLineContract()
        }
        return CaptionCueSuggestion(
            cueId = request.target.id,
            english = canonical ?: cue.correctedEnglish.trim(),
            chinese = cue.chinese.trim(),
            canonicalVerified = canonical != null,
        )
    }

    private fun applyCanonicalLineContract(
        response: CaptionEnhancementResponse,
        verified: VerifiedSongLyrics?,
    ): CaptionEnhancementResponse {
        val canonicalById = verified?.cueCanonicalLines.orEmpty()
        val cues = response.cues.map { cue ->
            val canonicalLines = canonicalById[cue.sourceId]
            if (canonicalLines == null) {
                if (cue.lines.size != 1) throw invalidLineContract()
                cue
            } else {
                if (canonicalLines.size !in 1..2 || cue.lines.size != canonicalLines.size) {
                    throw invalidLineContract()
                }
                val matches = cue.lines.zip(canonicalLines).all { (line, canonical) ->
                    normalizeCanonical(line.correctedEnglish) == normalizeCanonical(canonical)
                }
                if (!matches) throw invalidLineContract()
                cue.copy(
                    lines = cue.lines.zip(canonicalLines).map { (line, canonical) ->
                        line.copy(correctedEnglish = canonical)
                    },
                )
            }
        }
        return response.copy(cues = cues)
    }

    private fun validateFirstPassStructure(
        request: CaptionEnhancementRequest,
        firstPass: CaptionEnhancementResponse,
    ) {
        val rawCaptions = request.cues.map { cue ->
            CaptionCue(
                id = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                english = cue.rawEnglish,
                chinese = "",
                confidence = 1f,
            )
        }
        CaptionEnhancementResponseValidator().validate(request, firstPass, rawCaptions)
    }

    private fun invalidLineContract(): CaptionEnhancementProviderException =
        CaptionEnhancementProviderException(
            kind = CaptionEnhancementErrorKind.INVALID_RESPONSE,
            safeDetail = "Caption enhancement response was invalid.",
        )

    private fun normalizeCanonical(value: String): String = value
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private suspend fun findVerifiedLyrics(
        request: CaptionEnhancementRequest,
        identities: List<SongIdentityCandidate>,
    ): LyricsLookupOutcome {
        if (identities.isEmpty()) {
            return LyricsLookupOutcome(songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND))
        }

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

        val firstIdentity = identities.first()
        return if (foundLyrics || searchUnavailable) {
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

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-v4-pro"
        const val PROCESSING_VERSION = "deepseek-v4-pro-lyrics-search-context.v5-two-pass"
        const val FIRST_PASS_PROCESSING_VERSION = "deepseek-v4-pro-lyrics-search-context.v5-first-pass"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_SONG_CANDIDATES = 2
        const val LRCLIB_BATCH_DELAY_MS = 250L

        val IDENTIFICATION_SYSTEM_PROMPT = """
输入是同一首英文歌曲的整批 Whisper 识别字幕，内容可能包含错词、漏词和错误断句。
1.必须综合整批字幕中的多条歌词识别对应歌曲，不能只凭单句判断，也不能声称候选已经确认。
2.按可能性从高到低返回最多 $MAX_SONG_CANDIDATES 个候选，每个候选必须包含非空的英文歌名和歌手。
3.如果整批证据不足或内容无法识别为歌词，返回空 candidates 数组，不得编造候选。
只返回 JSON，格式必须严格为：{"candidates":[{"title":"...","artist":"..."}]}。
""".trimIndent()

        val VERIFIED_LYRICS_SYSTEM_PROMPT = """
输入已经包含由外部歌词检索工具取得并经多条 Whisper 字幕验证的歌曲信息、完整英文歌词和可用的 canonical cue 对齐。
任务必须按以下顺序完成：
1.先通读整首英文歌词。每个 source cue 都带有 1～2 行 canonical_lines；必须逐行原样回填 corrected_english，不得合并、重排或改写 canonical 英文。没有 canonical_lines 的 source cue 只能返回一行并保守纠错，不得凭模型记忆补写歌词。
2.整批英文确定后，再为每一行生成一一对应的中文歌词。中文应忠实表达歌曲原意，同时采用自然的中文歌词表达；不要逐词直译，也不能把每条字幕孤立翻译。
3.保持意象、情绪、语气、代词、跨行语义和重复副歌译法一致；不得为了押韵改变原意，不得输出解释、注释或歌词以外的内容。相同 canonical 英文歌词必须返回完全相同的中文。
只能使用请求中实际提供并经过验证的歌词内容。只有请求明确提供了经过验证的网易云中英对照歌词及来源时，才能采用并声称为网易云版本；未提供时不得凭模型记忆编造或冒充网易云译文。
不得增加、删除或重排 source cue，也不得修改 source cue 时间。每个 source cue 必须返回与 canonical_lines 数量相同的 1～2 个有序 lines。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$FIRST_PASS_PROCESSING_VERSION","cues":[{"source_id":"<copy input cue id>","start_ms":0,"end_ms":1,"lines":[{"corrected_english":"canonical English line","chinese":"coherent Chinese lyric line"}]}]}.
每个 source cue 必须包含上面展示的全部四个字段，每个 line 必须包含 corrected_english 和 chinese。不要返回 song_match。
""".trimIndent()

        val UNCONFIRMED_SYSTEM_PROMPT = """
当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。
必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。
中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。
不得增加、删除、拆分、合并或重排 source cue，也不得修改时间。由于没有验证歌词，每个 source cue 只能返回一个 line。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$FIRST_PASS_PROCESSING_VERSION","cues":[{"source_id":"<copy input cue id>","start_ms":0,"end_ms":1,"lines":[{"corrected_english":"corrected English line","chinese":"coherent Chinese lyric line"}]}]}.
每个 source cue 必须包含上面展示的全部四个字段，每个 line 必须包含 corrected_english 和 chinese。不要返回 song_match。
""".trimIndent()

        val LOCAL_REPAIR_SYSTEM_PROMPT = """
你是英文歌曲字幕的局部修复助手。输入是第一次整批增强后由同一个父 cue 拆出的全部子 cue。
逐条检查拆分是否留下半句、重复词、漏词或中英文错配，同时结合父 cue 原始识别、兄弟 cue、前后歌词和已验证 canonical 行理解上下文。
如果提供 canonical_english，corrected_english 必须逐字原样返回该行，不得依靠模型记忆改写；中文要像自然的中文歌词，忠实、连贯，不做逐词机器翻译。
不得增加、删除、合并或重排 cue，不得修改 id。每个输入 cue 必须且只能返回一条结果。
只返回 JSON：{"schema_version":"${CaptionLocalRepairContract.SCHEMA_VERSION}","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<copy input cue id>","corrected_english":"...","chinese":"..."}]}。
""".trimIndent()

        val CUE_SUGGESTION_SYSTEM_PROMPT = """
你是英文歌曲字幕的人工复核助手。用户正在复核其中一个 cue，输入会提供当前 cue、可能的兄弟 cue、前后 cue、整批英文和可选的已验证 canonical 行。
修复当前 cue 的错词、漏词、多词和不完整句式，并生成自然、忠实、连贯的中文歌词。不要逐词机器翻译，不要输出解释。
如果 canonical_english 非空，英文必须逐字原样采用 canonical；否则只能依据输入做保守修复，不得凭模型记忆声称找到了标准歌词。
只能返回目标 cue，不能修改兄弟或前后 cue。只返回 JSON：{"schema_version":"${CaptionLocalRepairContract.SCHEMA_VERSION}","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<target id>","corrected_english":"...","chinese":"..."}]}。
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
    LOCAL_REPAIR_REQUEST,
    LOCAL_REPAIR_PARSE,
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
                "canonical_range" to mapOf(
                    "start_line_inclusive" to verified.canonicalRange.startLineInclusive,
                    "end_line_inclusive" to verified.canonicalRange.endLineInclusive,
                ),
                "cue_canonical_alignments" to verified.cueCanonicalLines.map { (id, lines) ->
                    mapOf("source_id" to id, "canonical_lines" to lines)
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
            maxTokens = (request.cues.size * 192).coerceIn(768, 16_384),
        )
    }

    fun localRepairRequestBody(
        request: CaptionLocalRepairRequest,
        verified: VerifiedSongLyrics?,
    ): String = completionRequest(
        systemPrompt = DeepSeekCaptionEnhancementProvider.LOCAL_REPAIR_SYSTEM_PROMPT,
        userPayload = mapOf(
            "schema_version" to CaptionLocalRepairContract.SCHEMA_VERSION,
            "job_id" to request.jobId,
            "song" to verified?.let {
                mapOf(
                    "title" to it.candidate.title,
                    "artist" to it.candidate.artist,
                    "source_id" to it.candidate.sourceId,
                )
            },
            "cues" to request.cues.map { cue ->
                mapOf(
                    "id" to cue.id,
                    "parent_source_id" to cue.parentSourceId,
                    "sibling_id" to cue.siblingId,
                    "parent_raw_english" to cue.parentRawEnglish,
                    "current_english" to cue.english,
                    "current_chinese" to cue.chinese,
                    "previous_english" to cue.previousEnglish,
                    "next_english" to cue.nextEnglish,
                    "canonical_english" to cue.canonicalEnglish,
                )
            },
        ),
        maxTokens = (request.cues.size * 160).coerceIn(512, 8_192),
    )

    fun cueSuggestionRequestBody(
        request: CaptionCueSuggestionRequest,
        verified: VerifiedSongLyrics?,
        canonical: String?,
    ): String = completionRequest(
        systemPrompt = DeepSeekCaptionEnhancementProvider.CUE_SUGGESTION_SYSTEM_PROMPT,
        userPayload = mapOf(
            "schema_version" to CaptionLocalRepairContract.SCHEMA_VERSION,
            "job_id" to request.jobId,
            "song" to verified?.let {
                mapOf("title" to it.candidate.title, "artist" to it.candidate.artist)
            },
            "target" to mapOf(
                "id" to request.target.id,
                "current_english" to request.target.english,
                "current_chinese" to request.target.chinese,
                "canonical_english" to canonical,
            ),
            "sibling" to request.sibling?.let { mapOf("id" to it.id, "english" to it.english, "chinese" to it.chinese) },
            "previous" to request.previous?.let { mapOf("id" to it.id, "english" to it.english) },
            "next" to request.next?.let { mapOf("id" to it.id, "english" to it.english) },
            "batch_english" to request.batch.map { mapOf("id" to it.id, "english" to it.english) },
        ),
        maxTokens = 512,
    )

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
                sourceId = item.requiredString("source_id"),
                startMs = item.requiredLong("start_ms"),
                endMs = item.requiredLong("end_ms"),
                lines = item.requiredArray("lines").values.map { lineValue ->
                    val line = lineValue.asObject()
                    CaptionEnhancementResponseLine(
                        correctedEnglish = line.requiredString("corrected_english"),
                        chinese = line.requiredString("chinese"),
                    )
                },
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

    fun parseLocalRepairResponse(body: String): CaptionLocalRepairResponse {
        val root = parseAssistantJson(body)
        return CaptionLocalRepairResponse(
            schemaVersion = root.requiredString("schema_version"),
            jobId = root.requiredString("job_id"),
            processingVersion = root.requiredString("processing_version"),
            cues = root.requiredArray("cues").values.map { value ->
                val item = value.asObject()
                CaptionLocalRepairResponseCue(
                    id = item.requiredString("id"),
                    correctedEnglish = item.requiredString("corrected_english"),
                    chinese = item.requiredString("chinese"),
                )
            },
        )
    }

    fun parseResponse(body: String): CaptionEnhancementResponse = parseEnhancementResponse(body)

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

    private fun requestPayload(request: CaptionEnhancementRequest): Map<String, Any> = mapOf(
        "schema_version" to request.schemaVersion,
        "job_id" to request.jobId,
        "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
        "cues" to request.cues.map { cue ->
            mapOf(
                "id" to cue.id,
                "start_ms" to cue.startMs,
                "end_ms" to cue.endMs,
                "raw_english" to cue.rawEnglish,
            )
        },
    )
}
