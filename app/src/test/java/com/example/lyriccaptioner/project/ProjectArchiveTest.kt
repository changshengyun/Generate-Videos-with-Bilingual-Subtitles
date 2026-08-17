package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectArchiveTest {
    private val archive = ProjectArchive()

    @Test
    fun v2RoundTripPreservesAllProjectFieldsAndSpecialText() {
        val snapshot = sampleSnapshot()

        val restored = archive.read(archive.write(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun v1ArchiveRemainsReadable() {
        val raw = """# LyricCaptionerProject v1
videoUri=content://videos/input.mp4
videoDurationMs=42000
outputName=legacy.mp4
burnInSubtitles=false
fontSizeSp=28
bottomMarginPercent=15
primaryColorHex=#FFFFFF
secondaryColorHex=#F4E7A1
outlineColorHex=#000000

1
00:00:01,000 --> 00:00:02,500
I found a love
我找到了一份爱
"""

        val restored = archive.read(raw)

        assertEquals("content://videos/input.mp4", restored.videoUri)
        assertEquals(42_000L, restored.videoDurationMs)
        assertEquals("legacy.mp4", restored.exportProfile.outputName)
        assertEquals(false, restored.exportProfile.burnInSubtitles)
        assertEquals("我找到了一份爱", restored.captions.single().chinese)
    }

    @Test
    fun unknownV2FieldsAreIgnored() {
        val raw = archive.write(sampleSnapshot()).replaceFirst("captions=", "futureField=ignored\ncaptions=")

        assertEquals(sampleSnapshot(), archive.read(raw))
    }

    @Test
    fun missingOptionalV2FieldsUseDocumentedDefaults() {
        val restored = archive.read("""# LyricCaptionerProject v2
videoUri=
captions=
""")

        assertNull(restored.videoUri)
        assertNull(restored.videoDurationMs)
        assertTrue(restored.captions.isEmpty())
        assertEquals(ExportProfile(), restored.exportProfile)
    }

    @Test
    fun legacyV2ArchiveDefaultsFontFamilyAndSanitizesStyleBounds() {
        val raw = archive.write(
            ProjectSnapshot(
                videoUri = null,
                videoDurationMs = null,
                captions = emptyList(),
                exportProfile = ExportProfile(
                    subtitleStyle = SubtitleStyle(
                        fontSizeSp = 100,
                        bottomMarginPercent = 0,
                        primaryColorHex = "not-a-color",
                        fontFamily = "unknown",
                    ),
                ),
            ),
        )
        val restored = archive.read(raw)

        assertEquals(48, restored.exportProfile.subtitleStyle.fontSizeSp)
        assertEquals(4, restored.exportProfile.subtitleStyle.bottomMarginPercent)
        assertEquals("sans", restored.exportProfile.subtitleStyle.fontFamily)
        assertEquals("#FFFFFF", restored.exportProfile.subtitleStyle.primaryColorHex)
    }

    @Test
    fun emptySubtitleProjectRoundTripsWithoutInventingCues() {
        val snapshot = ProjectSnapshot(null, null, emptyList(), ExportProfile())

        val restored = archive.read(archive.write(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun invalidMagicAndUnsupportedVersionAreClassified() {
        assertThrows(ProjectArchiveFormatException::class.java) { archive.read("not-a-project") }
        assertThrows(UnsupportedProjectArchiveVersionException::class.java) {
            archive.read("# LyricCaptionerProject v99\n")
        }
    }

    @Test
    fun malformedNumbersBooleansAndCaptionEncodingAreRejected() {
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read("# LyricCaptionerProject v2\nvideoDurationMs=not-a-number\n")
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read("# LyricCaptionerProject v2\nburnInSubtitles=maybe\n")
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read("# LyricCaptionerProject v2\ncaptions=%%%\n")
        }
    }

    @Test
    fun malformedV1SubtitleIsRejected() {
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read("# LyricCaptionerProject v1\n\nnot an srt")
        }
    }

    private fun sampleSnapshot() = ProjectSnapshot(
        videoUri = "content://videos/带空格?name=一\n二.mp4",
        videoDurationMs = 42_000L,
        captions = listOf(
            CaptionCue(
                id = "cue|1",
                startMs = 1_000L,
                endMs = 2_500L,
                english = "Line one\nwith a newline | = : ",
                chinese = "第一行\n第二行",
                confidence = 0.73f,
                correctionCandidates = listOf("candidate,one", "候选二"),
                confirmed = true,
            ),
        ),
        exportProfile = ExportProfile(
            outputName = "字幕 输出 | final.mp4",
            subtitleStyle = SubtitleStyle(
                fontSizeSp = 28,
                bottomMarginPercent = 15,
                primaryColorHex = "#12ABEF",
                secondaryColorHex = "#F4E7A1",
                outlineColorHex = "#010203",
                fontFamily = "serif",
            ),
            burnInSubtitles = true,
        ),
    )
}
