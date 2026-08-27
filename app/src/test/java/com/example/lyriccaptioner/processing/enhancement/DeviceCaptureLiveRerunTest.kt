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
 * Live rerun of the 2026-08-27 20:31 device capture against the REAL DeepSeek endpoint using
 * the RELAXED identification prompt (flow 1 must never return empty; exactly one most-likely
 * candidate). The four device cues are replayed through the complete pipeline and every
 * artifact (request bodies, raw responses, parsed results, flow-4 validation) is written as a
 * Markdown report under test-artifacts/ai-enhancement.
 *
 * Skips automatically when DEEPSEEK_API_KEY is missing or rejected.
 */
class DeviceCaptureLiveRerunTest {

    @Test
    fun relaxedIdentificationPromptRunsLiveAndWritesMarkdownReport() = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping live rerun", EnhancementTestEnv.isConfigured)

        val request = deviceCaptureRequest()
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

        val connections = mutableListOf<RecordingHttpConnection>()
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = EnvFileDeepSeekByokManager(),
            connectionFactory = { url ->
                RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
            },
            onDiagnosticStage = { stages.add(it) },
        )

        val report = StringBuilder()
        report.appendLine("# 放宽识别 Prompt 后的真机数据重跑（Live）")
        report.appendLine()
        report.appendLine("运行时间：${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}，" +
            "端点 `${DeepSeekCaptionEnhancementProvider.ENDPOINT}`，模型 `${DeepSeekCaptionEnhancementProvider.MODEL}`。")
        report.appendLine("输入：2026-08-27 20:31 真机抓取的 4 条 Whisper cue（job_id=device-capture-live-rerun）。")
        report.appendLine()
        report.appendLine("## 输入 cues")
        report.appendLine()
        report.appendLine("| id | 时间轴 | confidence | 英文原文 |")
        report.appendLine("|---|---|---|---|")
        request.cues.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.startMs}..${cue.endMs} | ${cue.confidence} | ${cue.rawEnglish} |")
        }

        val response = try {
            provider.enhance(request)
        } catch (error: CaptionEnhancementProviderException) {
            connections.forEachIndexed { index, connection ->
                report.appendLine()
                report.appendLine("## DeepSeek 调用 #${index + 1} 失败 status=${connection.statusCode}")
                report.appendLine()
                report.appendLine("```json")
                report.appendLine(connection.rawResponse())
                report.appendLine("```")
            }
            writeReport(report.toString())
            if (error.kind == CaptionEnhancementErrorKind.AUTHENTICATION) {
                println("DEEPSEEK_API_KEY in .env was rejected (401). Update the key and rerun.")
                assumeTrue("DeepSeek rejected the configured API key", false)
            }
            throw error
        }

        // ---- FLOW 1 (curated: prompt text + parsed result only) ----
        val candidates = DeepSeekCaptionEnhancementJson.parseSongCandidates(connections[0].rawResponse())
        report.appendLine()
        report.appendLine("## 流程 1 歌曲识别（DeepSeek 调用 #1）")
        report.appendLine()
        report.appendLine("### 使用的 Prompt")
        report.appendLine()
        report.appendLine("```text")
        report.appendLine(DeepSeekCaptionEnhancementProvider.IDENTIFICATION_SYSTEM_PROMPT)
        report.appendLine("```")
        report.appendLine()
        report.appendLine("### 模型返回结果（格式化）")
        report.appendLine()
        report.appendLine("```json")
        report.appendLine("{")
        report.appendLine("  \"candidates\": [")
        candidates.forEachIndexed { index, candidate ->
            val comma = if (index < candidates.size - 1) "," else ""
            report.appendLine("    { \"title\": \"${candidate.title}\", \"artist\": \"${candidate.artist}\" }$comma")
        }
        report.appendLine("  ]")
        report.appendLine("}")
        report.appendLine("```")

        report.appendLine()
        report.appendLine("## 流程 2 歌词检索 + 本地 DP 校验（无 LLM 调用）")
        report.appendLine()
        report.appendLine("songMatch：`${response.songMatch}`")

        // ---- FLOW 3 (curated: prompt text + result table only) ----
        val verified = response.songMatch?.status == SongMatchStatus.CONFIRMED
        report.appendLine()
        report.appendLine("## 流程 3 双语生成（DeepSeek 调用 #2，${if (verified) "已验证模式" else "未确认模式"}）")
        report.appendLine()
        report.appendLine("### 使用的 Prompt")
        report.appendLine()
        report.appendLine("```text")
        report.appendLine(if (verified) {
            DeepSeekCaptionEnhancementProvider.VERIFIED_LYRICS_SYSTEM_PROMPT
        } else {
            DeepSeekCaptionEnhancementProvider.UNCONFIRMED_SYSTEM_PROMPT
        })
        report.appendLine("```")
        report.appendLine()
        report.appendLine("### 输入要点")
        report.appendLine()
        if (verified) {
            report.appendLine("mode=`verified_complete_lyrics`；已注入歌曲 `${response.songMatch?.title}` / `${response.songMatch?.artist}`（`${response.songMatch?.source}`）的完整英文歌词与逐 cue canonical 对齐。")
        } else {
            report.appendLine("mode=`unconfirmed_full_batch`；无权威歌词，仅携带 4 条原始 cue。")
        }
        report.appendLine()
        report.appendLine("### 模型返回结果")
        report.appendLine()
        report.appendLine("| id | 时间轴 | corrected_english | chinese |")
        report.appendLine("|---|---|---|---|")
        response.cues.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.startMs}..${cue.endMs} | ${cue.correctedEnglish} | ${cue.chinese} |")
        }

        val outcome = CaptionEnhancementResponseValidator().validate(request, response, rawCaptions)
        report.appendLine()
        report.appendLine("## 流程 4 本地校验")
        report.appendLine()
        report.appendLine("state=`${outcome.state}` source=`${outcome.source}` 应用 `${outcome.captions.size}` 条。")
        report.appendLine()
        report.appendLine("| id | 英文 | 中文 |")
        report.appendLine("|---|---|---|")
        outcome.captions.forEach { cue ->
            report.appendLine("| ${cue.id} | ${cue.english} | ${cue.chinese} |")
        }

        report.appendLine()
        report.appendLine("## 结论：放宽前后对比（同一份 4 条设备数据）")
        report.appendLine()
        report.appendLine("| 阶段 | 旧严格 prompt（20:31 真机） | 新放宽 prompt（本次） |")
        report.appendLine("|---|---|---|")
        val identified = candidates.firstOrNull()
        report.appendLine("| 流程 1 歌曲识别 | 弃权返回空候选 | 识别出 \"${identified?.title ?: "—"}\" — ${identified?.artist ?: "—"} |")
        report.appendLine("| 流程 2 检索校验 | 0 命中，NOT_FOUND | `${response.songMatch}` |")
        report.appendLine("| 流程 3 双语生成 | UNCONFIRMED 保守，残句直译 | ${if (verified) "已验证模式，依据完整权威歌词纠错" else "仍为未确认模式"} |")
        report.appendLine()
        report.appendLine("识别阶段不再弃权后，下游检索+本地 DP 验证完整接管；若歌曲被确认，Whisper 错词会被权威歌词纠正。")

        val artifact = writeReport(report.toString())
        println(report.toString())
        println("markdown report saved to ${artifact.absolutePath}")

        // Hard contract: 1:1 cues, ids and timestamps intact, flow 4 applied.
        assertEquals(request.cues.map { it.id }, response.cues.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
        assertTrue(stages.contains(DeepSeekEnhancementStage.CANDIDATE_REQUEST))
        assertTrue(stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_PARSE))
        // The relaxed prompt forbids empty candidates: flow 1 must yield at least one guess.
        val flow1Response = connections[0].rawResponse()
        assertTrue(
            "flow 1 must return a non-empty candidate under the relaxed prompt, got: $flow1Response",
            !flow1Response.contains("\"candidates\":[]") && !flow1Response.contains("\"candidates\": []"),
        )
    }

    private fun writeReport(content: String): File {
        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, "device-capture-live-rerun.md")
        artifact.writeText(content, Charsets.UTF_8)
        return artifact
    }

    /** The four cues captured from the device on 2026-08-27 20:31 (live-capture-summary.txt). */
    private fun deviceCaptureRequest(): CaptionEnhancementRequest = CaptionEnhancementRequest(
        jobId = "device-capture-live-rerun",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(
            CaptionEnhancementRequestCue("whisper-0-0", 0L, 4320L, "I stopped CPR, after all it's no use", 0.85f),
            CaptionEnhancementRequestCue("whisper-1-4320", 4320L, 7360L, "The spirit was gone, we would never come to", 0.8f),
            CaptionEnhancementRequestCue("whisper-2-7360", 7360L, 11440L, "I'm pissed off you let me give you all that youth for free", 0.78f),
            CaptionEnhancementRequestCue("whisper-3-11440", 11440L, 18720L, "For so long, let me", 0.62f),
        ),
        mediaDurationMs = 18_716L,
    )

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
            flushBody()
            val stream = delegate.errorStream ?: return null
            val bytes = stream.use { it.readBytes() }
            responseBody = bytes
            return ByteArrayInputStream(bytes)
        }
    }
}
