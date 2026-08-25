package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
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
import com.example.lyriccaptioner.model.LEGACY_PLAY_RES_Y

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
                        fontSizeRatio = 33f / LEGACY_PLAY_RES_Y,
                        primaryColorHex = "#123456",
                        bold = false,
                        alignment = CaptionAlignment.RIGHT,
                    ),
                    layoutOverride = CaptionLayoutOverride(xRatio = 0.2f, widthRatio = 0.65f),
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
                fontSizeRatio = 29f / LEGACY_PLAY_RES_Y,
            ),
        )

        val encoded = archive.write(snapshot)
        val restored = archive.read(encoded)

        assertTrue(encoded.startsWith("# LyricCaptionerProject v7\n"))
        assertEquals(snapshot, restored)
        assertNull(restored.captions[1].styleOverride)
        assertNull(restored.captions[1].layoutOverride)
    }

    @Test
    fun splitCueRoundTripPreservesStableIdsTimelineTextAndStyle() {
        val style = CaptionStyleOverride(
            fontSizeRatio = 34f / LEGACY_PLAY_RES_Y,
            fontFamily = "mono",
            bold = true,
            italic = true,
        )
        val layout = CaptionLayoutOverride(xRatio = 0.18f, widthRatio = 0.72f)
        val snapshot = ProjectSnapshot(
            videoUri = "content://video/split",
            videoDurationMs = 4_000,
            captions = listOf(
                CaptionCue("parent:1", 200, 1_958, "First line", "第一句", 0.87f, styleOverride = style, layoutOverride = layout),
                CaptionCue("parent:2", 2_042, 3_800, "Second line", "第二句", 0.87f, styleOverride = style, layoutOverride = layout),
            ),
            exportProfile = ExportProfile(),
        )

        val restored = archive.read(archive.write(snapshot))

        assertEquals(snapshot.captions, restored.captions)
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
        assertEquals(30f / LEGACY_PLAY_RES_Y, restored.defaultCaptionStyle.fontSizeRatio, 0.000001f)
    }

    @Test
    fun v6RoundTripPreservesSourceRelativeStyleAndPartialOverride() {
        val snapshot = ProjectSnapshot(
            videoUri = null,
            videoDurationMs = null,
            captions = listOf(
                CaptionCue(
                    "ratio", 0, 100, "en", "中", 1f,
                    styleOverride = CaptionStyleOverride(
                        fontSizeRatio = 32f / LEGACY_PLAY_RES_Y,
                        outlineWidthRatio = 3f / LEGACY_PLAY_RES_Y,
                        bold = true,
                    ),
                ),
                CaptionCue(
                    "color-only", 100, 200, "en2", "中2", 1f,
                    styleOverride = CaptionStyleOverride(primaryColorHex = "#123456"),
                ),
            ),
            exportProfile = ExportProfile(),
            defaultCaptionStyle = DefaultCaptionStyle(
                fontSizeRatio = 26f / LEGACY_PLAY_RES_Y,
                outlineWidthRatio = 2.5f / LEGACY_PLAY_RES_Y,
            ),
        )

        val restored = archive.read(archive.write(snapshot))

        assertEquals(26f / LEGACY_PLAY_RES_Y, restored.defaultCaptionStyle.fontSizeRatio, 0.000001f)
        assertEquals(2.5f / LEGACY_PLAY_RES_Y, restored.defaultCaptionStyle.outlineWidthRatio, 0.000001f)
        assertEquals(32f / LEGACY_PLAY_RES_Y, restored.captions[0].styleOverride?.fontSizeRatio ?: Float.NaN, 0.000001f)
        assertEquals(3f / LEGACY_PLAY_RES_Y, restored.captions[0].styleOverride?.outlineWidthRatio ?: Float.NaN, 0.000001f)
        assertEquals("#123456", restored.captions[1].styleOverride?.primaryColorHex)
        assertNull(restored.captions[1].styleOverride?.fontSizeRatio)
        assertNull(restored.captions[1].styleOverride?.outlineWidthRatio)
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

    @Test
    fun v4CueRecordsMigrateWithoutLayoutOverride() {
        val v4Payload = listOf(
            encode("legacy"), "100", "900", encode("english"), encode("中文"), "0.9", "true", "",
            "false", "", "", "", "", "", "", "", "",
        ).joinToString("\u001F")
        val raw = """# LyricCaptionerProject v4
layoutXRatio=0.1
layoutYRatio=0.8
layoutWidthRatio=0.7
captions=${encode(v4Payload)}
"""

        val restored = archive.read(raw)

        assertNull(restored.captions.single().layoutOverride)
        assertEquals(0.1f, restored.captionLayout.xRatio)
    }

    @Test
    fun invalidV5LayoutOverrideIsRejected() {
        val snapshot = ProjectSnapshot(
            videoUri = null,
            videoDurationMs = 1_000,
            captions = listOf(CaptionCue("cue", 0, 100, "en", "zh", 0.9f,
                layoutOverride = CaptionLayoutOverride(xRatio = 0.2f))),
            exportProfile = ExportProfile(),
        )
        val encoded = archive.write(snapshot)
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCaptions(encoded) { it.replace("0.2", "NaN") })
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCaptions(encoded) { it.replace("0.2", "0.8") })
        }
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
