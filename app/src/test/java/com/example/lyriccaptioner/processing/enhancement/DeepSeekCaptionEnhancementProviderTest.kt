package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekCaptionEnhancementProviderTest {
    @Test
    fun identificationRequestUsesCurrentAccuracyModelAndOnlyWhisperBatch() {
        val body = DeepSeekCaptionEnhancementJson.songIdentificationRequestBody(request())

        assertTrue(body.contains("\"model\":\"deepseek-v4-pro\""))
        assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertTrue(body.contains("raw_english"))
        assertTrue(body.contains("Hello from the quiet street"))
        assertTrue(body.contains("必须综合整批字幕中的多条歌词推断对应歌曲"))
        assertTrue(body.contains("最多返回 2 个候选"))
        assertTrue(body.contains("禁止返回空 candidates 数组"))
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertFalse(body.contains("complete_english_lyrics"))
        assertFalse(body.contains("content://"))
    }

    @Test
    fun verifiedContextRequestIncludesWholeLyricsAndForbidsIsolatedCueTranslation() {
        val request = request()
        val completeLyrics = lyrics()
        val verified = requireNotNull(
            SongLyricsCandidateVerifier().verify(
                request.cues,
                SongLyricsCandidate("lrclib:42", "Quiet Street", "Example Artist", completeLyrics),
            ),
        )

        val body = DeepSeekCaptionEnhancementJson.contextualEnhancementRequestBody(
            request = request,
            verified = verified,
            unconfirmedIdentity = null,
        )

        assertTrue(body.contains("verified_complete_lyrics"))
        assertTrue(body.contains("complete_english_lyrics"))
        assertTrue(body.contains("Hello from the quiet street"))
        assertTrue(body.contains("外部歌词检索工具取得并经多条 Whisper 字幕验证"))
        assertTrue(body.contains("先通读整首英文歌词"))
        assertTrue(body.contains("整批英文纠错完成后，再根据 corrected_english"))
        assertTrue(body.contains("不要逐词直译"))
        assertTrue(body.contains("不得为了押韵改变原意"))
        assertTrue(body.contains("未提供时不得凭模型记忆编造或冒充网易云译文"))
        assertTrue(body.contains("相同 canonical 英文歌词必须返回完全相同的中文"))
        assertTrue(body.contains("corrected_english"))
        assertTrue(body.contains("coherent Chinese lyric line"))
        assertTrue(body.contains("单条 raw_english 可能包含同一歌曲的两句歌词"))
        assertTrue(body.contains("cues 中的 confidence 是该条 Whisper 识别的置信度"))
        assertTrue(body.contains("\"max_tokens\":1024"))
    }

    @Test
    fun songCandidateContractAcceptsAtMostTwoAndRejectsThirdCandidate() {
        val twoCandidates = envelope(
            """{"candidates":[{"title":"First Song","artist":"First Artist"},{"title":"Second Song","artist":"Second Artist"}]}""",
        )
        val threeCandidates = envelope(
            """{"candidates":[{"title":"First Song","artist":"First Artist"},{"title":"Second Song","artist":"Second Artist"},{"title":"Third Song","artist":"Third Artist"}]}""",
        )

        assertEquals(2, DeepSeekCaptionEnhancementJson.parseSongCandidates(twoCandidates).size)
        assertThrows(JsonParseException::class.java) {
            DeepSeekCaptionEnhancementJson.parseSongCandidates(threeCandidates)
        }
    }

    @Test
    fun requestPayloadCarriesConfidenceAndMediaDuration() {
        val request = CaptionEnhancementRequest(
            jobId = "job-1",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = listOf(
                CaptionEnhancementRequestCue("cue-1", 0L, 1_000L, "Hello from the quiet street", 0.87f),
            ),
            mediaDurationMs = 18_716L,
        )

        val body = DeepSeekCaptionEnhancementJson.songIdentificationRequestBody(request)

        assertTrue(body.contains("\\\"confidence\\\":0.87"))
        assertTrue(body.contains("\\\"media_duration_ms\\\":18716"))
    }

    @Test
    fun unconfirmedPromptRemainsConservativeAndDoesNotClaimNeteaseVersion() {
        assertEquals(
            """
当前没有从在线歌词来源取得并验证完整歌词，不得声称歌曲已经确认，不得编造 canonical 歌词或网易云中英对照歌词。
必须先综合整批 Whisper 英文字幕进行保守纠错，确定全部 corrected_english；完成后再根据整批上下文生成自然的中文歌词。
中文应忠实表达歌曲原意而不是逐词直译，并保持意象、情绪、语气、代词、跨行语义和重复内容一致；不能把每条字幕孤立翻译，也不得声称为网易云版本。
不得增加、删除、拆分、合并、重排字幕或修改时间。每个 cue id 和时间戳必须原样保留。
单条 raw_english 可能包含同一歌曲的两句歌词。对这类 cue，corrected_english 应在保持该 cue 原有时间范围不变的前提下包含两句完整英文歌词，两句之间用一个换行符分隔；对应的 chinese 同样输出两句中文并用一个换行符分隔，两句中文必须与两句英文一一对应。其余 cue 仍然只输出单行。
cues 中的 confidence 是该条 Whisper 识别的置信度：数值越低说明该条错得越多，纠错幅度可以越大；confidence 高的条目应尽量保守。media_duration_ms 是素材总时长。
只返回 JSON，格式必须严格为：
{"schema_version":"<copy input>","job_id":"<copy input>","processing_version":"deepseek-v4-pro-lyrics-search-context.v5","cues":[{"id":"<copy input>","start_ms":0,"end_ms":1,"corrected_english":"complete English line","chinese":"coherent Chinese lyric line"}]}.
每个 cue 必须包含上面展示的全部六个字段。不要返回 song_match。
""".trimIndent(),
            DeepSeekCaptionEnhancementProvider.UNCONFIRMED_SYSTEM_PROMPT,
        )
    }

    @Test
    fun parseResponseMapsCueContractAndDoesNotTrustModelSongMatch() {
        val body = envelope(
            """{"schema_version":"caption-enhancement.v3","job_id":"job-1","processing_version":"ignored-model-version","cues":[{"id":"cue-1","start_ms":0,"end_ms":1000,"corrected_english":"Hello","chinese":"你好"}],"song_match":{"status":"CONFIRMED","title":"Invented","artist":"Invented"}}""",
        )

        val response = DeepSeekCaptionEnhancementJson.parseResponse(body)

        assertEquals("job-1", response.jobId)
        assertEquals("caption-enhancement.v3", response.schemaVersion)
        assertEquals("ignored-model-version", response.processingVersion)
        assertEquals("cue-1", response.cues.single().id)
        assertEquals(0L, response.cues.single().startMs)
        assertEquals(1000L, response.cues.single().endMs)
        assertEquals("Hello", response.cues.single().correctedEnglish)
        assertEquals("你好", response.cues.single().chinese)
        assertEquals(null, response.songMatch)
    }

    @Test
    fun twoStageProviderConfirmsOnlyLocallyVerifiedSearchCandidate() = runBlocking {
        val request = request()
        val connections = ArrayDeque<FakeConnection>().apply {
            add(FakeConnection(200, envelope("""{"candidates":[{"title":"Quiet Street","artist":"Example Artist"}]}""")))
            add(FakeConnection(200, enhancementEnvelope(request)))
        }
        val searched = mutableListOf<SongIdentityCandidate>()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val byokManager = FakeByokManager()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = byokManager,
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> {
                    assertFalse(byokManager.plaintextKeyInScope)
                    searched += identity
                    return listOf(SongLyricsCandidate("lrclib:42", "Quiet Street", "Example Artist", lyrics()))
                }
            },
            connectionFactory = { connections.removeFirst() },
            onDiagnosticStage = stages::add,
        )

        val response = provider.enhance(request)

        assertEquals(listOf(SongIdentityCandidate("Quiet Street", "Example Artist")), searched)
        assertEquals(
            listOf(
                DeepSeekEnhancementStage.CANDIDATE_REQUEST,
                DeepSeekEnhancementStage.CANDIDATE_PARSE,
                DeepSeekEnhancementStage.LYRICS_SEARCH,
                DeepSeekEnhancementStage.LYRICS_VERIFY,
                DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED,
                DeepSeekEnhancementStage.WHOLE_SONG_REQUEST,
                DeepSeekEnhancementStage.WHOLE_SONG_PARSE,
            ),
            stages,
        )
        assertEquals(SongMatchStatus.CONFIRMED, response.songMatch?.status)
        assertEquals("lrclib:42", response.songMatch?.source)
        assertEquals(DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION, response.processingVersion)
        assertEquals(request.cues.map { it.id }, response.cues.map { it.id })
    }

    @Test
    fun lyricTextFallbackConfirmsSongWhenMetadataSearchMisses() = runBlocking {
        val request = request()
        val connections = ArrayDeque<FakeConnection>().apply {
            add(FakeConnection(200, envelope("""{"candidates":[{"title":"Sadie of Stars","artist":"Example Artist"}]}""")))
            add(FakeConnection(200, enhancementEnvelope(request)))
        }
        var fallbackQuery: String? = null
        val usedConnections = mutableListOf<FakeConnection>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = FakeByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> = emptyList()

                override suspend fun searchByLyricText(queryText: String): List<SongLyricsCandidate> {
                    fallbackQuery = queryText
                    return listOf(SongLyricsCandidate("lrclib:99", "Quiet Street", "Example Artist", lyrics()))
                }
            },
            connectionFactory = {
                connections.removeFirst().also(usedConnections::add)
            },
        )

        val response = provider.enhance(request)

        assertEquals(SongMatchStatus.CONFIRMED, response.songMatch?.status)
        assertEquals("lrclib:99", response.songMatch?.source)
        assertTrue(requireNotNull(fallbackQuery).startsWith("Hello from the quiet street"))
        val finalBody = usedConnections.last().writtenBody()
        assertTrue(finalBody.contains("verified_complete_lyrics"))
    }

    @Test
    fun searchFailureUsesWholeBatchCloudFallbackWithoutClaimingConfirmation() = runBlocking {
        val request = request()
        val usedConnections = mutableListOf<FakeConnection>()
        val connections = ArrayDeque<FakeConnection>().apply {
            add(FakeConnection(200, envelope("""{"candidates":[{"title":"Quiet Street","artist":"Example Artist"}]}""")))
            add(FakeConnection(200, enhancementEnvelope(request)))
        }
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = FakeByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> {
                    throw SongLyricsSearchException(SongLyricsSearchFailureKind.CONNECTION, "private detail")
                }
            },
            connectionFactory = {
                connections.removeFirst().also(usedConnections::add)
            },
        )

        val response = provider.enhance(request)

        assertEquals(SongMatchStatus.UNCONFIRMED, response.songMatch?.status)
        assertEquals("lyrics-search-unavailable", response.songMatch?.source)
        val finalRequest = usedConnections.last().writtenBody()
        assertTrue(finalRequest.contains("unconfirmed_full_batch"))
        assertTrue(finalRequest.contains("先综合整批 Whisper 英文字幕进行保守纠错"))
        assertTrue(finalRequest.contains("不得声称为网易云版本"))
        assertFalse(finalRequest.contains("complete_english_lyrics"))
    }

    private fun request(): CaptionEnhancementRequest {
        val lines = lyrics().lineSequence().filter { it.isNotBlank() }.toList()
        return CaptionEnhancementRequest(
            jobId = "job-1",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = lines.mapIndexed { index, line ->
                CaptionEnhancementRequestCue("cue-${index + 1}", index * 1_000L, (index + 1) * 1_000L, line)
            },
        )
    }

    private fun lyrics() = """
        Hello from the quiet street
        We follow every fading light
        The river keeps our secrets close
        We carry hope into the night
        Hello from the quiet street
    """.trimIndent()

    private fun enhancementEnvelope(request: CaptionEnhancementRequest): String = envelope(
        encodeJson(
            mapOf(
                "schema_version" to request.schemaVersion,
                "job_id" to request.jobId,
                "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
                "cues" to request.cues.map { cue ->
                    mapOf(
                        "id" to cue.id,
                        "start_ms" to cue.startMs,
                        "end_ms" to cue.endMs,
                        "corrected_english" to cue.rawEnglish,
                        "chinese" to "测试歌词",
                    )
                },
            ),
        ),
    )

    private fun envelope(content: String): String = encodeJson(
        mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
    )

    private class FakeConnection(
        private val status: Int,
        private val response: String,
    ) : HttpURLConnection(URL(DeepSeekCaptionEnhancementProvider.ENDPOINT)) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = ByteArrayInputStream(response.toByteArray())

        fun writtenBody(): String = output.toString(Charsets.UTF_8.name())
    }

    private class FakeByokManager : DeepSeekByokManager {
        var plaintextKeyInScope = false
            private set

        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "masked")
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T {
            plaintextKeyInScope = true
            return try {
                block("sk-test-sentinel")
            } finally {
                plaintextKeyInScope = false
            }
        }
    }
}
