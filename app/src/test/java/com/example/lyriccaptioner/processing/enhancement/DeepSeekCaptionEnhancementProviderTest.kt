package com.example.lyriccaptioner.processing.enhancement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekCaptionEnhancementProviderTest {
    @Test
    fun requestBodyContainsOnlyCueContractFields() {
        val body = DeepSeekCaptionEnhancementJson.requestBody(request())
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        assertTrue(body.contains("raw_english"))
        assertTrue(body.contains("Hello"))
        assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        assertTrue(!body.contains("content://"))
    }

    @Test
    fun parseResponseMapsCueAndOptionalSongMatch() {
        val body = """
            {"choices":[{"message":{"content":"{\"schema_version\":\"caption-enhancement.v3\",\"job_id\":\"job-1\",\"processing_version\":\"deepseek-chat-caption-enhancement.v1\",\"cues\":[{\"id\":\"cue-1\",\"start_ms\":0,\"end_ms\":1000,\"corrected_english\":\"Hello\",\"chinese\":\"你好\"}],\"song_match\":{\"status\":\"UNCONFIRMED\",\"title\":\"Song\",\"artist\":\"Artist\",\"confidence\":0.6,\"source\":\"model\"}}"}}]}
        """.trimIndent()
        val response = DeepSeekCaptionEnhancementJson.parseResponse(body)
        assertEquals("job-1", response.jobId)
        assertEquals("Hello", response.cues.single().correctedEnglish)
        assertEquals(SongMatchStatus.UNCONFIRMED, response.songMatch?.status)
    }

    private fun request() = CaptionEnhancementRequest(
        jobId = "job-1",
        schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
        cues = listOf(CaptionEnhancementRequestCue("cue-1", 0, 1000, "Hello")),
    )
}
