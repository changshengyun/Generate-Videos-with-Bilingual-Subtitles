package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionCueSplitPolicy
import com.example.lyriccaptioner.model.CaptionSplitLine

/** Performs the all-or-nothing checks required before applying a provider response. */
class CaptionEnhancementResponseValidator {
    fun validate(
        request: CaptionEnhancementRequest,
        response: CaptionEnhancementResponse,
        rawCaptions: List<CaptionCue>,
    ): CaptionEnhancementOutcome {
        validateRequest(request, rawCaptions)

        if (response.schemaVersion != CaptionEnhancementContract.SCHEMA_VERSION ||
            response.schemaVersion != request.schemaVersion
        ) {
            reject("Unexpected response schema.")
        }
        if (response.jobId != request.jobId) reject("Response job does not match request.")
        requireIdentifier(response.jobId, "job id")
        requireText(response.processingVersion, allowBlank = false, "processing version")
        if (response.processingVersion.length > MAX_CONTRACT_IDENTIFIER_LENGTH) {
            reject("Processing version is too long.")
        }

        if (response.cues.size != request.cues.size) reject("Response cue count does not match request.")
        val expectedIds = request.cues.map { it.id }
        val responseIds = response.cues.map { it.sourceId }
        if (responseIds != expectedIds || responseIds.toSet().size != responseIds.size) {
            reject("Response cue ids do not match request.")
        }

        response.cues.forEachIndexed { index, cue ->
            val expected = request.cues[index]
            if (cue.startMs != expected.startMs || cue.endMs != expected.endMs) {
                reject("Response cue timeline does not match request.")
            }
            requireIdentifier(cue.sourceId, "source cue id")
            if (cue.lines.size !in 1..2) reject("Response cue must contain one or two lines.")
            cue.lines.forEach { line ->
                requireText(line.correctedEnglish, allowBlank = false, "corrected English")
                requireText(line.chinese, allowBlank = false, "Chinese translation")
            }
        }

        val validatedSongMatch = validateSongMatch(response.songMatch)
        val captions = try {
            response.cues.flatMapIndexed { index, cue ->
                CaptionCueSplitPolicy.apply(
                    parent = rawCaptions[index],
                    lines = cue.lines.map { line -> CaptionSplitLine(line.correctedEnglish, line.chinese) },
                )
            }
        } catch (_: IllegalArgumentException) {
            reject("Response cue could not be split within its source timeline.")
        }
        return CaptionEnhancementOutcome(
            captions = captions,
            source = CaptionResultSource.CLOUD_AI,
            state = CaptionEnhancementState.CLOUD_APPLIED,
            processingVersion = response.processingVersion,
            songMatch = validatedSongMatch,
            processingLevel = response.processingLevel,
        )
    }

    private fun validateRequest(request: CaptionEnhancementRequest, rawCaptions: List<CaptionCue>) {
        if (request.schemaVersion != CaptionEnhancementContract.SCHEMA_VERSION) reject("Unexpected request schema.")
        requireIdentifier(request.jobId, "job id")
        if (request.cues.isEmpty() || rawCaptions.size != request.cues.size) {
            reject("Request cue batch is empty or inconsistent.")
        }

        val ids = HashSet<String>(request.cues.size)
        request.cues.forEachIndexed { index, cue ->
            requireIdentifier(cue.id, "cue id")
            if (!ids.add(cue.id)) reject("Request contains duplicate cue ids.")
            requireTimeline(cue.startMs, cue.endMs)
            requireText(cue.rawEnglish, allowBlank = false, "raw English")

            val raw = rawCaptions[index]
            if (raw.id != cue.id || raw.startMs != cue.startMs || raw.endMs != cue.endMs || raw.english != cue.rawEnglish) {
                reject("Request does not match the local Whisper batch.")
            }
        }
    }

    private fun validateSongMatch(songMatch: SongMatch?): SongMatch? {
        songMatch ?: return null
        when (songMatch.status) {
            SongMatchStatus.CONFIRMED -> {
                requireText(songMatch.title.orEmpty(), allowBlank = false, "song title")
                requireText(songMatch.artist.orEmpty(), allowBlank = false, "song artist")
                requireText(songMatch.source.orEmpty(), allowBlank = false, "song source")
                requireConfidence(
                    songMatch.confidence,
                    minimum = SongLyricsCandidateVerifier.MIN_CONFIDENCE.toFloat(),
                )
            }

            SongMatchStatus.UNCONFIRMED -> {
                val hasTitle = !songMatch.title.isNullOrBlank()
                val hasArtist = !songMatch.artist.isNullOrBlank()
                if (hasTitle != hasArtist) reject("Unconfirmed song metadata is incomplete.")
                songMatch.title?.let { requireText(it, allowBlank = false, "song title") }
                songMatch.artist?.let { requireText(it, allowBlank = false, "song artist") }
                songMatch.source?.let { requireText(it, allowBlank = false, "song source") }
                songMatch.confidence?.let { requireConfidence(it, minimum = 0.0f) }
            }

            SongMatchStatus.NOT_FOUND -> {
                if (songMatch.title != null || songMatch.artist != null) {
                    reject("NOT_FOUND song match must not contain song metadata.")
                }
                songMatch.source?.let { requireText(it, allowBlank = false, "song source") }
                songMatch.confidence?.let { requireConfidence(it, minimum = 0.0f) }
            }
        }
        return songMatch
    }

    private fun requireConfidence(confidence: Float?, minimum: Float) {
        if (confidence == null || !confidence.isFinite() || confidence < minimum || confidence > 1.0f) {
            reject("Invalid song match confidence.")
        }
    }

    private fun reject(message: String): Nothing =
        throw CaptionEnhancementValidationException(message)
}
