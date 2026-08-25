package com.example.lyriccaptioner.processing.enhancement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CaptionLocalRepairBatchPolicyTest {
    @Test
    fun splitParentsCreateOneOrderedRepairJobForEveryChild() {
        val repair = CaptionLocalRepairBatchPolicy.build(request(), firstPass(), verified = null)

        assertNotNull(repair)
        assertEquals(listOf("cue-1:1", "cue-1:2"), repair!!.cues.map { it.id })
        assertEquals("cue-1:2", repair.cues.first().siblingId)
        assertEquals("raw merged words", repair.cues.first().parentRawEnglish)
    }

    @Test
    fun responseIsAppliedAtomicallyAndKeepsOriginalParentShape() {
        val repair = CaptionLocalRepairBatchPolicy.build(request(), firstPass(), verified = null)!!
        val updated = CaptionLocalRepairBatchPolicy.apply(
            request = repair,
            response = CaptionLocalRepairResponse(
                schemaVersion = CaptionLocalRepairContract.SCHEMA_VERSION,
                jobId = repair.jobId,
                processingVersion = "two-pass-v1",
                cues = listOf(
                    CaptionLocalRepairResponseCue("cue-1:1", "First repaired line", "修复第一句"),
                    CaptionLocalRepairResponseCue("cue-1:2", "Second repaired line", "修复第二句"),
                ),
            ),
            firstPass = firstPass(),
        )

        assertEquals(CaptionProcessingLevel.TWO_PASS_COMPLETE, updated.processingLevel)
        assertEquals(listOf("First repaired line", "Second repaired line"), updated.cues.single().lines.map { it.correctedEnglish })
        assertEquals(listOf("修复第一句", "修复第二句"), updated.cues.single().lines.map { it.chinese })
    }

    @Test
    fun missingDuplicateOrReorderedChildrenAreRejected() {
        val repair = CaptionLocalRepairBatchPolicy.build(request(), firstPass(), verified = null)!!
        val missing = CaptionLocalRepairResponse(
            schemaVersion = CaptionLocalRepairContract.SCHEMA_VERSION,
            jobId = repair.jobId,
            processingVersion = "two-pass-v1",
            cues = listOf(CaptionLocalRepairResponseCue("cue-1:1", "First", "第一")),
        )

        assertThrows(CaptionEnhancementProviderException::class.java) {
            CaptionLocalRepairBatchPolicy.apply(repair, missing, firstPass())
        }
    }

    @Test
    fun batchesWithoutAutomaticSplitsSkipSecondPass() {
        val single = firstPass().copy(
            cues = listOf(firstPass().cues.single().copy(lines = firstPass().cues.single().lines.take(1))),
        )

        assertNull(CaptionLocalRepairBatchPolicy.build(request(), single, verified = null))
    }

    private fun request() = CaptionEnhancementRequest(
        jobId = "job-1",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(CaptionEnhancementRequestCue("cue-1", 0L, 3_000L, "raw merged words")),
    )

    private fun firstPass() = CaptionEnhancementResponse(
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        jobId = "job-1",
        processingVersion = "first-pass-v1",
        cues = listOf(
            CaptionEnhancementResponseCue(
                sourceId = "cue-1",
                startMs = 0L,
                endMs = 3_000L,
                lines = listOf(
                    CaptionEnhancementResponseLine("First line", "第一句"),
                    CaptionEnhancementResponseLine("Second line", "第二句"),
                ),
            ),
        ),
        processingLevel = CaptionProcessingLevel.FIRST_PASS_REVIEW_REQUIRED,
    )
}
