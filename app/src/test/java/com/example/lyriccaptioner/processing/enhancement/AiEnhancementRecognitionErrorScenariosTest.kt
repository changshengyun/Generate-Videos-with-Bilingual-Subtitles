package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline recognition-quality error scenarios derived from the captured recognition input of
 * LiveAiEnhancementRecognitionTest. The base batch is the six recognized "Let It Be" lines;
 * each case mutates it into one plausible Whisper misrecognition shape and replays the local
 * pipeline deterministically:
 *
 *  - Wrong words (错字): homophone-style typos across most cues. The local DP verifier must
 *    still confirm the song and map every cue back to its canonical lyric line.
 *  - Wrong segmentation (错句): one cue merges two lyric lines and another cue steals the first
 *    words of the next line. The DP verifier must still confirm and expose the merged canonical
 *    span. The provider response honors the contract rule for two-line cues (two English lines
 *    and two aligned Chinese lines joined by a single newline) and must pass flow 4 validation.
 *  - Wrong order (顺序错乱): the cue texts are shuffled against their chronological timelines.
 *    The monotonic DP verifier must refuse to confirm such a batch, and the pipeline must fall
 *    back to conservative mode without claiming a confirmed song.
 */
class AiEnhancementRecognitionErrorScenariosTest {

    @Test
    fun wrongWordsStillConfirmThroughLocalDpVerification() {
        val cues = recognitionRequest(wrongWordLines()).cues
        val verified = SongLyricsCandidateVerifier().verify(cues, letItBeCandidate())

        assertNotNull("typo-riddled recognition must still be confirmed by the local DP verifier", verified)
        assertEquals(cues.size, verified!!.metrics.matchedCueCount)
        assertTrue("verifier confidence stays above the confirmation gate", verified.metrics.confidence >= 0.82)
        assertEquals(
            "every wrong-word cue maps back to its canonical lyric line",
            baseLines().map(::canonicalize),
            cues.map { cue -> verified.cueCanonicalEnglish.getValue(cue.id) },
        )
    }

    @Test
    fun mergedSegmentationStillConfirmsAndHonorsTheTwoLineContract() {
        val cues = recognitionRequest(mergedSegmentationLines()).cues
        val verified = SongLyricsCandidateVerifier().verify(cues, letItBeCandidate())

        assertNotNull("merged segmentation must still be confirmed by the local DP verifier", verified)
        assertEquals(cues.size, verified!!.metrics.matchedCueCount)
        assertEquals(
            "the merged cue aligns to both canonical lyric lines as one span",
            canonicalize(baseLines()[2] + " " + baseLines()[3]),
            verified.cueCanonicalEnglish.getValue("cue-3"),
        )

        // Flow 4 must accept the two-line contract shape: two English lines plus two aligned
        // Chinese lines, joined by a single newline, on a cue whose timeline stays unchanged.
        val request = recognitionRequest(mergedSegmentationLines())
        val response = CaptionEnhancementResponse(
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            jobId = request.jobId,
            processingVersion = DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
            cues = request.cues.map { cue ->
                val twoLines = cue.id == "cue-3"
                CaptionEnhancementResponseCue(
                    id = cue.id,
                    startMs = cue.startMs,
                    endMs = cue.endMs,
                    correctedEnglish = if (twoLines) "Line one\nLine two" else "Single line",
                    chinese = if (twoLines) "中文第一行\n中文第二行" else "单行中文",
                )
            },
            songMatch = null,
        )
        val outcome = CaptionEnhancementResponseValidator().validate(request, response, recognitionCaptions(request))
        assertEquals(CaptionEnhancementState.CLOUD_APPLIED, outcome.state)
        assertTrue(outcome.captions.single { it.id == "cue-3" }.english.contains("\n"))
        assertTrue(outcome.captions.single { it.id == "cue-3" }.chinese.contains("\n"))
    }

    @Test
    fun shuffledOrderRefusesConfirmationAndFallsBackToConservativeMode() {
        val cues = recognitionRequest(shuffledLines()).cues
        val verified = SongLyricsCandidateVerifier().verify(cues, letItBeCandidate())

        assertNull("the monotonic DP verifier must not confirm a batch with shuffled order", verified)

        // Provider level: identification succeeds but nothing verifies, so the batch runs in
        // conservative mode with songMatch=NOT_FOUND.
        val request = recognitionRequest(shuffledLines())
        val stages = mutableListOf<DeepSeekEnhancementStage>()
        val connections = ArrayDeque<HttpURLConnection>().apply {
            add(
                ScenarioConnection(
                    200,
                    envelope(
                        encodeJson(
                            mapOf(
                                "candidates" to listOf(
                                    mapOf("title" to "Let It Be", "artist" to "The Beatles"),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            add(ScenarioConnection(200, cannedEnhancementEnvelope(request)))
        }
        val provider = DeepSeekCaptionEnhancementProvider(
            byokManager = SentinelByokManager(),
            searchTool = object : SongLyricsSearchTool {
                override suspend fun search(identity: SongIdentityCandidate): List<SongLyricsCandidate> =
                    listOf(letItBeCandidate())
            },
            connectionFactory = { connections.removeFirst() },
            onDiagnosticStage = stages::add,
        )

        val response = runBlocking { provider.enhance(request) }
        assertNotEquals(
            "shuffled recognition must never be marked CONFIRMED",
            SongMatchStatus.CONFIRMED,
            response.songMatch?.status,
        )
        assertTrue(stages.contains(DeepSeekEnhancementStage.WHOLE_SONG_REQUEST))

        // Flow 4: the conservative batch still validates as an atomic 1:1 result.
        val outcome = CaptionEnhancementResponseValidator().validate(
            request,
            response,
            recognitionCaptions(request),
        )
        assertEquals(CaptionEnhancementState.CLOUD_APPLIED, outcome.state)
        assertEquals(request.cues.map { it.id }, outcome.captions.map { it.id })
        assertTrue(outcome.captions.all { it.chinese.isNotBlank() })
    }

    private fun baseLines() = listOf(
        "When I find myself in times of trouble",
        "Mother Mary comes to me",
        "Speaking words of wisdom, let it be",
        "And in my hour of darkness",
        "She is standing right in front of me",
        "Speaking words of wisdom, let it be",
    )

    /** Homophone-style wrong words: every line keeps most of its tokens. */
    private fun wrongWordLines() = listOf(
        "When I find myself in times of trouble",
        "Mother Mary cums to me",
        "Speaking words of wisdom, let it be",
        "And in my hour of darknes",
        "She is standing rite in front of me",
        "Speaking werds of wisdom, let it be",
    )

    /** Cue 3 swallows the next line; cue 4 keeps only the tail of its real line. */
    private fun mergedSegmentationLines() = listOf(
        "When I find myself in times of trouble",
        "Mother Mary comes to me",
        "Speaking words of wisdom, let it be and in my hour of darkness",
        "she is standing right in front of me",
        "Speaking words of wisdom, let it be",
    )

    /** The same six lines heard in the wrong order against chronological timelines. */
    private fun shuffledLines() = listOf(
        baseLines()[3],
        baseLines()[0],
        baseLines()[5],
        baseLines()[1],
        baseLines()[4],
        baseLines()[2],
    )

    /** Canonical spans are stored as normalized lowercase words joined by spaces. */
    private fun canonicalize(line: String): String = line
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun recognitionRequest(lines: List<String>): CaptionEnhancementRequest =
        CaptionEnhancementRequest(
            jobId = "recognition-errors-1",
            schemaVersion = CaptionEnhancementContract.SCHEMA_VERSION,
            cues = lines.mapIndexed { index, line ->
                CaptionEnhancementRequestCue(
                    "cue-${index + 1}",
                    index * 4_000L,
                    (index + 1) * 4_000L,
                    line,
                    0.9f,
                )
            },
            mediaDurationMs = lines.size * 4_000L,
        )

    private fun recognitionCaptions(request: CaptionEnhancementRequest): List<CaptionCue> = request.cues.map { cue ->
        CaptionCue(
            id = cue.id,
            startMs = cue.startMs,
            endMs = cue.endMs,
            english = cue.rawEnglish,
            chinese = "",
            confidence = cue.confidence ?: 0f,
        )
    }

    private fun letItBeCandidate() = SongLyricsCandidate(
        "lrclib:let-it-be",
        "Let It Be",
        "The Beatles",
        baseLines().joinToString("\n"),
    )

    private fun cannedEnhancementEnvelope(request: CaptionEnhancementRequest): String = envelope(
        encodeJson(
            mapOf(
                "schema_version" to request.schemaVersion,
                "job_id" to request.jobId,
                "processing_version" to DeepSeekCaptionEnhancementProvider.PROCESSING_VERSION,
                "cues" to request.cues.map { cue ->
                    mapOf(
                        "id" to cue.id,
                        "start_ms" to cue.startMs,
                        "end_ms" to cue.endMs,
                        "corrected_english" to cue.rawEnglish,
                        "chinese" to "中文歌词 ${cue.id}",
                    )
                },
            ),
        ),
    )

    private fun envelope(content: String): String = encodeJson(
        mapOf("choices" to listOf(mapOf("message" to mapOf("content" to content)))),
    )

    private class ScenarioConnection(
        private val status: Int,
        private val response: String,
    ) : HttpURLConnection(URL(DeepSeekCaptionEnhancementProvider.ENDPOINT)) {
        private val output = ByteArrayOutputStream()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getOutputStream(): OutputStream = output
        override fun getInputStream(): InputStream = ByteArrayInputStream(response.toByteArray())
    }

    /** Fixed sentinel key so the error scenarios never depend on the local .env. */
    private class SentinelByokManager : DeepSeekByokManager {
        override fun status() = DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, "sk-sen***")

        override suspend fun validateAndSave(apiKey: String) = status()
        override suspend fun replace(apiKey: String) = status()
        override suspend fun testConnection() = status()
        override suspend fun cancelInput() = status()
        override suspend fun delete() = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)

        override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = block("sk-error-sentinel")
    }
}
