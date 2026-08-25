package com.example.lyriccaptioner.processing

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionWorkflowStage
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementOutcome
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementProviderException
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteCaptionWorkflowTest {
    @Test
    fun preflightRequiresVideoLocalRuntimeAndConfiguredKey() {
        assertEquals("请先导入视频。", preflight(hasVideo = false).blockingMessage())
        assertEquals("本地 Whisper 模型尚未就绪。", preflight(localReady = false).blockingMessage())
        assertEquals(
            "请先在\"AI 服务配置\"中保存并验证 DeepSeek API Key。",
            preflight(keyConfigured = false).blockingMessage(),
        )
        assertEquals("字幕生成正在进行中。", preflight(alreadyRunning = true).blockingMessage())
        assertNull(preflight().blockingMessage())
    }

    @Test
    fun oneRunCallsAsrThenAiExactlyOnceWithoutExposingRawBatch() = runBlocking {
        val events = mutableListOf<String>()
        var asrCalls = 0
        var aiCalls = 0

        val outcome = CompleteCaptionWorkflowRunner().run(
            recognize = {
                asrCalls += 1
                events += "asr"
                rawCues()
            },
            enhance = { captions, _ ->
                aiCalls += 1
                events += "ai:${captions.single().id}"
                cloudOutcome(captions)
            },
            onStageChanged = { events += "stage:$it" },
            onRecognitionStatus = {},
            onEnhancementState = {},
        )

        assertEquals(1, asrCalls)
        assertEquals(1, aiCalls)
        assertEquals(CaptionResultSource.CLOUD_AI, outcome.source)
        assertEquals(
            listOf(
                "stage:LOCAL_RECOGNIZING",
                "asr",
                "stage:AI_ENHANCING",
                "ai:cue-1",
            ),
            events,
        )
    }

    @Test
    fun asrFailureNeverStartsAi() {
        var aiCalls = 0

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                CompleteCaptionWorkflowRunner().run(
                    recognize = { throw IllegalStateException("native failure") },
                    enhance = { captions, _ ->
                        aiCalls += 1
                        cloudOutcome(captions)
                    },
                    onStageChanged = {},
                    onRecognitionStatus = {},
                    onEnhancementState = {},
                )
            }
        }

        assertEquals(0, aiCalls)
    }

    @Test
    fun fallbackOutcomeIsReturnedWithoutChangingItsSource() = runBlocking {
        val fallback = CaptionEnhancementOutcome(
            captions = rawCues(),
            source = CaptionResultSource.LOCAL_FALLBACK,
            state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
            errorKind = CaptionEnhancementErrorKind.OFFLINE,
        )

        val result = runWorkflow(enhance = { fallback })

        assertEquals(fallback, result)
    }

    @Test
    fun aiFailureDoesNotExposeRawBatch() {
        val events = mutableListOf<String>()

        assertThrows(CaptionEnhancementProviderException::class.java) {
            runBlocking {
                runWorkflow(
                    enhance = {
                        events += "ai"
                        throw CaptionEnhancementProviderException(
                            CaptionEnhancementErrorKind.AUTHENTICATION,
                            "auth",
                        )
                    },
                )
            }
        }

        assertEquals(listOf("ai"), events)
    }

    @Test
    fun cancellationAfterAsrDoesNotProduceAnEnhancementOutcome() {
        val stages = mutableListOf<CaptionWorkflowStage>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                runWorkflow(
                    onStage = { stages += it },
                    enhance = { throw CancellationException("cancelled") },
                )
            }
        }

        assertTrue(stages.contains(CaptionWorkflowStage.LOCAL_RECOGNIZING))
        assertTrue(stages.contains(CaptionWorkflowStage.AI_ENHANCING))
    }

    private fun preflight(
        hasVideo: Boolean = true,
        localReady: Boolean = true,
        keyConfigured: Boolean = true,
        alreadyRunning: Boolean = false,
    ) = CompleteCaptionWorkflowPreflight(hasVideo, localReady, keyConfigured, alreadyRunning)

    private suspend fun runWorkflow(
        onStage: (CaptionWorkflowStage) -> Unit = {},
        enhance: suspend (List<CaptionCue>) -> CaptionEnhancementOutcome,
    ): CaptionEnhancementOutcome = CompleteCaptionWorkflowRunner().run(
        recognize = { rawCues() },
        enhance = { captions, _ -> enhance(captions) },
        onStageChanged = onStage,
        onRecognitionStatus = {},
        onEnhancementState = {},
    )

    private fun rawCues() = listOf(
        CaptionCue("cue-1", 0L, 1_000L, "hello", "", 0.9f),
    )

    private fun cloudOutcome(captions: List<CaptionCue>) = CaptionEnhancementOutcome(
        captions = captions.map { it.copy(chinese = "你好") },
        source = CaptionResultSource.CLOUD_AI,
        state = CaptionEnhancementState.CLOUD_APPLIED,
    )
}
