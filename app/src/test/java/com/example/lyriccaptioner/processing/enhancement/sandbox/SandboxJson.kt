package com.example.lyriccaptioner.processing.enhancement.sandbox

import com.example.lyriccaptioner.processing.enhancement.*

/**
 * JSON utilities for sandbox. Reuses production prompts for Phase 3 (bilingual generation).
 */
internal object SandboxJson {
    /**
     * Build request body for Phase 3 (bilingual generation).
     * Reuses production prompts (VERIFIED_LYRICS_SYSTEM_PROMPT / UNCONFIRMED_SYSTEM_PROMPT).
     */
    fun contextualEnhancementRequestBody(
        request: CaptionEnhancementRequest,
        verified: VerifiedSongLyrics?,
        unconfirmedIdentity: SongIdentityCandidate?,
        canonicalAlignments: Map<String, String>? = null,
    ): String {
        val context = if (verified != null) {
            mapOf(
                "mode" to "verified_complete_lyrics",
                "song" to mapOf(
                    "title" to verified.candidate.title,
                    "artist" to verified.candidate.artist,
                    "source_id" to verified.candidate.sourceId,
                ),
                "complete_english_lyrics" to verified.candidate.completeEnglishLyrics,
                "cue_canonical_alignments" to verified.cueCanonicalEnglish.map { (id, english) ->
                    mapOf("id" to id, "canonical_english" to english)
                },
                "request" to requestPayload(request),
            )
        } else {
            val base = mutableMapOf<String, Any>(
                "mode" to if (canonicalAlignments != null) "unconfirmed_partial_lyrics" else "unconfirmed_full_batch",
                "request" to requestPayload(request),
            )
            unconfirmedIdentity?.let {
                base["unconfirmed_candidate"] = mapOf("title" to it.title, "artist" to it.artist)
            }
            if (canonicalAlignments != null) {
                base["cue_canonical_alignments"] = canonicalAlignments.map { (id, english) ->
                    mapOf("id" to id, "canonical_english" to english)
                }
            }
            base
        }
        return completionRequest(
            systemPrompt = if (verified != null) {
                DeepSeekCaptionEnhancementProvider.VERIFIED_LYRICS_SYSTEM_PROMPT
            } else {
                DeepSeekCaptionEnhancementProvider.UNCONFIRMED_SYSTEM_PROMPT
            },
            userPayload = context,
            maxTokens = (request.cues.size * 192).coerceIn(1_024, 16_384),
        )
    }

    fun parseEnhancementResponse(body: String): CaptionEnhancementResponse {
        val root = parseAssistantJson(body)
        val cues = root.requiredArray("cues").values.map { value ->
            val item = value.asObject()
            CaptionEnhancementResponseCue(
                id = item.requiredString("id"),
                startMs = item.requiredLong("start_ms"),
                endMs = item.requiredLong("end_ms"),
                correctedEnglish = item.requiredString("corrected_english"),
                chinese = item.requiredString("chinese"),
            )
        }
        return CaptionEnhancementResponse(
            schemaVersion = root.requiredString("schema_version"),
            jobId = root.requiredString("job_id"),
            processingVersion = root.requiredString("processing_version"),
            cues = cues,
            songMatch = null,
        )
    }

    private fun parseAssistantJson(body: String): JsonObject {
        val envelope = StrictJsonParser(body).parseObjectDocument()
        val choice = envelope.requiredArray("choices").firstOrThrow().asObject()
        val content = choice.requiredObject("message").requiredString("content")
        return StrictJsonParser(content).parseObjectDocument()
    }

    private fun completionRequest(
        systemPrompt: String,
        userPayload: Any,
        maxTokens: Int,
    ): String = encodeJson(
        mapOf(
            "model" to DeepSeekCaptionEnhancementProvider.MODEL,
            "temperature" to 0,
            "max_tokens" to maxTokens,
            "stream" to false,
            "thinking" to mapOf("type" to "disabled"),
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to encodeJson(userPayload)),
            ),
        ),
    )

    private fun requestPayload(request: CaptionEnhancementRequest): Map<String, Any> {
        val cues = request.cues.map { cue ->
            val cuePayload = mutableMapOf<String, Any>(
                "id" to cue.id,
                "start_ms" to cue.startMs,
                "end_ms" to cue.endMs,
                "raw_english" to cue.rawEnglish,
            )
            cue.confidence?.let { cuePayload["confidence"] = it }
            cuePayload
        }
        val payload = mutableMapOf<String, Any>(
            "schema_version" to request.schemaVersion,
            "job_id" to request.jobId,
            "processing_version" to SandboxCaptionEnhancementProvider.PROCESSING_VERSION,
            "cues" to cues,
        )
        request.mediaDurationMs?.let { payload["media_duration_ms"] = it }
        return payload
    }
}
