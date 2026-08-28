package com.example.lyriccaptioner.processing.enhancement.sandbox

import com.example.lyriccaptioner.processing.enhancement.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Sandbox test for three real videos. Runs the new web_search-based flow.
 * Generates standard format report with search diagnostics.
 * Calls scheduler + Flow 3 directly to capture full diagnostics in one pass.
 */
class SandboxThreeVideoTest {

    private data class VideoTestData(
        val label: String,
        val request: CaptionEnhancementRequest,
        val searchResult: SandboxSearchResult,
        val response: CaptionEnhancementResponse,
    )

    @Test
    fun threeVideoSandboxTest() = runBlocking {
        assumeTrue("DEEPSEEK_API_KEY missing in .env; skipping sandbox test", EnhancementTestEnv.isConfigured)

        val byokManager = EnvFileDeepSeekByokManager()
        val responsesClient = SandboxResponsesApiClient()
        val verifier = SongLyricsCandidateVerifier()
        val searchScheduler = SandboxSearchScheduler(responsesClient, verifier, byokManager)

        val videos = listOf(
            "视频1（9条字幕）" to buildVideo1Request(),
            "视频2（4条字幕）" to buildVideo2Request(),
            "视频3（5条字幕）" to buildVideo3Request(),
        )

        val results = videos.map { (label, request) ->
            try {
                // Step 1: scheduler (Flow 1+2 with diagnostics)
                val searchResult = searchScheduler.schedule(request)
                // Step 2: Flow 3 bilingual generation using search result
                val responseBody = byokManager.withDecryptedKey { apiKey ->
                    responsesClient.executeChatRequest(
                        apiKey = apiKey,
                        requestBody = SandboxJson.contextualEnhancementRequestBody(
                            request = request,
                            verified = searchResult.verifiedLyrics,
                            unconfirmedIdentity = searchResult.unconfirmedIdentity,
                            canonicalAlignments = searchResult.canonicalAlignments,
                        ),
                    )
                }
                val parsed = SandboxJson.parseEnhancementResponse(responseBody).copy(
                    processingVersion = SandboxCaptionEnhancementProvider.PROCESSING_VERSION,
                    songMatch = searchResult.songMatch,
                )
                VideoTestData(label, request, searchResult, parsed)
            } catch (error: Exception) {
                println("[$label] FAILED: ${error.message}")
                // Create fallback data so the report still includes this video
                val fallbackSearch = SandboxSearchResult(
                    songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND),
                    verifiedLyrics = null,
                    unconfirmedIdentity = null,
                    repairedCues = null,
                )
                val fallbackResponse = CaptionEnhancementResponse(
                    schemaVersion = request.schemaVersion,
                    jobId = request.jobId,
                    processingVersion = "error-fallback",
                    cues = request.cues.map { cue ->
                        CaptionEnhancementResponseCue(
                            id = cue.id, startMs = cue.startMs, endMs = cue.endMs,
                            correctedEnglish = cue.rawEnglish, chinese = "(处理失败)",
                        )
                    },
                    songMatch = null,
                )
                VideoTestData(label, request, fallbackSearch, fallbackResponse)
            }
        }

        generateSandboxReport(results)
    }

    private fun buildVideo1Request(): CaptionEnhancementRequest =
        CaptionEnhancementRequest(
            schemaVersion = "caption-enhancement.v3",
            jobId = UUID.randomUUID().toString(),
            cues = listOf(
                CaptionEnhancementRequestCue("v1-0", 2000, 5500, "Creeping my own you", 0.78f),
                CaptionEnhancementRequestCue("v1-1", 5500, 8500, "I know that it won't be long", 0.85f),
                CaptionEnhancementRequestCue("v1-2", 8500, 12000, "I guess I like it this way", 0.82f),
                CaptionEnhancementRequestCue("v1-3", 12000, 15500, "And I don't wanna fight", 0.80f),
                CaptionEnhancementRequestCue("v1-4", 15500, 19000, "I'm not the kind of guy", 0.83f),
                CaptionEnhancementRequestCue("v1-5", 19000, 22500, "Who's gonna hurt you", 0.79f),
                CaptionEnhancementRequestCue("v1-6", 22500, 26000, "Or leave you behind", 0.84f),
                CaptionEnhancementRequestCue("v1-7", 26000, 29500, "I'm creeping up on you", 0.81f),
                CaptionEnhancementRequestCue("v1-8", 29500, 32000, "(upbeat music)", 0.65f),
            ),
            mediaDurationMs = 32000,
        )

    private fun buildVideo2Request(): CaptionEnhancementRequest =
        CaptionEnhancementRequest(
            schemaVersion = "caption-enhancement.v3",
            jobId = UUID.randomUUID().toString(),
            cues = listOf(
                CaptionEnhancementRequestCue("v2-0", 1000, 4000, "For so long let me", 0.62f),
                CaptionEnhancementRequestCue("v2-1", 4000, 7000, "I saw the lights go out", 0.75f),
                CaptionEnhancementRequestCue("v2-2", 7000, 10000, "In your eyes in your eyes", 0.78f),
                CaptionEnhancementRequestCue("v2-3", 10000, 13000, "So long London", 0.80f),
            ),
            mediaDurationMs = 13000,
        )

    private fun buildVideo3Request(): CaptionEnhancementRequest =
        CaptionEnhancementRequest(
            schemaVersion = "caption-enhancement.v3",
            jobId = UUID.randomUUID().toString(),
            cues = listOf(
                CaptionEnhancementRequestCue("v3-0", 500, 3500, "Take your eyes off of me", 0.72f),
                CaptionEnhancementRequestCue("v3-1", 3500, 6500, "So I can leave", 0.68f),
                CaptionEnhancementRequestCue("v3-2", 6500, 9500, "I've been trying to forget you", 0.75f),
                CaptionEnhancementRequestCue("v3-3", 9500, 12500, "But you're always on my mind", 0.77f),
                CaptionEnhancementRequestCue("v3-4", 12500, 15500, "Eyes on me", 0.80f),
            ),
            mediaDurationMs = 15500,
        )

    private fun generateSandboxReport(videos: List<VideoTestData>) {
        val report = buildString {
            appendLine("# 沙箱三视频测试报告（新流程版 + 搜索诊断）")
            appendLine()
            appendLine("运行时间：${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine("端点：Responses API (`${SandboxResponsesApiClient.RESPONSES_ENDPOINT}`)")
            appendLine("模型：`${SandboxResponsesApiClient.RESPONSES_MODEL}`")
            appendLine()

            appendLine("## 整体流程")
            appendLine("```")
            appendLine("输入字幕 → 流程1+2（AI带官方联网搜索找歌取词）")
            appendLine("         → 本地DP比对（唯一确认闸门）")
            appendLine("         → 三区间分流：")
            appendLine("             CONFIRMED（≥0.82/0.76）→ 确认歌曲")
            appendLine("             MIDDLE_ZONE（50%~门槛）→ 逐条修复+保守翻译")
            appendLine("             RESEARCH（<50%）→ 带失败反馈重搜（上限5轮）")
            appendLine("         → 流程3（双语生成）→ 流程4（合同校验）→ 落屏")
            appendLine("```")
            appendLine()

            videos.forEach { data ->
                appendLine("## ${data.label}")
                appendLine()
                appendLine("### 基本信息")
                appendLine("| 字段 | 值 |")
                appendLine("|---|---|")
                appendLine("| 字幕条数 | ${data.request.cues.size} |")
                appendLine("| 处理版本 | ${data.response.processingVersion} |")
                appendLine("| 歌曲状态 | ${data.searchResult.songMatch.status} |")
                appendLine("| 歌曲 | ${data.searchResult.songMatch.title ?: "N/A"} - ${data.searchResult.songMatch.artist ?: "N/A"} |")
                appendLine("| 置信度 | ${data.searchResult.songMatch.confidence ?: "N/A"} |")
                appendLine()

                if (data.searchResult.diagnostics.isNotEmpty()) {
                    appendLine("### 搜索诊断")
                    appendLine("| 轮次 | 识别歌曲 | 匹配率 | 分类 | 搜索次数 |")
                    appendLine("|---|---|---|---|---|")
                    data.searchResult.diagnostics.forEach { diag ->
                        val song = if (diag.parsedSongTitle != null) "${diag.parsedSongTitle} - ${diag.parsedArtist}" else "(解析失败)"
                        val rate = diag.rawMatchRate?.let { "%.1f%%".format(it * 100) } ?: "N/A"
                        appendLine("| R${diag.round} | $song | $rate | ${diag.intervalClassification} | ${diag.searchActionCount} |")
                    }
                    appendLine()
                }

                appendLine("### 输出字幕")
                data.response.cues.forEach { cue ->
                    appendLine("- **${cue.id}**: ${cue.correctedEnglish} / ${cue.chinese}")
                }
                appendLine()
            }

            appendLine("## 汇总")
            appendLine("| 视频 | 字幕数 | 搜索轮次 | 最终分类 | 歌曲 | 状态 |")
            appendLine("|---|---|---|---|---|---|")
            videos.forEach { data ->
                val rounds = data.searchResult.diagnostics.size
                val lastClass = data.searchResult.diagnostics.lastOrNull()?.intervalClassification ?: "N/A"
                appendLine("| ${data.label} | ${data.request.cues.size} | $rounds | $lastClass | ${data.searchResult.songMatch.title ?: "N/A"} | ${data.searchResult.songMatch.status} |")
            }
        }

        val reportFile = java.io.File("test-artifacts/ai-enhancement/sandbox-three-video-report.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(report)
        println("沙箱报告已生成：${reportFile.absolutePath}")
    }
}
