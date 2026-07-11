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
)
