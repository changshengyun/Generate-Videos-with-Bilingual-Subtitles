package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEnhancementContractTest {
    private val mapper = CaptionEnhancementRequestMapper()
    private val validator = CaptionEnhancementResponseValidator()

    @Test
    fun requestMappingPreservesRawWhisperCueIdentityOrderTimelineAndEnglish() {
        val source = rawCues()

        val request = mapper.map(jobId = "job-001", captions = source)

        assertEquals(CaptionEnhancementContract.SCHEMA_VERSION, request.schemaVersion)
        assertEquals("job-001", request.jobId)
        assertEquals(source.map { it.id }, request.cues.map { it.id })
        assertEquals(source.map { it.startMs }, request.cues.map { it.startMs })
        assertEquals(source.map { it.endMs }, request.cues.map { it.endMs })
        assertEquals(source.map { it.english }, request.cues.map { it.rawEnglish })
    }

    @Test
    fun validCloudResponseProducesCompleteCorrectedBatchAndConfirmedSongMatch() {
        val request = request()
        val response = validResponse(
            request,
            songMatch = SongMatch(
                status = SongMatchStatus.CONFIRMED,
                title = "Example Song",
                artist = "Example Artist",
                confidence = 0.97f,
                source = "licensed-catalog",
            ),
        )

        val validated = validator.validate(request, response, rawCues())

        assertEquals(listOf("corrected alpha", "corrected beta"), validated.captions.map { it.english })
        assertEquals(listOf("translation-a", "translation-b"), validated.captions.map { it.chinese })
        assertEquals(rawCues().map { it.startMs }, validated.captions.map { it.startMs })
        assertEquals(SongMatchStatus.CONFIRMED, validated.songMatch?.status)
        assertEquals("provider-v1", validated.processingVersion)
    }

    @Test
    fun lowConfidenceSongMatchCannotClaimConfirmedIdentity() {
        val request = request()
        val response = validResponse(
            request,
            songMatch = SongMatch(
                status = SongMatchStatus.CONFIRMED,
                title = "Maybe",
                artist = "Unknown",
                confidence = 0.2f,
                source = "licensed-catalog",
            ),
        )

        assertThrows(CaptionEnhancementValidationException::class.java) {
            validator.validate(request, response, rawCues())
        }
    }

    @Test
    fun configuredThirtyPercentConfidenceCanClaimConfirmedIdentity() {
        val request = request()
        val response = validResponse(
            request,
            songMatch = SongMatch(
                status = SongMatchStatus.CONFIRMED,
                title = "Example Song",
                artist = "Example Artist",
                confidence = SongLyricsCandidateVerifier.MIN_CONFIDENCE.toFloat(),
                source = "lrclib:42",
            ),
        )

        val validated = validator.validate(request, response, rawCues())

        assertEquals(SongMatchStatus.CONFIRMED, validated.songMatch?.status)
        assertEquals(0.30f, validated.songMatch?.confidence ?: -1f, 0f)
    }

    @Test
    fun notFoundSongMatchDoesNotInventSongMetadata() {
        val request = request()
        val response = validResponse(
            request,
            songMatch = SongMatch(status = SongMatchStatus.NOT_FOUND),
        )

        val validated = validator.validate(request, response, rawCues())

        assertEquals(SongMatchStatus.NOT_FOUND, validated.songMatch?.status)
        assertNull(validated.songMatch?.title)
        assertNull(validated.songMatch?.artist)
    }

    @Test
    fun missingExtraOrDuplicateCueIdsRejectTheWholeResponse() {
        val request = request()
        val valid = validResponse(request)
        val variants = listOf(
            valid.copy(cues = valid.cues.dropLast(1)),
            valid.copy(cues = valid.cues + valid.cues.last().copy(sourceId = "extra")),
            valid.copy(cues = listOf(valid.cues.first(), valid.cues.first())),
        )

        variants.forEach { response ->
            assertThrows(CaptionEnhancementValidationException::class.java) {
                validator.validate(request, response, rawCues())
            }
        }
    }

    @Test
    fun changedStartOrEndTimeRejectsTheWholeResponse() {
        val request = request()
        val valid = validResponse(request)
        val variants = listOf(
            valid.copy(cues = valid.cues.mapIndexed { index, cue -> if (index == 0) cue.copy(startMs = 1L) else cue }),
            valid.copy(cues = valid.cues.mapIndexed { index, cue -> if (index == 1) cue.copy(endMs = cue.endMs + 1L) else cue }),
        )

        variants.forEach { response ->
            assertThrows(CaptionEnhancementValidationException::class.java) {
                validator.validate(request, response, rawCues())
            }
        }
    }

    @Test
    fun wrongSchemaWrongJobEmptyEnglishAndOversizedTextAreRejected() {
        val request = request()
        val valid = validResponse(request)
        val variants = listOf(
            valid.copy(schemaVersion = "wrong"),
            valid.copy(jobId = "wrong-job"),
            valid.copy(cues = valid.cues.mapIndexed { index, cue ->
                if (index == 0) cue.copy(lines = listOf(cue.lines.single().copy(correctedEnglish = " "))) else cue
            }),
            valid.copy(cues = valid.cues.mapIndexed { index, cue ->
                if (index == 0) cue.copy(lines = listOf(cue.lines.single().copy(chinese = "x".repeat(10_001)))) else cue
            }),
        )

        variants.forEach { response ->
            assertThrows(CaptionEnhancementValidationException::class.java) {
                validator.validate(request, response, rawCues())
            }
        }
    }

    @Test
    fun twoLineResponseProducesStableOrderedChildCuesWithinParentBoundary() {
        val request = request()
        val valid = validResponse(request)
        val response = valid.copy(
            cues = valid.cues.mapIndexed { index, cue ->
                if (index == 0) {
                    cue.copy(
                        endMs = 2_000L,
                        lines = listOf(
                            CaptionEnhancementResponseLine("first canonical line", "第一行"),
                            CaptionEnhancementResponseLine("second longer canonical line", "第二行"),
                        ),
                    )
                } else {
                    cue.copy(startMs = 2_000L, endMs = 3_000L)
                }
            },
        )
        val adjustedRequest = request.copy(
            cues = request.cues.mapIndexed { index, cue ->
                if (index == 0) cue.copy(endMs = 2_000L) else cue.copy(startMs = 2_000L, endMs = 3_000L)
            },
        )
        val adjustedRaw = rawCues().mapIndexed { index, cue ->
            if (index == 0) cue.copy(endMs = 2_000L) else cue.copy(startMs = 2_000L, endMs = 3_000L)
        }

        val validated = validator.validate(adjustedRequest, response, adjustedRaw)

        assertEquals(listOf("cue-a:1", "cue-a:2", "cue-b"), validated.captions.map { it.id })
        assertEquals(0L, validated.captions[0].startMs)
        assertTrue(validated.captions[0].endMs <= validated.captions[1].startMs)
        assertEquals(2_000L, validated.captions[1].endMs)
    }

    @Test
    fun diagnosticsAndDebugStringsDoNotExposeSecretsLyricsOrPrivatePaths() {
        val secret = "sk-secret-sentinel"
        val lyric = "complete-lyric-batch-sentinel"
        val privatePath = "content://private/media/sentinel"
        val request = mapper.map("job-private", listOf(cue("one", lyric)))
        val diagnostics = CaptionEnhancementDiagnostics(
            jobId = request.jobId,
            state = CaptionEnhancementState.CLOUD_PENDING,
            cueCount = request.cues.size,
            errorKind = null,
        )
        val error = CaptionEnhancementProviderException(
            kind = CaptionEnhancementErrorKind.AUTHENTICATION,
            safeDetail = "Provider authentication failed.",
            cause = IllegalStateException("$secret $lyric $privatePath"),
        )

        val forbidden = listOf(secret, lyric, privatePath)
        forbidden.forEach { value ->
            assertFalse(request.toString().contains(value))
            assertFalse(diagnostics.toString().contains(value))
            assertFalse(error.message.orEmpty().contains(value))
        }
        val requestProperties = CaptionEnhancementRequest::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(requestProperties.none { it.contains("apikey") || it == "key" || it.contains("secret") })
    }

    private fun request() = mapper.map("job-001", rawCues())

    private fun validResponse(
        request: CaptionEnhancementRequest,
        songMatch: SongMatch? = null,
    ) = CaptionEnhancementResponse(
        schemaVersion = request.schemaVersion,
        jobId = request.jobId,
        processingVersion = "provider-v1",
        cues = request.cues.mapIndexed { index, cue ->
            CaptionEnhancementResponseCue(
                sourceId = cue.id,
                startMs = cue.startMs,
                endMs = cue.endMs,
                lines = listOf(
                    CaptionEnhancementResponseLine(
                        correctedEnglish = if (index == 0) "corrected alpha" else "corrected beta",
                        chinese = if (index == 0) "translation-a" else "translation-b",
                    ),
                ),
            )
        },
        songMatch = songMatch,
    )
}

internal fun rawCues(): List<CaptionCue> = listOf(
    cue("cue-a", " line alpha ", 0L, 1_000L),
    cue("cue-b", "line beta", 1_000L, 2_000L),
)

internal fun cue(
    id: String,
    english: String,
    startMs: Long = 0L,
    endMs: Long = 1_000L,
    chinese: String = "",
) = CaptionCue(
    id = id,
    startMs = startMs,
    endMs = endMs,
    english = english,
    chinese = chinese,
    confidence = 0.9f,
)
