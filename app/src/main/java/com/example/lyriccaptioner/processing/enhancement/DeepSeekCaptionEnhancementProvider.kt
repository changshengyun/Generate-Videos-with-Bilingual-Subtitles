package com.example.lyriccaptioner.processing.enhancement

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
) : CaptionEnhancementProvider {
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
        const val PROCESSING_VERSION = "deepseek-v4-pro-lyrics-search-context.v2"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val MAX_SONG_CANDIDATES = 2
        const val LRCLIB_BATCH_DELAY_MS = 250L

        val IDENTIFICATION_SYSTEM_PROMPT = """
1.我给你一部分英文歌词，你根据歌词找对应的英文歌曲。
必须综合整批 Whisper 英文字幕中的多条歌词判断，不能只依赖单句，也不能声称候选歌曲已经确认。
按可能性从高到低返回最多 $MAX_SONG_CANDIDATES 个候选，每个候选必须包含非空的英文歌名和歌手。
如果信息不足或内容无法识别为歌词，返回空 candidates 数组。
只返回 JSON，格式必须严格为：{"candidates":[{"title":"...","artist":"..."}]}。
""".trimIndent()

        val VERIFIED_LYRICS_SYSTEM_PROMPT = """
2.根据已经确认的歌曲和提供的完整英文歌词，对每条英文歌词进行纠错。
3.对应英文歌词逐句给出网易云音乐版本的中文翻译，采用该歌曲准确的网易云译法。如果无法可靠确定现成译文，不得伪造来源，应根据整首歌词上下文给出自然、准确的中文歌词译文（符合歌曲原意而不是直接翻译）。
先通读整首歌曲再写中文，保持意象、代词、跨行语义、语气和重复副歌译法一致，不能把每条字幕孤立直译。
存在 canonical cue 对齐时必须使用；没有对齐的英文只能依据完整歌词保守纠错。相同 canonical 英文歌词必须返回完全相同的中文。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
""".trimIndent()

        val UNCONFIRMED_SYSTEM_PROMPT = """
根据整批 Whisper 英文歌词进行保守纠错，并逐句给出中文歌词翻译。
当前没有经过验证的搜索歌词，因此不得声称歌曲已经确认，不得编造 canonical 歌词，也不得把中文声称为网易云音乐版本。
先通读整批歌词再写中文，保持意象、代词、跨行语义、语气和重复内容一致，不能把每条字幕孤立直译。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"$PROCESSING_VERSION","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
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
            maxTokens = (request.cues.size * 192).coerceIn(768, 16_384),
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
