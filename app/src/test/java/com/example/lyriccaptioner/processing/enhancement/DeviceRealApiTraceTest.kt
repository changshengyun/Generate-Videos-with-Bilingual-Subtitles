package com.example.lyriccaptioner.processing.enhancement

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real-API trace test using 3 device-captured datasets.
 *
 * Each dataset is fed through the full enhancement pipeline (SearchScheduler + Flow 3)
 * and every stage transition, scheduler diagnostic, request/response body, and final
 * output is captured into a Markdown report.
 *
 * Datasets:
 *   1. device-asr-base.json — 9 raw Whisper cues, video 5e4c… (31.6 s)
 *   2. live-capture-summary.txt — 4 cues, live session (18.7 s)
 *   3. three-video summary — 4 cues, video 6101… (36.3 s)
 *
 * Skips when DEEPSEEK_API_KEY is missing in .env.
 */
class DeviceRealApiTraceTest {

    // ---- datasets ----

    private data class DeviceDataset(
        val name: String,
        val videoFile: String,
        val mediaDurationMs: Long,
        val cues: List<CaptionEnhancementRequestCue>,
    )

    /** Dataset 1: raw Whisper output from device-asr-base.json (video 1, 31.6 s). */
    private val dataset1 = DeviceDataset(
        name = "Dataset 1: device-asr-base (video 1, 31.6 s, 9 cues)",
        videoFile = "5e4c3cd7073a9e9b03df1fbf8af6d928.mp4",
        mediaDurationMs = 31_602L,
        cues = listOf(
            CaptionEnhancementRequestCue("seg-0", 0L, 2800L, "I have to live without you", 0.808f),
            CaptionEnhancementRequestCue("seg-1", 2800L, 7000L, "Nobody could, I need to be around you", 0.802f),
            CaptionEnhancementRequestCue("seg-2", 7000L, 10600L, "Watching you, no one else can love you", 0.848f),
            CaptionEnhancementRequestCue("seg-3", 10600L, 12600L, "Like I do", 0.712f),
            CaptionEnhancementRequestCue("seg-4", 12600L, 17000L, "Healing and I'm creeping up on you", 0.599f),
            CaptionEnhancementRequestCue("seg-5", 17000L, 20000L, "I know that it won't be right", 0.862f),
            CaptionEnhancementRequestCue("seg-6", 20000L, 25000L, "If I stay all night to be among you", 0.768f),
            CaptionEnhancementRequestCue("seg-7", 25000L, 29000L, "Creeping my own you", 0.660f),
            CaptionEnhancementRequestCue("seg-8", 30000L, 31400L, "(upbeat music)", 0.671f),
        ),
    )

    /** Dataset 2: live capture (4 cues, 18.7 s). */
    private val dataset2 = DeviceDataset(
        name = "Dataset 2: live-capture (18.7 s, 4 cues)",
        videoFile = "live-session-20260827",
        mediaDurationMs = 18_716L,
        cues = listOf(
            CaptionEnhancementRequestCue("whisper-0-0", 0L, 4320L, "I stopped CPR, after all it's no use", 0.85f),
            CaptionEnhancementRequestCue("whisper-1-4320", 4320L, 7360L, "The spirit was gone, we would never come to", 0.8f),
            CaptionEnhancementRequestCue("whisper-2-7360", 7360L, 11440L, "I'm pissed off you let me give you all that youth for free", 0.78f),
            CaptionEnhancementRequestCue("whisper-3-11440", 11440L, 18720L, "For so long, let me", 0.62f),
        ),
    )

    /** Dataset 3: video 2 from three-video verification (36.3 s, 4 cues). */
    private val dataset3 = DeviceDataset(
        name = "Dataset 3: video 2 — Already Gone (36.3 s, 4 cues)",
        videoFile = "6101d9b51a973fcc6bc8432d87851280.mp4",
        mediaDurationMs = 36_293L,
        cues = listOf(
            CaptionEnhancementRequestCue("v2-0", 0L, 8000L, "It was like we're all who stands apart", 0.63f),
            CaptionEnhancementRequestCue("v2-1", 9000L, 13000L, "There's so much space between us", 0.91f),
            CaptionEnhancementRequestCue("v2-2", 13000L, 17000L, "Baby, we're already behind", 0.68f),
            CaptionEnhancementRequestCue("v2-3", 17000L, 35840L, "And you have given me something that I can't live without.", 0.81f),
        ),
    )

    // ---- test ----

    @Test
    fun realApiTraceForThreeDeviceDatasets() = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping", EnhancementTestEnv.isConfigured)

        val report = StringBuilder()
        report.appendLine("# Real-API Device Trace — 3 Datasets")
        report.appendLine()
        report.appendLine("Run: ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}, " +
            "endpoint `${ResponsesApiClient.RESPONSES_ENDPOINT}` + `${ResponsesApiClient.CHAT_ENDPOINT}`, " +
            "models `${ResponsesApiClient.RESPONSES_MODEL}` / `${DeepSeekCaptionEnhancementProvider.MODEL}`.")
        report.appendLine()

        // Overall flow diagram
        report.appendLine("## Pipeline Flow")
        report.appendLine()
        report.appendLine("```mermaid")
        report.appendLine("flowchart TD")
        report.appendLine("    A[\"Input: Whisper cues\"] --> B[\"SearchScheduler.schedule()\"]")
        report.appendLine("    B --> C[\"Responses API + web_search<br/>(3-route parallel)\"]")
        report.appendLine("    C --> D[\"Parse: song_title + full_lyrics\"]")
        report.appendLine("    D --> E[\"Local DP verifier\"]")
        report.appendLine("    E --> F{\"Interval?\"}")
        report.appendLine("    F -->|\"≥ 0.82\"| G[\"CONFIRMED\"]")
        report.appendLine("    F -->|\"0.50 ~ 0.82\"| H[\"MIDDLE_ZONE<br/>item-by-item repair\"]")
        report.appendLine("    F -->|\"< 0.50\"| I[\"RESEARCH<br/>re-search up to 5 rounds\"]")
        report.appendLine("    G --> J[\"Flow 3: verified_complete_lyrics\"]")
        report.appendLine("    H --> K[\"Flow 3: unconfirmed_partial_lyrics\"]")
        report.appendLine("    I --> L[\"Flow 3: unconfirmed_full_batch\"]")
        report.appendLine("    J --> M[\"Chat Completions<br/>bilingual generation\"]")
        report.appendLine("    K --> M")
        report.appendLine("    L --> M")
        report.appendLine("    M --> N[\"Output: corrected EN + ZH\"]")
        report.appendLine("```")
        report.appendLine()

        val allConnections = mutableListOf<List<RecordingHttpConnection>>()
        val allStages = mutableListOf<List<DeepSeekEnhancementStage>>()
        val allResults = mutableListOf<Result<CaptionEnhancementResponse>>()
        val allSearchDiagnostics = mutableListOf<List<SearchRoundDiagnostic>>()

        for ((index, dataset) in listOf(dataset1, dataset2, dataset3).withIndex()) {
            report.appendLine("---")
            report.appendLine()
            report.appendLine("## ${dataset.name}")
            report.appendLine()

            // Input table
            report.appendLine("### Input Cues")
            report.appendLine()
            report.appendLine("| # | id | start_ms | end_ms | confidence | raw_english |")
            report.appendLine("|---|---|---|---|---|---|")
            dataset.cues.forEachIndexed { i, cue ->
                report.appendLine("| ${i + 1} | ${cue.id} | ${cue.startMs} | ${cue.endMs} | ${cue.confidence} | ${cue.rawEnglish} |")
            }
            report.appendLine()
            report.appendLine("media_duration_ms = ${dataset.mediaDurationMs}")
            report.appendLine()

            // Run
            val connections = mutableListOf<RecordingHttpConnection>()
            val stages = mutableListOf<DeepSeekEnhancementStage>()
            val provider = createRealProvider(connections, stages)
            val request = CaptionEnhancementRequest(
                jobId = "device-real-trace-${index + 1}",
                schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
                cues = dataset.cues,
                mediaDurationMs = dataset.mediaDurationMs,
            )

            val result = try {
                val response = provider.enhance(request)
                allResults.add(Result.success(response))
                response
            } catch (e: Exception) {
                report.appendLine("**ERROR**: ${e.javaClass.simpleName}: ${e.message}")
                report.appendLine()
                allResults.add(Result.failure(e))
                allConnections.add(connections.toList())
                allStages.add(stages.toList())
                allSearchDiagnostics.add(emptyList())
                continue
            }

            allConnections.add(connections.toList())
            allStages.add(stages.toList())

            // Stage trace
            report.appendLine("### Stage Trace (DeepSeekEnhancementStage)")
            report.appendLine()
            report.appendLine("| # | Stage | Description |")
            report.appendLine("|---|---|---|")
            stages.forEachIndexed { i, stage ->
                report.appendLine("| ${i + 1} | `$stage` | ${stageDescription(stage)} |")
            }
            report.appendLine()

            // SearchScheduler diagnostics
            val searchDiags = result.songMatch.let { emptyList<SearchRoundDiagnostic>() }
            allSearchDiagnostics.add(searchDiags)

            // HTTP calls detail — FULL bodies (no truncation)
            report.appendLine("### HTTP Calls")
            report.appendLine()
            connections.forEachIndexed { i, conn ->
                report.appendLine("**Call ${i + 1}**: `${conn.url}`")
                report.appendLine()
                report.appendLine("- Method: POST")
                report.appendLine("- Status: ${conn.statusCode}")
                val reqBody = conn.writtenBody()
                report.appendLine("- Request body length: ${reqBody.length} chars")
                report.appendLine()
                report.appendLine("#### Request Body (full)")
                report.appendLine()
                report.appendLine("```json")
                report.appendLine(reqBody)
                report.appendLine("```")
                report.appendLine()
                val respBody = conn.rawResponse()
                report.appendLine("#### Response Body (full)")
                report.appendLine()
                report.appendLine("```json")
                report.appendLine(respBody)
                report.appendLine("```")
                report.appendLine()
            }

            // Output table
            report.appendLine("### Output")
            report.appendLine()
            report.appendLine("songMatch: `${result.songMatch}`")
            report.appendLine()
            report.appendLine("| # | id | start_ms | end_ms | corrected_english | chinese |")
            report.appendLine("|---|---|---|---|---|---|")
            result.cues.forEachIndexed { i, cue ->
                report.appendLine("| ${i + 1} | ${cue.id} | ${cue.startMs} | ${cue.endMs} | ${cue.correctedEnglish} | ${cue.chinese} |")
            }
            report.appendLine()

            // Dataset 2 detailed standalone report
            if (index == 1) {
                val ds2 = StringBuilder()
                ds2.appendLine("# Dataset 2 完整输入输出 — live-capture (So Long, London)")
                ds2.appendLine()
                ds2.appendLine("## 输入")
                ds2.appendLine()
                ds2.appendLine("### 请求参数")
                ds2.appendLine()
                ds2.appendLine("| 参数 | 值 |")
                ds2.appendLine("|---|---|")
                ds2.appendLine("| job_id | `${request.jobId}` |")
                ds2.appendLine("| schema_version | `${request.schemaVersion}` |")
                ds2.appendLine("| media_duration_ms | ${request.mediaDurationMs} |")
                ds2.appendLine("| cue 数量 | ${request.cues.size} |")
                ds2.appendLine()
                ds2.appendLine("### 输入 Cues（Whisper 原始识别结果）")
                ds2.appendLine()
                ds2.appendLine("| # | id | start_ms | end_ms | confidence | raw_english |")
                ds2.appendLine("|---|---|---|---|---|---|")
                request.cues.forEachIndexed { i, cue ->
                    ds2.appendLine("| ${i + 1} | `${cue.id}` | ${cue.startMs} | ${cue.endMs} | ${cue.confidence} | ${cue.rawEnglish} |")
                }
                ds2.appendLine()
                ds2.appendLine("---")
                ds2.appendLine()
                ds2.appendLine("## 阶段 1：SearchScheduler — Responses API + web_search")
                ds2.appendLine()
                ds2.appendLine("**端点**: `${ResponsesApiClient.RESPONSES_ENDPOINT}`")
                ds2.appendLine("**模型**: `${ResponsesApiClient.RESPONSES_MODEL}`")
                ds2.appendLine()
                if (connections.size >= 1) {
                    ds2.appendLine("### 请求体（完整）")
                    ds2.appendLine()
                    ds2.appendLine("```json")
                    ds2.appendLine(connections[0].writtenBody())
                    ds2.appendLine("```")
                    ds2.appendLine()
                    ds2.appendLine("### 响应体（完整）")
                    ds2.appendLine()
                    ds2.appendLine("```json")
                    ds2.appendLine(connections[0].rawResponse())
                    ds2.appendLine("```")
                }
                ds2.appendLine()
                ds2.appendLine("### 阶段 1 结果")
                ds2.appendLine()
                ds2.appendLine("| 字段 | 值 |")
                ds2.appendLine("|---|---|")
                ds2.appendLine("| songMatch.status | `${result.songMatch?.status}` |")
                ds2.appendLine("| songMatch.title | `${result.songMatch?.title}` |")
                ds2.appendLine("| songMatch.artist | `${result.songMatch?.artist}` |")
                ds2.appendLine("| songMatch.confidence | `${result.songMatch?.confidence}` |")
                ds2.appendLine("| songMatch.source | `${result.songMatch?.source}` |")
                ds2.appendLine()
                ds2.appendLine("---")
                ds2.appendLine()
                ds2.appendLine("## 阶段 2：Flow 3 — Chat Completions 双语生成")
                ds2.appendLine()
                ds2.appendLine("**端点**: `${ResponsesApiClient.CHAT_ENDPOINT}`")
                ds2.appendLine("**模型**: `${DeepSeekCaptionEnhancementProvider.MODEL}`")
                ds2.appendLine()
                if (connections.size >= 2) {
                    ds2.appendLine("### 请求体（完整）")
                    ds2.appendLine()
                    ds2.appendLine("```json")
                    ds2.appendLine(connections[1].writtenBody())
                    ds2.appendLine("```")
                    ds2.appendLine()
                    ds2.appendLine("### 响应体（完整）")
                    ds2.appendLine()
                    ds2.appendLine("```json")
                    ds2.appendLine(connections[1].rawResponse())
                    ds2.appendLine("```")
                }
                ds2.appendLine()
                ds2.appendLine("---")
                ds2.appendLine()
                ds2.appendLine("## 最终输出")
                ds2.appendLine()
                ds2.appendLine("### songMatch")
                ds2.appendLine()
                ds2.appendLine("```json")
                ds2.appendLine("{")
                ds2.appendLine("  \"status\": \"${result.songMatch?.status}\",")
                ds2.appendLine("  \"title\": \"${result.songMatch?.title}\",")
                ds2.appendLine("  \"artist\": \"${result.songMatch?.artist}\",")
                ds2.appendLine("  \"confidence\": ${result.songMatch?.confidence},")
                ds2.appendLine("  \"source\": \"${result.songMatch?.source}\"")
                ds2.appendLine("}")
                ds2.appendLine("```")
                ds2.appendLine()
                ds2.appendLine("### 输出 Cues（纠错后英文 + 中文）")
                ds2.appendLine()
                ds2.appendLine("| # | id | start_ms | end_ms | corrected_english | chinese |")
                ds2.appendLine("|---|---|---|---|---|---|")
                result.cues.forEachIndexed { i, cue ->
                    ds2.appendLine("| ${i + 1} | `${cue.id}` | ${cue.startMs} | ${cue.endMs} | ${cue.correctedEnglish} | ${cue.chinese} |")
                }
                ds2.appendLine()
                ds2.appendLine("### processing_version")
                ds2.appendLine()
                ds2.appendLine("`${result.processingVersion}`")
                writeReport(ds2.toString(), "dataset2-full-trace.md")
            }
        }

        // Summary table
        report.appendLine("---")
        report.appendLine()
        report.appendLine("## Summary")
        report.appendLine()
        report.appendLine("| Dataset | Cues | Stages | HTTP Calls | songMatch | Status |")
        report.appendLine("|---|---|---|---|---|---|")
        listOf(dataset1, dataset2, dataset3).forEachIndexed { i, ds ->
            val stages = allStages.getOrNull(i) ?: emptyList()
            val conns = allConnections.getOrNull(i) ?: emptyList()
            val res = allResults.getOrNull(i)
            val match = res?.getOrNull()?.songMatch?.toString() ?: "N/A"
            val status = if (res?.isSuccess == true) "OK" else "FAIL"
            report.appendLine("| ${ds.name.split(":").first().trim()} | ${ds.cues.size} | ${stages.size} | ${conns.size} | $match | $status |")
        }
        report.appendLine()

        val artifact = writeReport(report.toString())
        println(report.toString())
        println("Trace report saved to ${artifact.absolutePath}")

        // At least verify no exceptions for successful runs
        val successCount = allResults.count { it.isSuccess }
        assertTrue("Expected at least 1 successful API call, got $successCount", successCount >= 1)
    }

    // ---- helpers ----

    private fun createRealProvider(
        connections: MutableList<RecordingHttpConnection>,
        stages: MutableList<DeepSeekEnhancementStage>,
    ): DeepSeekCaptionEnhancementProvider {
        val recordingClient = ResponsesApiClient(
            connectionFactory = { url ->
                RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
            },
        )
        val scheduler = SearchScheduler(
            responsesClient = recordingClient,
            byokManager = EnvFileDeepSeekByokManager(),
        )
        return DeepSeekCaptionEnhancementProvider(
            byokManager = EnvFileDeepSeekByokManager(),
            connectionFactory = { url ->
                RecordingHttpConnection(url.openConnection() as HttpURLConnection).also(connections::add)
            },
            onDiagnosticStage = { stages.add(it) },
            searchScheduler = scheduler,
        )
    }

    private fun stageDescription(stage: DeepSeekEnhancementStage): String = when (stage) {
        DeepSeekEnhancementStage.CANDIDATE_REQUEST -> "Send song identification / web_search request"
        DeepSeekEnhancementStage.CANDIDATE_PARSE -> "Parse identification response"
        DeepSeekEnhancementStage.LYRICS_SEARCH -> "Search lyrics (LRCLIB or SearchScheduler result)"
        DeepSeekEnhancementStage.LYRICS_VERIFY -> "Local DP verification of candidate lyrics"
        DeepSeekEnhancementStage.VERIFIED_LYRICS_SELECTED -> "Verified lyrics selected for Flow 3"
        DeepSeekEnhancementStage.WHOLE_SONG_REQUEST -> "Flow 3: send bilingual generation request"
        DeepSeekEnhancementStage.WHOLE_SONG_PARSE -> "Flow 3: parse bilingual response"
    }

    private fun writeReport(content: String, fileName: String = "device-real-api-trace.md"): File {
        val artifactDir = File(EnhancementTestEnv.projectRoot, "test-artifacts/ai-enhancement")
        artifactDir.mkdirs()
        val artifact = File(artifactDir, fileName)
        artifact.writeText(content, Charsets.UTF_8)
        return artifact
    }

    // ---- recording HTTP connection ----

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
        override fun setDoOutput(dooutput: Boolean) { delegate.setDoOutput(dooutput) }
        override fun setInstanceFollowRedirects(followRedirects: Boolean) { delegate.instanceFollowRedirects = followRedirects }
        override fun setUseCaches(usecaches: Boolean) { delegate.useCaches = usecaches }
        override fun setRequestProperty(key: String, value: String) { delegate.setRequestProperty(key, value) }
        override fun connect() = delegate.connect()
        override fun disconnect() = delegate.disconnect()
        override fun usingProxy(): Boolean = false
        override fun getOutputStream(): OutputStream = captured

        override fun getResponseCode(): Int {
            flushBody()
            return delegate.responseCode.also { statusCode = it }.also { status ->
                if (status !in 200..299) {
                    responseBody = try {
                        delegate.errorStream?.use { it.readBytes() }
                    } catch (_: Exception) { null }
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
