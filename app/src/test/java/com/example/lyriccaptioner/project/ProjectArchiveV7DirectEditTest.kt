package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.resolveCaptionStyle
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveV7DirectEditTest {
    private val archive = ProjectArchive()

    @Test
    fun v7RoundTripPreservesDefaultAndCueBackgrounds() {
        val snapshot = ProjectSnapshot(
            videoUri = "content://video",
            videoDurationMs = 1_000,
            captions = listOf(
                CaptionCue(
                    id = "cue",
                    startMs = 0,
                    endMs = 900,
                    english = "English",
                    chinese = "中文",
                    confidence = 1f,
                    styleOverride = CaptionStyleOverride(
                        backgroundEnabled = true,
                        backgroundColorHex = "#123456",
                    ),
                    layoutOverride = CaptionLayoutOverride(0.1f, 0.2f, 0.6f),
                ),
            ),
            exportProfile = ExportProfile(),
            defaultCaptionStyle = DefaultCaptionStyle(
                backgroundEnabled = true,
                backgroundColorHex = "#654321",
            ),
        )

        val encoded = archive.write(snapshot)
        val restored = archive.read(encoded)

        assertTrue(encoded.startsWith("# LyricCaptionerProject v7\n"))
        assertTrue(restored.defaultCaptionStyle.backgroundEnabled)
        assertEquals("#654321", restored.defaultCaptionStyle.backgroundColorHex)
        assertTrue(restored.captions.single().styleOverride?.backgroundEnabled == true)
        assertEquals("#123456", restored.captions.single().styleOverride?.backgroundColorHex)
        assertEquals(snapshot.captions.single().layoutOverride, restored.captions.single().layoutOverride)
    }

    @Test
    fun legacyV6WithoutBackgroundFieldsRestoresDisabledBlackDefaults() {
        val v7 = archive.write(
            snapshotWithCueStyle(CaptionStyleOverride(primaryColorHex = "#123456")),
        )
        val restored = archive.read(downgradeToV6(v7))
        val resolvedCue = resolveCaptionStyle(
            restored.defaultCaptionStyle,
            restored.captions.single().styleOverride,
        )

        assertFalse(restored.defaultCaptionStyle.backgroundEnabled)
        assertEquals("#000000", restored.defaultCaptionStyle.backgroundColorHex)
        assertEquals(null, restored.captions.single().styleOverride?.backgroundEnabled)
        assertEquals(null, restored.captions.single().styleOverride?.backgroundColorHex)
        assertFalse(resolvedCue.backgroundEnabled)
        assertEquals("#000000", resolvedCue.backgroundColorHex)
    }

    @Test
    fun v7RejectsInvalidDefaultBackgroundBooleanAndColor() {
        val valid = archive.write(snapshotWithCueStyle(CaptionStyleOverride(backgroundEnabled = true)))
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(valid.replace("defaultBackgroundEnabled=false", "defaultBackgroundEnabled=maybe"))
        }
        val invalidColor = Base64.getEncoder().encodeToString("red".toByteArray())
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(valid.replace(Regex("defaultBackgroundColorHex=.*"), "defaultBackgroundColorHex=$invalidColor"))
        }
    }

    @Test
    fun v7RejectsInvalidCueBackgroundBooleanAndColor() {
        val valid = archive.write(snapshotWithCueStyle(CaptionStyleOverride(backgroundEnabled = true)))
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCueFields(valid) { it[18] = "maybe" })
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCueFields(valid) {
                it[19] = Base64.getEncoder().encodeToString("not-a-color".toByteArray())
            })
        }
    }

    @Test
    fun v7RejectsWrongFieldCountAndStylePresenceMismatch() {
        val valid = archive.write(snapshotWithCueStyle(null))
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCueFields(valid) { it.removeAt(it.lastIndex) })
        }
        assertThrows(ProjectArchiveFormatException::class.java) {
            archive.read(rewriteCueFields(valid) { it[18] = "true" })
        }
    }

    private fun snapshotWithCueStyle(style: CaptionStyleOverride?): ProjectSnapshot = ProjectSnapshot(
        videoUri = null,
        videoDurationMs = 1_000,
        captions = listOf(CaptionCue("cue", 0, 900, "A", "甲", 1f, styleOverride = style)),
        exportProfile = ExportProfile(),
    )

    private fun rewriteCueFields(
        raw: String,
        transform: (MutableList<String>) -> Unit,
    ): String {
        val line = raw.lineSequence().first { it.startsWith("captions=") }
        val encodedPayload = line.substringAfter('=')
        val payload = String(Base64.getDecoder().decode(encodedPayload), Charsets.UTF_8)
        val fields = payload.split('\u001F').toMutableList()
        transform(fields)
        val rewrittenPayload = fields.joinToString("\u001F")
        val rewritten = Base64.getEncoder().encodeToString(rewrittenPayload.toByteArray(Charsets.UTF_8))
        return raw.replace(line, "captions=$rewritten")
    }

    private fun downgradeToV6(raw: String): String {
        val withoutV7HeaderFields = raw
            .replace("# LyricCaptionerProject v7", "# LyricCaptionerProject v6")
            .lineSequence()
            .filterNot { it.startsWith("defaultBackgroundEnabled=") }
            .filterNot { it.startsWith("defaultBackgroundColorHex=") }
            .joinToString("\n", postfix = "\n")
        return rewriteCueFields(withoutV7HeaderFields) { fields ->
            fields.removeAt(19)
            fields.removeAt(18)
        }
    }
}
