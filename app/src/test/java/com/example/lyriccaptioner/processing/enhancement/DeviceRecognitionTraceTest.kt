package com.example.lyriccaptioner.processing.enhancement

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test sample 2: the recognition run captured directly from the physical device.
 *
 * The app auto-records every recognition into cache/ai-trace.jsonl (schema ai-trace.v1,
 * single-session, sanitized). The file was pulled via `run-as` from device fcf4b0cb on
 * 2026-08-27 and archived at test-artifacts/device-capture/device-ai-trace.jsonl.
 *
 * Captured run (job caption-533671023, 2026-08-26 12:49 CST, 16.5s total):
 *   Whisper produced 3 parent cues (0-17840 / 17840-28480 / 28480-38880 ms).
 *   Flow 1 (ai_1_song_identity): IDENTITY_REQUEST -> IDENTITY_PARSE -> identified=false.
 *   Flow 2 (ai_2_batch_enhancement): AI_ONLY_REQUEST -> AI_ONLY_PARSE -> validated.
 *   Auto split: parent whisper-0-0 split into two lines -> 4 final cues.
 *   Lyrics search skipped (song_identity_missing) -> final selection AI_2 -> published.
 */
class DeviceRecognitionTraceTest {

    private val traceLines: List<String> by lazy {
        File(EnhancementTestEnv.projectRoot, "test-artifacts/device-capture/device-ai-trace.jsonl")
            .readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
    }

    @Test
    fun capturedDeviceRunKeepsTheCompleteEventSequence() {
        assertEquals(30, traceLines.size)
        assertTrue(traceLines.all { it.contains("\"schema_version\":\"ai-trace.v1\"") })
        assertTrue(traceLines.all { it.contains("\"job_id\":\"caption-533671023\"") })

        val events = traceLines.map { line ->
            Regex("\"event\":\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: ""
        }
        assertEquals(
            listOf(
                "workflow_started",
                "workflow_stage_changed",
                "workflow_stage_changed",
                "whisper_batch_ready",
                "enhancement_state_changed",
                "enhancement_state_changed",
                "enhancement_state_changed",
                "ai_call_started",
                "provider_stage",
                "provider_stage",
                "ai_call_succeeded",
                "song_identity_completed",
                "enhancement_state_changed",
                "ai_call_started",
                "provider_stage",
                "provider_stage",
                "ai_validation_succeeded",
                "ai_call_succeeded",
                "ai_2_batch_completed",
                "enhancement_state_changed",
                "auto_split_completed",
                "enhancement_state_changed",
                "provider_stage",
                "lyrics_search_skipped",
                "ai_2_selected",
                "final_selection",
                "enhancement_state_changed",
                "enhancement_state_changed",
                "workflow_published",
                "workflow_finished",
            ),
            events,
        )
    }

    @Test
    fun whisperBatchReadyCarriesTheThreeDeviceCues() {
        val batchLine = traceLines.single { it.contains("\"event\":\"whisper_batch_ready\"") }
        assertTrue(batchLine.contains("\"parent_cue_count\":3"))
        assertTrue(batchLine.contains("\"cue_id\":\"whisper-0-0\", \"start_ms\":0, \"end_ms\":17840"))
        assertTrue(batchLine.contains("\"cue_id\":\"whisper-1-17840\", \"start_ms\":17840, \"end_ms\":28480"))
        assertTrue(batchLine.contains("\"cue_id\":\"whisper-2-28480\", \"start_ms\":28480, \"end_ms\":38880"))
    }

    @Test
    fun flowOneCouldNotIdentifyTheSongSoLyricsSearchWasSkipped() {
        val identity = traceLines.single { it.contains("\"event\":\"song_identity_completed\"") }
        assertTrue(identity.contains("\"identified\":false"))
        assertTrue(identity.contains("\"title\":null"))

        val skipped = traceLines.single { it.contains("\"event\":\"lyrics_search_skipped\"") }
        assertTrue(skipped.contains("\"reason\":\"song_identity_missing\""))
    }

    @Test
    fun autoSplitTurnedThreeParentsIntoFourPublishedCaptions() {
        val split = traceLines.single { it.contains("\"event\":\"auto_split_completed\"") }
        assertTrue(split.contains("\"before_parent_cue_count\":3"))
        assertTrue(split.contains("\"after_cue_count\":4"))
        assertTrue(split.contains("\"cue_id\":\"whisper-0-0:1\", \"parent_source_id\":\"whisper-0-0\", \"line_index\":0, \"start_ms\":0, \"end_ms\":9276"))
        assertTrue(split.contains("\"cue_id\":\"whisper-0-0:2\", \"parent_source_id\":\"whisper-0-0\", \"line_index\":1, \"start_ms\":9360, \"end_ms\":17840"))

        val published = traceLines.single { it.contains("\"event\":\"workflow_published\"") }
        assertTrue(published.contains("\"source\":\"CLOUD_AI\""))
        assertTrue(published.contains("\"processing_level\":\"AI_ONLY_COMPLETE\""))
        assertTrue(published.contains("\"caption_count\":4"))
        assertTrue(published.contains("\"whisper-0-0:1\", \"whisper-0-0:2\", \"whisper-1-17840\", \"whisper-2-28480\""))

        val finished = traceLines.single { it.contains("\"event\":\"workflow_finished\"") }
        assertTrue(finished.contains("\"outcome\":\"success\""))
    }
}
