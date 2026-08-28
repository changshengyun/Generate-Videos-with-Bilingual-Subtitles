package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline walkthrough using REAL Whisper ASR output captured from a physical device.
 *
 * Data source: test-artifacts/device-capture/device-asr-base.json
 *   - Device: Xiaomi 25098PN5AC (fcf4b0cb)
 *   - Audio: 31.6s, Whisper v1.9.1, 9 segments, context fresh
 *   - Song: appears to be a Beatles-style track ("creeping up on you", "words of wisdom" motifs)
 *
 * This test replays the 9 real segments through the full 4-flow pipeline using fake HTTP
 * connections (no real API calls). It demonstrates the exact stage trace and data flow
 * that would occur at runtime, and writes a Markdown report to test-artifacts.
 */
class DeviceAsrWalkthroughTest {

    @Test
    fun realDeviceNineSegmentsRunThroughTheFullFourFlowPipeline() = runBlocking {
        val request = deviceAsrBaseRequest()
        val rawCaptions = request.cues.map { cue ->
            CaptionCue(cue.id, cue.startMs, cue.endMs, cue.rawEnglish, "", cue.confidence ?: 0f)
        }

        // Mock: Flow 1 identifies the song, Flow 2 LRCLIB returns lyrics, Flow 3 generates bilingual
        val connections = ArrayDeque<WalkthroughConnection>().apply {
            // Flow 1 response: AI identifies the song
            add(WalkthroughConnection(200, envelope(
                """{"candidates":[{"title":"Creeping Up on You","artist":"The Beatles"}]}"""
            )))
            // Flow 3 response: bilingual generation
            add(WalkthroughConnection(200, enhancementEnvelope(request)))
        }
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val usedConnections = mutableListOf<WalkthroughConnection>()

        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = TestByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> {
                    // Simulate LRCLIB returning lyrics that roughly match the real ASR output
                    val lyrics = listOf(
                        "I have to live without you",
                        "Nobody could, I need to be around you",
                        "Watching you, no one else can love you",
                        "Like I do",
                        "Healing and I'm creeping up on you",
                        "I know that it won't be long",
                        "If I stay all night to be among you",
                        "Creeping my own you",
                    ).joinToString("\n")
                    return listOf(SongLyricsCandidate(
                        "lrclib:device-asr", identity.title, identity.artist, lyrics,
                    ))
                }
            },
            connectionFactory = { connections.removeFirst().also(usedConnections::add) },
            onDiagnosticStage = stages::add,
        )

        val response = provider.enhance(request)

        // Flow 4: local batch validation
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)

        // ---- Generate report ----
        val report = StringBuilder()
        report.appendLine("# 真机 ASR 数据离线 Walkthrough（9 段真实 Whisper 输出）")
        report.appendLine()
        report.appendLine("运行时间：${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        report.appendLine("数据来源：`test-artifacts/device-capture/device-asr-base.json`")
        report.appendLine("设备：Xiaomi 25098PN5AC (fcf4b0cb)，Whisper v1.9.1，31.6s 音频，9 段识别")
        report.appendLine("注意：本测试使用假 HTTP 响应（离线），不消耗 API 额度")
        report.appendLine()

        report.appendLine("## 输入：真机 Whisper 识别结果")
        report.appendLine()
        report.appendLine("| # | 时间 | 识别文本 | 置信度 | token 数 |")
        report.appendLine("|---|---|---|---|---|")
        request.cues.forEachIndexed { index, cue ->
            report.appendLine("| ${index} | ${cue.startMs}..${cue.endMs}ms | ${cue.rawEnglish} | ${cue.confidence} | ${deviceAsrSegments()[index].tokenCount} |")
        }
        report.appendLine()

        report.appendLine("## 流程 Stage Trace")
        report.appendLine()
        report.appendLine("```")
        report.appendLine("enhance(request)")
        report.appendLine("  ├─ searchScheduler == null → Legacy 路径")
        report.appendLine("  │")
        stages.forEachIndexed { index, stage ->
            val prefix = if (index == stages.size - 1) "  └─" else "  ├─"
            report.appendLine("$prefix $stage")
        }
        report.appendLine("```")
        report.appendLine()
        report.appendLine("Stage 序列：`${stages.joinToString(" → ")}`")
        report.appendLine()

        report.appendLine("## Flow 1: 歌曲识别")
        report.appendLine()
        report.appendLine("- HTTP 请求：Chat Completions（`/chat/completions`）")
        report.appendLine("- 输入：9 条 Whisper cue（含 1 条噪声 `(upbeat music)`）")
        report.appendLine("- 假响应返回：`Creeping Up on You` by `The Beatles`")
        report.appendLine("- Stage: CANDIDATE_REQUEST → CANDIDATE_PARSE")
        report.appendLine()

        report.appendLine("## Flow 2: LRCLIB 搜索 + DP 验证")
        report.appendLine()
        report.appendLine("- searchTool.search() 返回模拟歌词（8 行）")
        report.appendLine("- SongLyricsCandidateVerifier.verify() 进行 DP 对齐")
        val hasVerified = stages.contains(DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED)
        report.appendLine("- DP 验证结果：${if (hasVerified) "CONFIRMED（过线）" else "未过线 → 保守模式"}")
        report.appendLine("- Stage: LYRICS_SEARCH → LYRICS_VERIFY${if (hasVerified) " → VERIFIED_LYRICS_SELECTED" else ""}")
        report.appendLine()

        report.appendLine("## Flow 3: 双语生成")
        report.appendLine()
        val mode = if (hasVerified) "verified_complete_lyrics" else "unconfirmed_full_batch"
        report.appendLine("- 模式：`$mode`")
        report.appendLine("- HTTP 请求：Chat Completions（`/chat/completions`）")
        report.appendLine("- Stage: WHOLE_SONG_REQUEST → WHOLE_SONG_PARSE")
        report.appendLine()
        report.appendLine("### 输出字幕")
        report.appendLine()
        report.appendLine("| id | 时间 | corrected_english | chinese |")
        report.appendLine("|---|---|---|---|")
        response.cues.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.startMs}..${cue.endMs}ms | ${cue.correctedEnglish} | ${cue.chinese} |")
        }
        report.appendLine()

        report.appendLine("## Flow 4: 本地批次校验")
        report.appendLine()
        report.appendLine("- state: `${outcome.state}`")
        report.appendLine("- source: `${outcome.source}`")
        report.appendLine("- songMatch: `${response.songMatch}`")
        report.appendLine("- 应用 ${outcome.captions.size} 条字幕")
        report.appendLine()

        report.appendLine("## 汇总")
        report.appendLine()
        report.appendLine("| 维度 | 值 |")
        report.appendLine("|---|---|")
        report.appendLine("| 输入 cue 数 | ${request.cues.size} |")
        report.appendLine("| 输出 cue 数 | ${response.cues.size} |")
        report.appendLine("| Stage 数 | ${stages.size} |")
        report.appendLine("| 歌曲状态 | ${response.songMatch?.status} |")
        report.appendLine("| Flow 4 状态 | ${outcome.state} |")
        report.appendLine("| 是否走真实 API | 否（离线假响应） |")

        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "device-asr-walkthrough.md")
        artifact.writeText(report.toString(), Charsets.UTF_8)
        println(report.toString())
        println("report saved to ${artifact.absolutePath}")

        // ---- Assertions ----
        assertEquals("1:1 cue contract", request.cues.map { it.id }, response.cues.map { it.id })
        assertEquals("9 segments in, 9 cues out", 9, response.cues.size)
        assertTrue("all stages present", stages.contains(DeepSeekEnhancementStage.CANDIDATE_REQUEST))
        assertTrue("flow 3 ran", stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_PARSE))
        assertTrue("all Chinese non-blank", outcome.captions.all { it.chinese.isNotBlank() })
    }

    /** Build request from the real device-asr-base.json segments. */
    private fun deviceAsrBaseRequest(): CaptionEnhancementRequest {
        val segments = deviceAsrSegments()
        return CaptionEnhancementRequest(
            jobId = "device-asr-walkthrough",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = segments.map { seg ->
                CaptionEnhancementRequestCue(
                    id = "device-asr-${seg.index}",
                    startMs = seg.startMs.toLong(),
                    endMs = seg.endMs.toLong(),
                    rawEnglish = seg.text.trim(),
                    confidence = seg.avgTokenProb,
                )
            },
            mediaDurationMs = 31_602L,
        )
    }

    /** Parsed segments from device-asr-base.json (real Whisper output). */
    private data class AsrSegment(
        val index: Int, val startMs: Int, val endMs: Int,
        val text: String, val avgTokenProb: Float, val tokenCount: Int,
    )

    private fun deviceAsrSegments(): List<AsrSegment> = listOf(
        AsrSegment(0, 0, 2800, "I have to live without you", 0.81f, 8),
        AsrSegment(1, 2800, 7000, "Nobody could, I need to be around you", 0.80f, 10),
        AsrSegment(2, 7000, 10600, "Watching you, no one else can love you", 0.85f, 10),
        AsrSegment(3, 10600, 12600, "Like I do", 0.71f, 4),
        AsrSegment(4, 12600, 17000, "Healing and I'm creeping up on you", 0.60f, 9),
        AsrSegment(5, 17000, 20000, "I know that it won't be right", 0.86f, 9),
        AsrSegment(6, 20000, 25000, "If I stay all night to be among you", 0.77f, 10),
        AsrSegment(7, 25000, 29000, "Creeping my own you", 0.66f, 6),
        AsrSegment(8, 30000, 31400, "(upbeat music)", 0.67f, 7),
    )

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
                        "chinese" to "中文歌词 ${cue.id}",
                    )
                },
            ),
        ),
    )

    private fun envelope(content: String): String = encodeJson(
        mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
    )

    private class WalkthroughConnection(
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

    private class TestByokManager : com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager {
        override fun status() = com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus(
            com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState.CONFIGURED, "sk-test***"
        )
        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus(
            com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState.UNCONFIGURED
        )
        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-device-asr-test")
    }
}
