package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.LEGACY_PLAY_RES_Y
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.adjustCaptionFontSizeRatio
import com.example.lyriccaptioner.model.resolveCaptionStyle
import com.example.lyriccaptioner.model.withFontSizeRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectArchiveR4EditSaveReloadTest {
    private val archive = ProjectArchive()

    @Test
    fun legacyV5ImportEditSaveAndReloadUsesNewCanonicalRatio() {
        val legacy = """# LyricCaptionerProject v5
fontSizeSp=30
bottomMarginPercent=12
defaultFontSizeSp=30
captions=
"""
        val imported = archive.read(legacy)
        val editedStyle = imported.defaultCaptionStyle.withFontSizeRatio(
            adjustCaptionFontSizeRatio(imported.defaultCaptionStyle.fontSizeRatio, deltaSp = 1),
        )
        val restored = archive.read(archive.write(imported.copy(defaultCaptionStyle = editedStyle)))
        assertEquals(31f / LEGACY_PLAY_RES_Y, restored.defaultCaptionStyle.fontSizeRatio, 0.000001f)
        assertEquals(31, restored.defaultCaptionStyle.fontSizeSp)
    }

    @Test
    fun everyLegacyGenerationCanEditThenPersistTheCanonicalRatio() {
        listOf(1, 2, 3).forEach { version ->
            val raw = """# LyricCaptionerProject v$version
fontSizeSp=30
bottomMarginPercent=12
captions=
"""
            assertEditedLegacyRoundTrip(raw)
        }
        assertEditedLegacyRoundTrip(
            """# LyricCaptionerProject v4
fontSizeSp=30
defaultFontSizeSp=30
captions=
""",
        )
        assertEditedLegacyRoundTrip(
            """# LyricCaptionerProject v5
fontSizeSp=30
defaultFontSizeSp=30
captions=
""",
        )
    }

    @Test
    fun v6ReloadThenCueEditDoesNotLeaveStaleLegacyProjection() {
        val initial = ProjectSnapshot(
            videoUri = "content://r4",
            videoDurationMs = 2_000,
            captions = listOf(
                CaptionCue("a", 0, 900, "a", "zh-a", 1f),
                CaptionCue("b", 900, 1_800, "b", "zh-b", 1f),
            ),
            exportProfile = ExportProfile(),
            defaultCaptionStyle = DefaultCaptionStyle(fontSizeRatio = 30f / LEGACY_PLAY_RES_Y),
        )
        val restored = archive.read(archive.write(initial))
        val target = restored.captions.first()
        val editedOverride = (target.styleOverride ?: CaptionStyleOverride()).withFontSizeRatio(
            adjustCaptionFontSizeRatio(restored.defaultCaptionStyle.fontSizeRatio, deltaSp = 1),
        )
        val edited = restored.copy(
            captions = restored.captions.map { cue ->
                if (cue.id == target.id) cue.copy(styleOverride = editedOverride) else cue
            },
        )
        val roundTrip = archive.read(archive.write(edited))
        val resolvedTarget = resolveCaptionStyle(roundTrip.defaultCaptionStyle, roundTrip.captions[0].styleOverride)
        val resolvedSibling = resolveCaptionStyle(roundTrip.defaultCaptionStyle, roundTrip.captions[1].styleOverride)
        assertEquals(31f / LEGACY_PLAY_RES_Y, resolvedTarget.fontSizeRatio, 0.000001f)
        assertEquals(31, resolvedTarget.fontSizeSp)
        assertEquals(30f / LEGACY_PLAY_RES_Y, resolvedSibling.fontSizeRatio, 0.000001f)
        assertEquals(30, resolvedSibling.fontSizeSp)
        assertNotEquals(roundTrip.captions[0].styleOverride, roundTrip.captions[1].styleOverride)
    }

    @Test
    fun canonicalAPlusAMinusClampAtFourteenAndFortyEight() {
        val minimum = adjustCaptionFontSizeRatio(14f / LEGACY_PLAY_RES_Y, deltaSp = -1)
        val maximum = adjustCaptionFontSizeRatio(48f / LEGACY_PLAY_RES_Y, deltaSp = 1)
        assertEquals(14f / LEGACY_PLAY_RES_Y, minimum, 0.000001f)
        assertEquals(48f / LEGACY_PLAY_RES_Y, maximum, 0.000001f)
    }

    @Test
    fun saveReloadKeepsSiblingWithoutImplicitOverride() {
        val snapshot = ProjectSnapshot(
            null,
            null,
            listOf(
                CaptionCue("one", 0, 100, "one", "zh-one", 1f, styleOverride = CaptionStyleOverride(primaryColorHex = "#123456")),
                CaptionCue("two", 100, 200, "two", "zh-two", 1f),
            ),
            ExportProfile(),
        )
        val restored = archive.read(archive.write(snapshot))
        assertNull(restored.captions[1].styleOverride)
        assertEquals("#123456", restored.captions[0].styleOverride?.primaryColorHex)
    }

    private fun assertEditedLegacyRoundTrip(raw: String) {
        val imported = archive.read(raw)
        val edited = imported.defaultCaptionStyle.withFontSizeRatio(
            adjustCaptionFontSizeRatio(imported.defaultCaptionStyle.fontSizeRatio, deltaSp = 1),
        )
        val restored = archive.read(archive.write(imported.copy(defaultCaptionStyle = edited)))
        assertEquals(31, restored.defaultCaptionStyle.fontSizeSp)
        assertEquals(31f / LEGACY_PLAY_RES_Y, restored.defaultCaptionStyle.fontSizeRatio, 0.000001f)
    }
}
