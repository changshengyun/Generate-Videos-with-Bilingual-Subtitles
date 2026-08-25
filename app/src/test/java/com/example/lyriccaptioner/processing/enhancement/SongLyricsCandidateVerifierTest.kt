package com.example.lyriccaptioner.processing.enhancement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLyricsCandidateVerifierTest {
    private val verifier = SongLyricsCandidateVerifier()

    @Test
    fun frozenThresholdsRemainConservative() {
        assertEquals(0.75, SongLyricsCandidateVerifier.MIN_COVERAGE, 0.0)
        assertEquals(0.62, SongLyricsCandidateVerifier.MIN_CUE_SIMILARITY, 0.0)
        assertEquals(0.78, SongLyricsCandidateVerifier.MIN_AVERAGE_SIMILARITY, 0.0)
        assertEquals(0.78, SongLyricsCandidateVerifier.MIN_MEDIAN_SIMILARITY, 0.0)
        assertEquals(0.82, SongLyricsCandidateVerifier.MIN_CONFIDENCE, 0.0)
        assertEquals(2, SongLyricsCandidateVerifier.MAX_LINES_PER_SPAN)
    }

    @Test
    fun exactMultiCueSongPassesFrozenBatchThresholds() {
        val result = verifier.verify(cues(canonicalLines()), candidate(canonicalLines()))

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(8, result.metrics.matchedCueCount)
        assertEquals(1.0, result.metrics.coverage, 0.0001)
        assertEquals(1.0, result.metrics.averageSimilarity, 0.0001)
        assertEquals(1.0, result.metrics.medianSimilarity, 0.0001)
    }

    @Test
    fun correctLyricsPassWhenCueBoundariesCrossAdjacentLyricLines() {
        val shifted = listOf(
            "Amazing grace how sweet",
            "the sound that saved a wretch like me",
            "I once was lost but now",
            "am found was blind but now I see",
            "Twas grace that taught my heart to fear",
            "And grace my fears relieved",
            "How precious did that grace",
            "appear the hour I first believed",
        )

        val result = verifier.verify(cues(shifted), candidate(canonicalLines()))

        assertConfirmedAllCues(result, shifted.size)
        requireNotNull(result)
        assertEquals("the sound That saved a wretch like me!", result.cueCanonicalEnglish["cue-1"])
        assertEquals(listOf("the sound", "That saved a wretch like me!"), result.cueCanonicalLines["cue-1"])
        assertEquals("am found; Was blind, but now I see.", result.cueCanonicalEnglish["cue-3"])
    }

    @Test
    fun oneLyricLineMaySplitAcrossTwoOrderedCues() {
        val split = listOf(
            "Amazing grace how sweet",
            "the sound",
            "That saved a wretch",
            "like me",
            "I once was lost but now am found",
            "Was blind but now I see",
            "Twas grace that taught my heart to fear",
            "And grace my fears relieved",
            "How precious did that grace appear",
            "The hour I first believed",
        )

        val result = verifier.verify(cues(split), candidate(canonicalLines()))

        assertConfirmedAllCues(result, split.size)
        requireNotNull(result)
        assertEquals("Amazing grace! How sweet", result.cueCanonicalLines["cue-0"]?.single())
        assertEquals("the sound", result.cueCanonicalLines["cue-1"]?.single())
        assertEquals("like me!", result.cueCanonicalLines["cue-3"]?.single())
    }

    @Test
    fun oneCueMayMergeTwoAdjacentLyricLines() {
        val merged = listOf(
            "Amazing grace how sweet the sound That saved a wretch like me",
            "I once was lost but now am found Was blind but now I see",
            "Twas grace that taught my heart to fear And grace my fears relieved",
            "How precious did that grace appear The hour I first believed",
        )

        val result = verifier.verify(cues(merged), candidate(canonicalLines()))

        assertConfirmedAllCues(result, merged.size)
        requireNotNull(result)
        assertEquals(2, result.cueCanonicalLines["cue-0"]?.size)
        assertEquals(canonicalLines().take(2), result.cueCanonicalLines["cue-0"])
    }

    @Test
    fun mildAsrErrorsStillPassAcrossAllCueEvidence() {
        val asr = listOf(
            "Amazing grace how sweet the sounds",
            "That save a wretch like me",
            "I once was lost but now I am found",
            "Was blind but now I can see",
            "Twas grace that taught my heart to fears",
            "And grace my fear relieved",
            "How precious did that grace appears",
            "The hour I first believe",
        )

        val result = verifier.verify(cues(asr), candidate(canonicalLines()))

        assertNotNull(result)
        requireNotNull(result)
        assertEquals(asr.size, result.metrics.matchedCueCount)
        assertTrue(result.metrics.averageSimilarity >= SongLyricsCandidateVerifier.MIN_AVERAGE_SIMILARITY)
        assertTrue(result.metrics.confidence >= SongLyricsCandidateVerifier.MIN_CONFIDENCE)
    }

    @Test
    fun fiveMatchingCuesAndThreeContradictoryCuesAreRejected() {
        val partiallyWrongVersion = canonicalLines().take(5) + listOf(
            "Thunder rolls across the mountain",
            "Strangers wait beside the station",
            "Morning paints the empty highway",
        )

        assertNull(verifier.verify(cues(canonicalLines()), candidate(partiallyWrongVersion)))
    }

    @Test
    fun partialRepeatedChorusCannotConfirmWrongRemainingVerses() {
        val wrongVersion = listOf(
            canonicalLines()[0],
            canonicalLines()[1],
            "We sailed beyond the harbor wall",
            "And counted every distant flame",
            canonicalLines()[0],
            canonicalLines()[1],
            "The winter road was cold and long",
            "No hour of faith was spoken there",
        )

        assertNull(verifier.verify(cues(canonicalLines()), candidate(wrongVersion)))
    }

    @Test
    fun unrelatedSongIsRejectedEvenWhenMetadataMatchesClaim() {
        val unrelated = listOf(
            "Mountains wake beneath the snow",
            "Morning bells are ringing clear",
            "Silver birds are turning home",
            "Winter paints the open field",
            "Footsteps vanish with the dawn",
            "A quiet train is leaving town",
            "The ocean turns from blue to gray",
            "We close the door and walk away",
        )

        assertNull(verifier.verify(cues(canonicalLines()), candidate(unrelated)))
    }

    @Test
    fun fewerThanThreeCuesCannotConfirmSong() {
        assertNull(verifier.verify(cues(canonicalLines().take(2)), candidate(canonicalLines())))
    }

    private fun assertConfirmedAllCues(result: VerifiedSongLyrics?, expectedCueCount: Int) {
        assertNotNull(result)
        requireNotNull(result)
        assertEquals(expectedCueCount, result.metrics.matchedCueCount)
        assertEquals(1.0, result.metrics.coverage, 0.0001)
        assertEquals(expectedCueCount, result.cueCanonicalEnglish.size)
        assertTrue(result.metrics.confidence >= SongLyricsCandidateVerifier.MIN_CONFIDENCE)
    }

    private fun canonicalLines() = listOf(
        "Amazing grace! How sweet the sound",
        "That saved a wretch like me!",
        "I once was lost, but now am found;",
        "Was blind, but now I see.",
        "'Twas grace that taught my heart to fear,",
        "And grace my fears relieved;",
        "How precious did that grace appear",
        "The hour I first believed.",
    )

    private fun cues(lines: List<String>) = lines.mapIndexed { index, line ->
        CaptionEnhancementRequestCue("cue-$index", index * 1_000L, (index + 1) * 1_000L, line)
    }

    private fun candidate(lines: List<String>) = SongLyricsCandidate(
        sourceId = "lrclib:42",
        title = "Amazing Grace",
        artist = "John Newton",
        completeEnglishLyrics = lines.joinToString("\n"),
    )
}
