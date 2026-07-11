package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectArchiveTest {
    @Test
    fun roundTripsProjectMetadataAndCaptions() {
        val snapshot = ProjectSnapshot(
            videoUri = "content://videos/input.mp4",
            videoDurationMs = 42_000L,
            captions = listOf(
                CaptionCue(
                    id = "cue-1",
                    startMs = 1_000L,
                    endMs = 2_500L,
                    english = "I found a love",
                    chinese = "\u6211\u627e\u5230\u4e86\u7231",
                    confidence = 0.9f,
                ),
            ),
            exportProfile = ExportProfile(
                outputName = "captioned.mp4",
                subtitleStyle = SubtitleStyle(fontSizeSp = 28, bottomMarginPercent = 15),
                burnInSubtitles = true,
            ),
        )

        val archive = ProjectArchive()
        val restored = archive.read(archive.write(snapshot))

        assertEquals(snapshot.videoUri, restored.videoUri)
        assertEquals(snapshot.videoDurationMs, restored.videoDurationMs)
        assertEquals(snapshot.exportProfile, restored.exportProfile)
        assertEquals(1, restored.captions.size)
        assertEquals("I found a love", restored.captions[0].english)
        assertEquals("\u6211\u627e\u5230\u4e86\u7231", restored.captions[0].chinese)
    }
}
