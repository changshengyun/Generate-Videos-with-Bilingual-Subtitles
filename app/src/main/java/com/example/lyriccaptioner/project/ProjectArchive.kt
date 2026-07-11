package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.captions.SrtWriter
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SubtitleStyle

class ProjectArchive(
    private val srtParser: SrtParser = SrtParser(),
    private val srtWriter: SrtWriter = SrtWriter(),
) {
    fun write(snapshot: ProjectSnapshot): String {
        val duration = snapshot.videoDurationMs?.toString().orEmpty()
        return buildString {
            appendLine(MAGIC)
            appendLine("videoUri=${snapshot.videoUri.orEmpty()}")
            appendLine("videoDurationMs=$duration")
            appendLine("outputName=${snapshot.exportProfile.outputName}")
            appendLine("burnInSubtitles=${snapshot.exportProfile.burnInSubtitles}")
            appendLine("fontSizeSp=${snapshot.exportProfile.subtitleStyle.fontSizeSp}")
            appendLine("bottomMarginPercent=${snapshot.exportProfile.subtitleStyle.bottomMarginPercent}")
            appendLine("primaryColorHex=${snapshot.exportProfile.subtitleStyle.primaryColorHex}")
            appendLine("secondaryColorHex=${snapshot.exportProfile.subtitleStyle.secondaryColorHex}")
            appendLine("outlineColorHex=${snapshot.exportProfile.subtitleStyle.outlineColorHex}")
            appendLine()
            append(srtWriter.write(snapshot.captions))
        }
    }

    fun read(raw: String): ProjectSnapshot {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val parts = normalized.split("\n\n", limit = 2)
        val header = parts.firstOrNull().orEmpty().lines()
        require(header.firstOrNull() == MAGIC) { "Unsupported project archive." }

        val values = header.drop(1)
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()

        val style = SubtitleStyle(
            fontSizeSp = values["fontSizeSp"]?.toIntOrNull() ?: 24,
            bottomMarginPercent = values["bottomMarginPercent"]?.toIntOrNull() ?: 12,
            primaryColorHex = values["primaryColorHex"].orDefault("#FFFFFF"),
            secondaryColorHex = values["secondaryColorHex"].orDefault("#F4E7A1"),
            outlineColorHex = values["outlineColorHex"].orDefault("#000000"),
        )
        val profile = ExportProfile(
            outputName = values["outputName"].orDefault("lyric-captioner-output.mp4"),
            subtitleStyle = style,
            burnInSubtitles = values["burnInSubtitles"]?.toBooleanStrictOrNull() ?: true,
        )

        return ProjectSnapshot(
            videoUri = values["videoUri"].orEmpty().ifBlank { null },
            videoDurationMs = values["videoDurationMs"]?.toLongOrNull(),
            captions = srtParser.parse(parts.getOrElse(1) { "" }),
            exportProfile = profile,
        )
    }

    private fun String?.orDefault(default: String): String {
        return this?.ifBlank { default } ?: default
    }

    private companion object {
        const val MAGIC = "# LyricCaptionerProject v1"
    }
}
