package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue

/** Maps the immutable local Whisper batch onto the provider-neutral V3 request. */
class CaptionEnhancementRequestMapper {
    fun map(
        jobId: String,
        captions: List<CaptionCue>,
    ): CaptionEnhancementRequest {
        requireIdentifier(jobId, "job id")
        if (captions.isEmpty()) {
            throw CaptionEnhancementValidationException("Caption enhancement request must contain at least one cue.")
        }

        val seenIds = HashSet<String>(captions.size)
        val requestCues = captions.map { cue ->
            requireIdentifier(cue.id, "cue id")
            if (!seenIds.add(cue.id)) {
                throw CaptionEnhancementValidationException("Caption enhancement request contains duplicate cue ids.")
            }
            requireTimeline(cue.startMs, cue.endMs)
            requireText(cue.english, allowBlank = false, "raw English")

            CaptionEnhancementRequestCue(
                id = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                rawEnglish = cue.english,
            )
        }

        return CaptionEnhancementRequest(
            jobId = jobId,
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = requestCues,
        )
    }
}

internal const val MAX_CONTRACT_IDENTIFIER_LENGTH = 128
internal const val MAX_CONTRACT_TEXT_LENGTH = 10_000

internal fun requireIdentifier(value: String, label: String) {
    if (value.isEmpty() || value.length > MAX_CONTRACT_IDENTIFIER_LENGTH ||
        !value.first().isAsciiIdentifierStart() ||
        value.drop(1).any { !it.isAsciiIdentifierPart() }
    ) {
        throw CaptionEnhancementValidationException("Invalid $label.")
    }
}

private fun Char.isAsciiIdentifierStart(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

private fun Char.isAsciiIdentifierPart(): Boolean =
    isAsciiIdentifierStart() || this == '-' || this == '_' || this == '.' || this == ':'

internal fun requireTimeline(startMs: Long, endMs: Long) {
    if (startMs < 0L || endMs <= startMs) {
        throw CaptionEnhancementValidationException("Invalid cue timeline.")
    }
}

internal fun requireText(value: String, allowBlank: Boolean, label: String) {
    if (value.length > MAX_CONTRACT_TEXT_LENGTH || (!allowBlank && value.isBlank()) || !isWellFormedText(value)) {
        throw CaptionEnhancementValidationException("Invalid $label.")
    }
}

/** Reject malformed UTF-16 and invisible control characters while allowing subtitle whitespace. */
private fun isWellFormedText(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (Character.isHighSurrogate(character)) {
            if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
            index += 2
            continue
        }
        if (Character.isLowSurrogate(character)) return false
        if (Character.isISOControl(character) && character != '\n' && character != '\r' && character != '\t') return false
        index += 1
    }
    return true
}
