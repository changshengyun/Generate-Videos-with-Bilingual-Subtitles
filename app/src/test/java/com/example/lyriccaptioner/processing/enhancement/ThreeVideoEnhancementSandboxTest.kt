package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Local strategy sandbox: replays the three device-verified videos (2026-08-28) through the
 * SAME provider/verifier/validator classes the app ships with, against the REAL DeepSeek
 * endpoint. Because this test references the app classes directly, any prompt or rule edit made
 * here reflects the exact current strategy; tune prompts in DeepSeekCaptionEnhancementProvider
 * (or thresholds in SongLyricsCandidateVerifier), rerun this test, inspect the generated
 * Markdown report, and the change is already in sync with the app.
 *
 * Report format contract (user-mandated):
 *   overall trigger/interception flow chart -> local hard rules -> per video
 *   (input table, flow 1..4 prompt + input + output + triggered strategy) -> summary.
 *
 * Skips automatically when DEEPSEEK_API_KEY is missing or rejected.
 */
class ThreeVideoEnhancementSandboxTest {

    private data class VideoOutcome(
        val label: String,
        val request: CaptionEnhancementRequest,
        val response: CaptionEnhancementResponse?,
        val songMatch: SongMatch?,
        val searches: List<RecordingSearchTool.Event>,
        val flow3Mode: String,
    )

    @Test
    fun replayThreeVideosWithCurrentStrategyAndWriteSandboxReport() = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping sandbox rerun", EnhancementTestEnv.isConfigured)

        val videos = listOf(
            video1Request() to "视频1（5e4c…）",
            video2Request() to "视频2（6101…）",
            video3Request() to "视频3（f176…）",
        )

        val report = StringBuilder()
        report.appendLine("# 三视频本地沙箱重跑（当前策略同步版）")
        report.appendLine()
        report.appendLine("运行时间：${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}，" +
            "端点 `${DeepSeekCaptionEnhancementProvider.ENDPOINT}`，模型 `${DeepSeekCaptionEnhancementProvider.MODEL}`。")
        report.appendLine("本沙箱与 app 共用同一份 `DeepSeekCaptionEnhancementProvider` / `SongLyricsCandidateVerifier` / " +
            "`CaptionEnhancementResponseValidator` 源码：在此修改提示词或阈值后重跑，即等同于修改 app 策略。")
        report.appendLine()

        writeOverallStrategy(report)
        writeLocalRules(report)

        val outcomes = mutableListOf<VideoOutcome>()

        videos.forEachIndexed { index, (request, label) ->
            val connections = mutableListOf<RecordingHttpConnection>()
            val recordingSearch = RecordingSearchTool(LrclibSongLyricsSearchTool())
            val provider = DeepSeekCaptionEnhancementProvider(
                byokManager = EnvFileDeepSeekByokManager(),
                searchTool = recordingSearch,
                connectionFactory = { url ->
                    RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
                },
            )

            val rawCaptions = request.cues.map { cue ->
                CaptionCue(
                    id = cue.id,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    english = cue.rawEnglish,
                    chinese = "",
                    confidence = cue.confidence ?: 0f,
                )
            }

            val response = try {
                provider.enhance(request)
            } catch (error: CaptionEnhancementProviderException) {
                if (error.kind == CaptionEnhancementErrorKind.AUTHENTICATION) {
                    println("DEEPSEEK_API_KEY in .env was rejected (401). Update the key and rerun.")
                    assumeTrue("DeepSeek rejected the configured API key", false)
                }
                throw error
            }

            val flow3Verified = response.songMatch?.status == SongMatchStatus.CONFIRMED
            val flow3Mode = if (flow3Verified) "verified_complete_lyrics" else "unconfirmed_full_batch"
            writeVideoSection(report, label, request, response, recordingSearch.events, connections, flow3Mode, rawCaptions)
            outcomes.add(VideoOutcome(label, request, response, response.songMatch, recordingSearch.events, flow3Mode))

            // Hard contract: 1:1 cues, ids and timestamps intact.
            assertEquals(request.cues.map { it.id }, response.cues.map { it.id })
            if (index < videos.size - 1) report.appendLine()
        }

        writeSummary(report, outcomes)
        val artifact = writeReport(report.toString())
        println("sandbox report saved to ${artifact.absolutePath}")
        assertTrue(outcomes.all { it.response != null && it.response.cues.all { cue -> cue.chinese.isNotBlank() } })
    }

    // ---------- report sections ----------

    private fun writeOverallStrategy(report: StringBuilder) {
        report.appendLine("## 整体触发/拦截策略")
        report.appendLine()
        report.appendLine("```mermaid")
        report.appendLine("flowchart TD")
        report.appendLine("    A[\"输入：整批 Whisper cue\"] --> B{\"cue 数 ≥ ${SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES}？\"}")
        report.appendLine("    B -->|\"否：门槛拦截\"| J")
        report.appendLine("    B -->|是| C[\"流程1：LLM 猜歌名（最多 ${DeepSeekCaptionEnhancementProvider.MAX_SONG_CANDIDATES} 个候选）\"]")
        report.appendLine("    C --> D[\"流程2：逐候选检索完整歌词\"]")
        report.appendLine("    D --> E{\"本地 DP 校验通过？\"}")
        report.appendLine("    E -->|是| F[\"CONFIRMED：注入已验证歌词\"]")
        report.appendLine("    E -->|\"否，且检索服务可用\"| G[\"兜底：歌词原文文本检索 + 再校验\"]")
        report.appendLine("    G -->|命中| F")
        report.appendLine("    G -->|未命中| H[\"UNCONFIRMED\"]")
        report.appendLine("    E -->|\"检索服务不可用\"| H")
        report.appendLine("    F --> I[\"流程3：已验证模式，按权威歌词纠错+翻译\"]")
        report.appendLine("    H --> J[\"流程3：保守模式，忠实直译不编造\"]")
        report.appendLine("    I --> K[\"流程4：本地校验（1:1、时间戳不变）并落屏\"]")
        report.appendLine("    J --> K")
        report.appendLine("```")
        report.appendLine()
    }

    private fun writeLocalRules(report: StringBuilder) {
        report.appendLine("## 本地固有规则 / 拦截规则（源码常量同步）")
        report.appendLine()
        report.appendLine("| 规则 | 当前值 | 所在位置 |")
        report.appendLine("|---|---|---|")
        report.appendLine("| 启动门槛：cue 数达到才走歌曲识别 | ≥ ${SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES} | SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES |")
        report.appendLine("| 候选数上限（流程1） | ${DeepSeekCaptionEnhancementProvider.MAX_SONG_CANDIDATES} | DeepSeekCaptionEnhancementProvider.MAX_SONG_CANDIDATES |")
        report.appendLine("| DP 校验确认阈值（常规批） | ${SongLyricsCandidateVerifier.MIN_CONFIDENCE} | SongLyricsCandidateVerifier.MIN_CONFIDENCE |")
        report.appendLine("| DP 校验确认阈值（≤5 条小批） | ${SongLyricsCandidateVerifier.SMALL_BATCH_MIN_CONFIDENCE} | SMALL_BATCH_MIN_CONFIDENCE |")
        report.appendLine("| 最少匹配 cue 数（常规/小批） | ${SongLyricsCandidateVerifier.MIN_MATCHED_CUES} / ${SongLyricsCandidateVerifier.SMALL_BATCH_MIN_MATCHED_CUES} | MIN_MATCHED_CUES / SMALL_BATCH_MIN_MATCHED_CUES |")
        report.appendLine("| 最少覆盖率（常规/小批） | ${SongLyricsCandidateVerifier.MIN_COVERAGE} / ${SongLyricsCandidateVerifier.SMALL_BATCH_MIN_COVERAGE} | MIN_COVERAGE / SMALL_BATCH_MIN_COVERAGE |")
        report.appendLine("| 单条相似度下限 | ${SongLyricsCandidateVerifier.MIN_CUE_SIMILARITY} | MIN_CUE_SIMILARITY |")
        report.appendLine("| 兜底门控 | 无候选通过校验且检索服务可用 → 歌词原文检索 | DeepSeekCaptionEnhancementProvider.findVerifiedLyrics |")
        report.appendLine("| 兜底查询长度上限 | ${DeepSeekCaptionEnhancementProvider.FALLBACK_QUERY_MAX_CHARS} 字符 | FALLBACK_QUERY_MAX_CHARS |")
        report.appendLine("| 输出合同 | 输入输出条数 1:1，id 与时间戳不可变，丢弃模型自报 song_match | CaptionEnhancementContract / ResponseValidator |")
        report.appendLine()
        report.appendLine("DP（Dynamic Programming，动态规划）校验在设备本地运行，不消耗任何 API（Application Programming " +
            "Interface，应用程序接口）费用，是唯一有权\"确认歌曲\"的环节。")
        report.appendLine()
    }

    private fun writeVideoSection(
        report: StringBuilder,
        label: String,
        request: CaptionEnhancementRequest,
        response: CaptionEnhancementResponse,
        searches: List<RecordingSearchTool.Event>,
        connections: List<RecordingHttpConnection>,
        flow3Mode: String,
        rawCaptions: List<CaptionCue>,
    ) {
        report.appendLine("---")
        report.appendLine()
        report.appendLine("## $label")
        report.appendLine()
        report.appendLine("### 输入（Whisper ASR（自动语音识别）结果）")
        report.appendLine()
        report.appendLine("| id | 时间轴 | confidence | 英文原文 |")
        report.appendLine("|---|---|---|---|")
        request.cues.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.startMs}..${cue.endMs} | ${cue.confidence ?: "—"} | ${cue.rawEnglish} |")
        }
        report.appendLine()

        // Flow 1
        val candidates = DeepSeekCaptionEnhancementJson.parseSongCandidates(connections[0].rawResponse())
        report.appendLine("### 流程 1 歌曲识别（LLM（大语言模型）调用 #1）")
        report.appendLine()
        report.appendLine("**使用的 Prompt：**")
        report.appendLine()
        report.appendLine("```text")
        report.appendLine(DeepSeekCaptionEnhancementProvider.IDENTIFICATION_SYSTEM_PROMPT)
        report.appendLine("```")
        report.appendLine()
        report.appendLine("**输出：** ${candidates.size} 个候选")
        report.appendLine()
        report.appendLine("| # | 歌名 | 歌手 |")
        report.appendLine("|---|---|---|")
        candidates.forEachIndexed { i, candidate ->
            report.appendLine("| ${i + 1} | ${candidate.title} | ${candidate.artist} |")
        }
        report.appendLine()

        // Flow 2
        report.appendLine("### 流程 2 歌词检索 + 本地 DP 校验（无 LLM 调用）")
        report.appendLine()
        report.appendLine("| 检索 | 查询 | 命中数 |")
        report.appendLine("|---|---|---|")
        searches.forEach { event ->
            val kind = if (event.kind == RecordingSearchTool.Kind.IDENTITY) "身份检索" else "文本兜底检索"
            report.appendLine("| $kind | ${event.query.replace("|", "/")} | ${event.hits} |")
        }
        report.appendLine()
        report.appendLine("songMatch：`${response.songMatch}`")
        report.appendLine()
        report.appendLine("**触发的策略：** ${triggeredStrategyText(response.songMatch, searches)}")
        report.appendLine()

        // Flow 3
        val verified = flow3Mode == "verified_complete_lyrics"
        report.appendLine("### 流程 3 双语生成（LLM 调用 #2，$flow3Mode）")
        report.appendLine()
        report.appendLine("**使用的 Prompt：**")
        report.appendLine()
        report.appendLine("```text")
        report.appendLine(if (verified) {
            DeepSeekCaptionEnhancementProvider.VERIFIED_LYRICS_SYSTEM_PROMPT
        } else {
            DeepSeekCaptionEnhancementProvider.UNCONFIRMED_SYSTEM_PROMPT
        })
        report.appendLine("```")
        report.appendLine()
        report.appendLine("**输入要点：** ${if (verified) {
            "mode=`verified_complete_lyrics`；已注入歌曲 `${response.songMatch?.title}` / `${response.songMatch?.artist}`" +
                "（`${response.songMatch?.source}`）的完整英文歌词与逐 cue canonical 对齐。"
        } else {
            "mode=`unconfirmed_full_batch`；无权威歌词，仅携带 ${request.cues.size} 条原始 cue，禁止编造。"
        }}")
        report.appendLine()
        report.appendLine("**输出：**")
        report.appendLine()
        report.appendLine("| id | 时间轴 | corrected_english | chinese |")
        report.appendLine("|---|---|---|---|")
        response.cues.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.startMs}..${cue.endMs} | ${cue.correctedEnglish} | ${cue.chinese} |")
        }
        report.appendLine()

        // Flow 4
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        report.appendLine("### 流程 4 本地校验（无 LLM 调用）")
        report.appendLine()
        report.appendLine("state=`${outcome.state}` source=`${outcome.source}` 应用 `${outcome.captions.size}` 条。")
        report.appendLine()
        report.appendLine("| id | 英文 | 中文 |")
        report.appendLine("|---|---|---|")
        outcome.captions.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.english} | ${cue.chinese} |")
        }
    }

    private fun triggeredStrategyText(songMatch: SongMatch?, searches: List<RecordingSearchTool.Event>): String {
        val identitySearches = searches.count { it.kind == RecordingSearchTool.Kind.IDENTITY }
        val textFallback = searches.firstOrNull { it.kind == RecordingSearchTool.Kind.TEXT }
        return when {
            songMatch?.status == SongMatchStatus.CONFIRMED ->
                "身份检索命中且 DP 校验通过 → CONFIRMED，注入权威歌词。"
            songMatch?.status == SongMatchStatus.UNCONFIRMED -> buildString {
                append("$identitySearches 路身份检索")
                if (textFallback != null) append(" + 1 路文本兜底检索（放宽门控触发）")
                append(" 均未通过 DP 校验 → UNCONFIRMED，走保守翻译（错误歌曲被本地校验拦截）。")
            }
            else -> "无候选或检索全部失败 → NOT_FOUND，走保守翻译。"
        }
    }

    private fun writeSummary(report: StringBuilder, outcomes: List<VideoOutcome>) {
        report.appendLine()
        report.appendLine("---")
        report.appendLine()
        report.appendLine("## 三视频汇总")
        report.appendLine()
        report.appendLine("| 视频 | cue 数 | 流程1候选 | 流程2结论 | 流程3模式 | 触发策略摘要 |")
        report.appendLine("|---|---|---|---|---|---|")
        outcomes.forEach { outcome ->
            val candidateCount = outcome.searches.count { it.kind == RecordingSearchTool.Kind.IDENTITY }
            report.appendLine(
                "| ${outcome.label} | ${outcome.request.cues.size} | $candidateCount 路检索 | " +
                    "`${outcome.songMatch?.status ?: "NOT_FOUND"}` | ${outcome.flow3Mode} | " +
                    "${triggeredStrategyText(outcome.songMatch, outcome.searches)} |"
            )
        }
    }

    private fun writeReport(content: String): File {
        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "three-video-local-sandbox-rerun.md")
        artifact.writeText(content, Charsets.UTF_8)
        return artifact
    }

    // ---------- inputs: real ASR cues captured from the three device videos (2026-08-28) ----------

    /** 5e4c3cd7073a9e9b03df1fbf8af6d928.mp4, 31.58 s, 9 cues. */
    private fun video1Request(): CaptionEnhancementRequest = CaptionEnhancementRequest(
        jobId = "sandbox-video1-5e4c",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(
            CaptionEnhancementRequestCue("whisper-0-0", 0L, 2800L, "I have to live without you", 0.92f),
            CaptionEnhancementRequestCue("whisper-1-2800", 2800L, 7000L, "Nobody could, I need to be around you", 0.88f),
            CaptionEnhancementRequestCue("whisper-2-7000", 7000L, 10600L, "Watching you, no one else can love you", 0.93f),
            CaptionEnhancementRequestCue("whisper-3-10600", 10600L, 12600L, "Like I do"),
            CaptionEnhancementRequestCue("whisper-4-12600", 12600L, 17000L, "Healing and I'm creeping up on you", 0.63f),
            CaptionEnhancementRequestCue("whisper-5-17000", 17000L, 20000L, "I know that it won't be right", 0.94f),
            CaptionEnhancementRequestCue("whisper-6-20000", 20000L, 25000L, "If I stay all night to be among you", 0.78f),
            CaptionEnhancementRequestCue("whisper-7-25000", 25000L, 29000L, "Creeping my own you", 0.68f),
            CaptionEnhancementRequestCue("whisper-8-30000", 30000L, 31400L, "(upbeat music)", 0.77f),
        ),
        mediaDurationMs = 31_584L,
    )

    /** 6101d9b51a973fcc6bc8432d87851280.mp4, 36.29 s, 4 cues. */
    private fun video2Request(): CaptionEnhancementRequest = CaptionEnhancementRequest(
        jobId = "sandbox-video2-6101",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(
            CaptionEnhancementRequestCue("whisper-0-0", 0L, 8000L, "It was like we're all who stands apart", 0.63f),
            CaptionEnhancementRequestCue("whisper-1-9000", 9000L, 13000L, "There's so much space between us", 0.91f),
            CaptionEnhancementRequestCue("whisper-2-13000", 13000L, 17000L, "Baby, we're already behind", 0.68f),
            CaptionEnhancementRequestCue("whisper-3-17000", 17000L, 35840L, "And you have given me something that I can't live without.", 0.81f),
        ),
        mediaDurationMs = 36_293L,
    )

    /** f1764157e6fccc410443c5cbefaecfac.mp4, ~33.2 s, 5 cues. */
    private fun video3Request(): CaptionEnhancementRequest = CaptionEnhancementRequest(
        jobId = "sandbox-video3-f176",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(
            CaptionEnhancementRequestCue("whisper-0-0", 0L, 6240L, "[Music]", 0.52f),
            CaptionEnhancementRequestCue("whisper-1-6240", 6240L, 14240L, "Take your eyes off of me so I can leave", 0.76f),
            CaptionEnhancementRequestCue("whisper-2-14240", 14240L, 23160L, "I'm far too ashamed to do it with you watching me", 0.84f),
            CaptionEnhancementRequestCue("whisper-3-23160", 23160L, 32160L, "This is never ending, we have been here before", 0.80f),
            CaptionEnhancementRequestCue("whisper-4-32160", 32160L, 33160L, "But I", 0.58f),
        ),
        mediaDurationMs = 33_200L,
    )

    // ---------- recording decorators ----------

    /** Records every lyrics search call so the report can show which strategy branches fired. */
    private class RecordingSearchTool(private val delegate: SongLyricsSearchTool) : SongLyricsSearchTool {
        enum class Kind { IDENTITY, TEXT }

        data class Event(val kind: Kind, val query: String, val hits: Int)

        val events = mutableListOf<Event>()

        override suspend fun search(candidate: SongIdentityCandidate): List<SongLyricsCandidate> {
            val results = delegate.search(candidate)
            events.add(Event(Kind.IDENTITY, "${candidate.title} | ${candidate.artist}", results.size))
            return results
        }

        override suspend fun searchByLyricText(queryText: String): List<SongLyricsCandidate> {
            val results = delegate.searchByLyricText(queryText)
            events.add(Event(Kind.TEXT, queryText.take(60) + if (queryText.length > 60) "…" else "", results.size))
            return results
        }
    }

    /** Wraps the real connection, capturing the written request body and raw response. */
    private class RecordingHttpConnection(private val delegate: HttpURLConnection) : HttpURLConnection(delegate.url) {
        private val captured = ByteArrayOutputStream()
        private var responseBody: ByteArray? = null
        private var flushed = false
        var statusCode: Int = -1
            private set

        fun writtenBody(): String = captured.toString(Charsets.UTF_8.name())

        fun rawResponse(): String = responseBody?.toString(Charsets.UTF_8) ?: ""

        private fun flushBody() {
            if (!flushed) {
                flushed = true
                delegate.outputStream.use { it.write(captured.toByteArray()) }
            }
        }

        override fun setRequestMethod(method: String) = delegate.setRequestMethod(method)
        override fun setConnectTimeout(timeout: Int) { delegate.connectTimeout = timeout }
        override fun setReadTimeout(timeout: Int) { delegate.readTimeout = timeout }
        override fun setDoOutput(dooutput: Boolean) = delegate.setDoOutput(dooutput)
        override fun setInstanceFollowRedirects(followRedirects: Boolean) {
            delegate.instanceFollowRedirects = followRedirects
        }
        override fun setUseCaches(usecaches: Boolean) { delegate.useCaches = usecaches }
        override fun setRequestProperty(key: String, value: String) = delegate.setRequestProperty(key, value)
        override fun connect() = delegate.connect()
        override fun disconnect() = delegate.disconnect()
        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = captured

        override fun getResponseCode(): Int {
            flushBody()
            return delegate.responseCode.also { statusCode = it }.also { status ->
                if (status !in 200..299) {
                    responseBody = try {
                        delegate.errorStream?.use { stream -> stream.readBytes() }
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        override fun getInputStream(): InputStream {
            flushBody()
            val bytes = delegate.inputStream.use { it.readBytes() }
            responseBody = bytes
            return ByteArrayInputStream(bytes)
        }

        override fun getErrorStream(): InputStream? {
            val stream = delegate.errorStream ?: return null
            val bytes = stream.use { it.readBytes() }
            responseBody = bytes
            return ByteArrayInputStream(bytes)
        }
    }
}
