package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue

/** Provider-neutral V3 caption enhancement wire and processing contract. */
object CaptionEnhancementContract {
    const val SCHEMA_VERSION = "caption-enhancement.v5"
}

data class CaptionEnhancementRequest(
    val jobId: String,
    val schemaVersion: String,
    val cues: List<CaptionEnhancementRequestCue>,
) {
    override fun toString(): String =
        "CaptionEnhancementRequest(jobId=$jobId, schemaVersion=$schemaVersion, cueCount=${cues.size})"
}

data class CaptionEnhancementRequestCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val rawEnglish: String,
)

data class CaptionEnhancementResponse(
    val schemaVersion: String,
    val jobId: String,
    val processingVersion: String,
    val cues: List<CaptionEnhancementResponseCue>,
    val songMatch: SongMatch? = null,
    val processingLevel: CaptionProcessingLevel = CaptionProcessingLevel.LEGACY_UNKNOWN,
) {
    override fun toString(): String =
        "CaptionEnhancementResponse(jobId=$jobId, schemaVersion=$schemaVersion, processingVersion=$processingVersion, cueCount=${cues.size}, songMatch=${songMatch?.status})"
}

data class CaptionEnhancementResponseCue(
    val sourceId: String,
    val startMs: Long,
    val endMs: Long,
    val lines: List<CaptionEnhancementResponseLine>,
)

data class CaptionEnhancementResponseLine(
    val correctedEnglish: String,
    val chinese: String,
)

fun interface CaptionEnhancementProvider {
    suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse
}

interface StagedCaptionEnhancementProvider : CaptionEnhancementProvider {
    suspend fun enhance(
        request: CaptionEnhancementRequest,
        onStateChanged: (CaptionEnhancementState) -> Unit,
    ): CaptionEnhancementResponse

    override suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse =
        enhance(request, {})
}

/** Application-facing boundary for one complete, atomic caption enhancement batch. */
interface CaptionEnhancementService {
    suspend fun enhance(
        jobId: String,
        captions: List<CaptionCue>,
        onStateChanged: (CaptionEnhancementState) -> Unit = {},
    ): CaptionEnhancementOutcome
}

enum class CaptionResultSource {
    RAW_ASR,
    CLOUD_AI,
    LOCAL_FALLBACK,
}

enum class CaptionProcessingLevel {
    TWO_PASS_COMPLETE,
    FIRST_PASS_REVIEW_REQUIRED,
    LOCAL_FALLBACK,
    LEGACY_UNKNOWN,
}

enum class CaptionEnhancementState {
    RAW_ASR_READY,
    SONG_IDENTIFYING,
    LYRICS_RETRIEVING,
    FIRST_PASS_ENHANCING,
    AUTO_SPLITTING,
    LOCAL_REPAIRING,
    FINAL_VALIDATING,
    CLOUD_PENDING,
    CLOUD_VALIDATING,
    CLOUD_APPLIED,
    LOCAL_FALLBACK_APPLIED,
    CANCELLED,
}

enum class CaptionEnhancementErrorKind {
    OFFLINE,
    CONNECTION,
    TIMEOUT,
    RETRYABLE_SERVER,
    INVALID_RESPONSE,
    AUTHENTICATION,
    LOCAL_TRANSLATION,
    UNKNOWN,
}

enum class SongMatchStatus {
    CONFIRMED,
    UNCONFIRMED,
    NOT_FOUND,
}

data class SongMatch(
    val status: SongMatchStatus,
    val title: String? = null,
    val artist: String? = null,
    val confidence: Float? = null,
    val source: String? = null,
)

data class CaptionEnhancementOutcome(
    val captions: List<CaptionCue>,
    val source: CaptionResultSource,
    val state: CaptionEnhancementState,
    val processingVersion: String? = null,
    val errorKind: CaptionEnhancementErrorKind? = null,
    val songMatch: SongMatch? = null,
    val processingLevel: CaptionProcessingLevel = CaptionProcessingLevel.LEGACY_UNKNOWN,
) {
    override fun toString(): String =
        "CaptionEnhancementOutcome(source=$source, state=$state, cueCount=${captions.size}, processingVersion=$processingVersion, errorKind=$errorKind, songMatch=${songMatch?.status})"
}

data class CaptionEnhancementDiagnostics(
    val jobId: String,
    val state: CaptionEnhancementState,
    val cueCount: Int,
    val errorKind: CaptionEnhancementErrorKind?,
) {
    override fun toString(): String =
        "CaptionEnhancementDiagnostics(jobId=$jobId, state=$state, cueCount=$cueCount, errorKind=$errorKind)"
}

open class CaptionEnhancementException(
    val kind: CaptionEnhancementErrorKind,
    val recoverable: Boolean,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(sanitizeExceptionMessage(message)) {
    // Causes may contain request bodies, credentials, paths, or provider responses. They are
    // intentionally not retained on the public exception object.
    @Suppress("UNUSED_PARAMETER")
    private val sanitizedCause: Throwable? = cause?.let { null }
}

class CaptionEnhancementProviderException(
    kind: CaptionEnhancementErrorKind,
    safeDetail: String,
    cause: Throwable? = null,
) : CaptionEnhancementException(
    kind = kind,
    recoverable = kind in setOf(
        CaptionEnhancementErrorKind.OFFLINE,
        CaptionEnhancementErrorKind.CONNECTION,
        CaptionEnhancementErrorKind.TIMEOUT,
        CaptionEnhancementErrorKind.RETRYABLE_SERVER,
        CaptionEnhancementErrorKind.INVALID_RESPONSE,
    ),
    message = "Caption enhancement provider request failed.",
    cause = null,
)

class CaptionEnhancementValidationException(
    message: String,
    cause: Throwable? = null,
) : CaptionEnhancementException(
    kind = CaptionEnhancementErrorKind.INVALID_RESPONSE,
    recoverable = true,
    message = "Caption enhancement response validation failed.",
    cause = cause,
)

private fun sanitizeExceptionMessage(value: String): String = value
    .replace(Regex("(?i)authorization\\s*:\\s*bearer\\s+\\S+"), "Authorization: [REDACTED]")
    .replace(Regex("(?i)bearer\\s+\\S+"), "Bearer [REDACTED]")
    .replace(Regex("sk-[A-Za-z0-9_-]+"), "[REDACTED_KEY]")
    .replace(Regex("(?:content|file)://\\S+"), "[REDACTED_PATH]")
