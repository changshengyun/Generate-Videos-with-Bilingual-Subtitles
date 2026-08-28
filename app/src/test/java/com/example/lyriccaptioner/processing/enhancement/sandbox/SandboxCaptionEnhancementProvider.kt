package com.example.lyriccaptioner.processing.enhancement.sandbox

import com.example.lyriccaptioner.processing.enhancement.*
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Sandbox version of DeepSeekCaptionEnhancementProvider with new web_search-based flow.
 * This is an independent copy for testing; does not modify the production source.
 */
class SandboxCaptionEnhancementProvider(
    private val byokManager: DeepSeekByokManager,
    private val responsesClient: SandboxResponsesApiClient,
    private val searchScheduler: SandboxSearchScheduler,
    private val verifier: SongLyricsCandidateVerifier = SongLyricsCandidateVerifier(),
    private val onDiagnosticStage: (DeepSeekEnhancementStage) -> Unit = {},
) : CaptionEnhancementProvider {

    override suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse {
        currentCoroutineContext().ensureActive()

        // Phase 1+2 merged: AI with web_search finds song and lyrics
        val searchResult = if (request.cues.size >= SongLyricsCandidateVerifier.MIN_ELIGIBLE_CUES) {
            onDiagnosticStage(DeepSeekEnhancementStage.CANDIDATE_REQUEST)
            searchScheduler.schedule(request)
        } else {
            SandboxSearchResult(
                songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND),
                verifiedLyrics = null,
                unconfirmedIdentity = null,
                repairedCues = null,
                canonicalAlignments = null,
            )
        }
        currentCoroutineContext().ensureActive()

        // Phase 3: bilingual generation (reuse prompts from production)
        onDiagnosticStage(DeepSeekEnhancementStage.WHOLE_SONG_REQUEST)
        val finalBody = byokManager.withDecryptedKey { apiKey ->
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
        onDiagnosticStage(DeepSeekEnhancementStage.WHOLE_SONG_PARSE)
        return parseProviderJson { SandboxJson.parseEnhancementResponse(finalBody) }.copy(
            processingVersion = PROCESSING_VERSION,
            songMatch = searchResult.songMatch,
        )
    }

    private inline fun <T> parseProviderJson(block: () -> T): T = try {
        block()
    } catch (error: JsonParseException) {
        throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
    } catch (error: IllegalArgumentException) {
        throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
    }

    private fun providerFailure(
        kind: CaptionEnhancementErrorKind,
        cause: Throwable?,
    ): CaptionEnhancementProviderException =
        CaptionEnhancementProviderException(kind, "Sandbox DeepSeek request failed.", cause)

    companion object {
        const val PROCESSING_VERSION = "sandbox-web-search-v1"
        const val MIDDLE_ZONE_THRESHOLD = 0.50  // 50% boundary
        const val MAX_RESEARCH_ROUNDS = 5
    }
}

/** Result from the sandbox search scheduler. */
data class SandboxSearchResult(
    val songMatch: SongMatch,
    val verifiedLyrics: VerifiedSongLyrics?,
    val unconfirmedIdentity: SongIdentityCandidate?,
    val repairedCues: Map<String, String>?,
    val canonicalAlignments: Map<String, String>? = null,
    val diagnostics: List<SearchRoundDiagnostic> = emptyList(),
)
