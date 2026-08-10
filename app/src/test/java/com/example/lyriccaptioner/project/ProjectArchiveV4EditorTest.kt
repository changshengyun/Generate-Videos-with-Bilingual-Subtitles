package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveV4EditorTest {
    private val archive = ProjectArchive()

    @Test
    fun v4RoundTripPreservesLayoutDefaultStyleAndPerCueOverrides() {
        val snapshot = ProjectSnapshot(
            videoUri = "content://video/editor",
            videoDurationMs = 2_000,
            captions = listOf(
                CaptionCue(
                    id = "one",
                    startMs = 100,
                    endMs = 900,
                    english = "line\none",
                    chinese = "第一行",
                    confidence = 0.91f,
                    confirmed = true,
                    styleOverride = CaptionStyleOverride(
                        fontSizeSp = 33,
                        primaryColorHex = "#123456",
                        bold = false,
                        alignment = CaptionAlignment.RIGHT,
                    ),
                ),
                CaptionCue("two", 900, 1_800, "line two", "第二行", 0.8f),
            ),
            exportProfile = ExportProfile(),
            captionLayout = CaptionLayout(0.12f, 0.81f, 0.76f),
            defaultCaptionStyle = DefaultCaptionStyle(
                fontSizeSp = 29,
                primaryColorHex = "#ABCDEF",
                secondaryColorHex = "#FEDCBA",
                outlineColorHex = "#010203",
                fontFamily = "serif",
                bold = true,
                italic = true,
                alignment = CaptionAlignment.LEFT,
            ),
        )

        val encoded = archive.write(snapshot)
        val restored = archive.read(encoded)

        assertTrue(encoded.startsWith("# LyricCaptionerProject v4\n"))
        assertEquals(snapshot, restored)
        assertNull(restored.captions[1].styleOverride)
    }

    @Test
    fun legacyV2StyleMigratesToDefaultAndLayoutWithNoCueOverride() {
        val raw = """# LyricCaptionerProject v2
fontSizeSp=30
bottomMarginPercent=20
primaryColorHex=IzEyYWJlZg==
secondaryColorHex=I0Y0RTdBMQ==
outlineColorHex=IzAwMDAwMA==
fontFamily=c2VyaWY=
captions=
"""

        val restored = archive.read(raw)

        assertEquals(30, restored.defaultCaptionStyle.fontSizeSp)
        assertEquals("#12ABEF", restored.defaultCaptionStyle.primaryColorHex)
        assertEquals("serif", restored.defaultCaptionStyle.fontFamily)
        assertEquals(0.8f, restored.captionLayout.yRatio)
        assertTrue(restored.captions.all { it.styleOverride == null })
    }

    @Test
    fun legacyV3RemainsReadableAndGetsEditorDefaultsWithoutOverrides() {
        val legacyCaption = listOf(
            encode("legacy"), "100", "900", encode("english"), encode("中文"), "0.9", "true", "",
        ).joinToString("\u001F")
        val raw = """# LyricCaptionerProject v3
fontSizeSp=28
bottomMarginPercent=10
captionState=RAW_ASR_READY
captionSource=RAW_ASR
captions=${encode(legacyCaption)}
"""

        val restored = archive.read(raw)

        assertEquals(28, restored.defaultCaptionStyle.fontSizeSp)
        assertEquals(0.9f, restored.captionLayout.yRatio)
        assertEquals("legacy", restored.captions.single().id)
        assertEquals(100L, restored.captions.single().startMs)
        assertNull(restored.captions.single().styleOverride)
    }

    @Test
    fun illegalV4LayoutAndDefaultStyleAreRejected() {
        val encoded = archive.write(ProjectSnapshot(null, null, emptyList(), ExportProfile()))

        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(encoded.replace("layoutXRatio=0.05", "layoutXRatio=0.8"))
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(encoded.replace("defaultFontSizeSp=24", "defaultFontSizeSp=999"))
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(encoded.replace("defaultPrimaryColorHex=I0ZGRkZGRg==", "defaultPrimaryColorHex=YmFk"))
        }
    }

    @Test
    fun illegalCueOverrideIsRejectedWithoutChangingTextOrTimeline() {
        val cue = CaptionCue(
            "cue", 100, 900, "english", "中文", 0.9f,
            styleOverride = CaptionStyleOverride(primaryColorHex = "#112233"),
        )
        val snapshot = ProjectSnapshot(null, 1_000, listOf(cue), ExportProfile())
        val encoded = archive.write(snapshot)

        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCaptions(encoded) { it.replace("IzExMjIzMw==", "YmFk") })
        }
        val restored = archive.read(encoded).captions.single()
        assertEquals(cue.english, restored.english)
        assertEquals(cue.chinese, restored.chinese)
        assertEquals(cue.startMs, restored.startMs)
        assertEquals(cue.endMs, restored.endMs)
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun rewriteCaptions(raw: String, transform: (String) -> String): String {
        val lines = raw.lines().toMutableList()
        val index = lines.indexOfFirst { it.startsWith("captions=") }
        check(index >= 0)
        val payload = Base64.getDecoder().decode(lines[index].substringAfter('='))
            .toString(Charsets.UTF_8)
        lines[index] = "captions=" + encode(transform(payload))
        return lines.joinToString("\n")
    }
}
