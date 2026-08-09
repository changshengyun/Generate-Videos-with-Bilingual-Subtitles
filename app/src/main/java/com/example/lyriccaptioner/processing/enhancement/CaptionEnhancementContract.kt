package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue

/** Provider-neutral V3 caption enhancement wire and processing contract. */
object CaptionEnhancementContract {
    const val SCHEMA_VERSION = "caption-enhancement.v3"
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
) {
    override fun toString(): String =
        "CaptionEnhancementResponse(jobId=$jobId, schemaVersion=$schemaVersion, processingVersion=$processingVersion, cueCount=${cues.size}, songMatch=${songMatch?.status})"
}

data class CaptionEnhancementResponseCue(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val correctedEnglish: String,
    val chinese: String,
)

fun interface CaptionEnhancementProvider {
    suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse
}

enum class CaptionResultSource {
    RAW_ASR,
    CLOUD_AI,
    LOCAL_FALLBACK,
}

enum class CaptionEnhancementState {
    RAW_ASR_READY,
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
) : IllegalStateException(message, cause)

class CaptionEnhancementProviderException(
    kind: CaptionEnhancementErrorKind,
    safeDetail: String,
    cause: Throwable? = null,
) : CaptionEnhancementException(
    kind = kind,
    recoverable = kind != CaptionEnhancementErrorKind.AUTHENTICATION,
    message = safeDetail,
    cause = cause,
)

class CaptionEnhancementValidationException(
    message: String,
    cause: Throwable? = null,
) : CaptionEnhancementException(
    kind = CaptionEnhancementErrorKind.INVALID_RESPONSE,
    recoverable = true,
    message = message,
    cause = cause,
)
