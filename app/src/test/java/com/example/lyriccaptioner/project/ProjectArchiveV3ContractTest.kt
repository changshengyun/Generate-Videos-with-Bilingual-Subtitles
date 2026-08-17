package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionProcessingSnapshot
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import com.example.lyriccaptioner.processing.enhancement.SongMatch
import com.example.lyriccaptioner.processing.enhancement.SongMatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveV3ContractTest {
    private val archive = ProjectArchive()

    @Test
    fun v3RoundTripPreservesCaptionSourceProcessingVersionErrorAndSongMatch() {
        val snapshot = ProjectSnapshot(
            videoUri = "content://video/example",
            videoDurationMs = 2_000L,
            captions = listOf(CaptionCue("cue-a", 0L, 1_000L, "alpha", "translation-a", 0.9f)),
            exportProfile = ExportProfile(),
            captionProcessing = CaptionProcessingSnapshot(
                state = CaptionEnhancementState.LOCAL_FALLBACK_APPLIED,
                source = CaptionResultSource.LOCAL_FALLBACK,
                processingVersion = "contract-v1",
                lastErrorKind = CaptionEnhancementErrorKind.TIMEOUT,
                songMatch = SongMatch(
                    status = SongMatchStatus.UNCONFIRMED,
                    title = "Possible Song",
                    artist = "Possible Artist",
                    confidence = 0.61f,
                    source = "licensed-catalog",
                ),
            ),
        )

        val encoded = archive.write(snapshot)
        val restored = archive.read(encoded)

        assertTrue(encoded.startsWith("# LyricCaptionerProject v7\n"))
        assertEquals(snapshot, restored)
    }

    @Test
    fun legacyV2ArchiveDefaultsToRawAsrWithoutInventingCloudProcessing() {
        val raw = """# LyricCaptionerProject v2
videoUri=
captions=
"""

        val restored = archive.read(raw)

        assertEquals(CaptionResultSource.RAW_ASR, restored.captionProcessing.source)
        assertEquals(CaptionEnhancementState.RAW_ASR_READY, restored.captionProcessing.state)
        assertEquals(null, restored.captionProcessing.processingVersion)
        assertEquals(null, restored.captionProcessing.lastErrorKind)
        assertEquals(null, restored.captionProcessing.songMatch)
    }

    @Test
    fun v3ArchiveDoesNotPersistApiKeyOrProviderRawLyricsOutsideFinalCues() {
        val snapshot = ProjectSnapshot(
            videoUri = null,
            videoDurationMs = null,
            captions = listOf(CaptionCue("cue-a", 0L, 1_000L, "final english", "final chinese", 0.9f)),
            exportProfile = ExportProfile(),
            captionProcessing = CaptionProcessingSnapshot(
                state = CaptionEnhancementState.CLOUD_APPLIED,
                source = CaptionResultSource.CLOUD_AI,
                processingVersion = "provider-v1",
            ),
        )

        val encoded = archive.write(snapshot)

        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("provider-raw-lyrics-sentinel"))
    }
}
