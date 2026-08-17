package com.example.lyriccaptioner.model

data class ExportProfile(
    val outputName: String = "lyric-captioner-output.mp4",
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val burnInSubtitles: Boolean = true,
)

data class SubtitleStyle(
    val fontSizeSp: Int = 24,
    val bottomMarginPercent: Int = 12,
    val primaryColorHex: String = "#FFFFFF",
    val secondaryColorHex: String = "#F4E7A1",
    val outlineColorHex: String = "#000000",
    val fontFamily: String = SUBTITLE_FONT_SANS,
)

const val SUBTITLE_FONT_SANS = "sans"
const val SUBTITLE_FONT_SERIF = "serif"
const val SUBTITLE_FONT_MONO = "mono"

private val SUBTITLE_COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")

fun isValidSubtitleColorHex(value: String): Boolean = SUBTITLE_COLOR_PATTERN.matches(value)

fun normalizeSubtitleColor(value: String, fallback: String): String =
    value.takeIf(::isValidSubtitleColorHex)?.uppercase() ?: fallback

fun SubtitleStyle.validated(): SubtitleStyle = copy(
    fontSizeSp = fontSizeSp.coerceIn(14, 48),
    bottomMarginPercent = bottomMarginPercent.coerceIn(4, 28),
    primaryColorHex = normalizeSubtitleColor(primaryColorHex, "#FFFFFF"),
    secondaryColorHex = normalizeSubtitleColor(secondaryColorHex, "#F4E7A1"),
    outlineColorHex = normalizeSubtitleColor(outlineColorHex, "#000000"),
    fontFamily = when (fontFamily) {
        SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO -> fontFamily
        else -> SUBTITLE_FONT_SANS
    },
)
