package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SubtitleStyle
import java.util.Base64

/** Versioned, text-based project archive codec. Media bytes are never copied into an archive. */
class ProjectArchive(
    private val srtParser: SrtParser = SrtParser(),
) {
    fun write(snapshot: ProjectSnapshot): String = buildString {
        appendLine(MAGIC_V2)
        appendField("videoUri", snapshot.videoUri?.let(::encode))
        appendField("videoDurationMs", snapshot.videoDurationMs?.toString())
        appendField("outputName", encode(snapshot.exportProfile.outputName))
        appendField("burnInSubtitles", snapshot.exportProfile.burnInSubtitles.toString())
        appendField("fontSizeSp", snapshot.exportProfile.subtitleStyle.fontSizeSp.toString())
        appendField("bottomMarginPercent", snapshot.exportProfile.subtitleStyle.bottomMarginPercent.toString())
        appendField("primaryColorHex", encode(snapshot.exportProfile.subtitleStyle.primaryColorHex))
        appendField("secondaryColorHex", encode(snapshot.exportProfile.subtitleStyle.secondaryColorHex))
        appendField("outlineColorHex", encode(snapshot.exportProfile.subtitleStyle.outlineColorHex))
        appendField("captions", encodeCaptions(snapshot.captions))
    }

    fun read(raw: String): ProjectSnapshot {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val header = normalized.substringBefore('\n').trimEnd()
        return when {
            header == MAGIC_V1 -> readV1(normalized)
            header == MAGIC_V2 -> readV2(normalized)
            header.startsWith("# LyricCaptionerProject v") ->
                throw UnsupportedProjectArchiveVersionException("Unsupported project archive version.")
            else -> throw ProjectArchiveFormatException("Invalid project archive magic.")
        }
    }

    private fun readV1(raw: String): ProjectSnapshot {
        val (values, body) = splitHeader(raw)
        val profile = ExportProfile(
            outputName = values["outputName"].orDefault("lyric-captioner-output.mp4"),
            subtitleStyle = SubtitleStyle(
                fontSizeSp = values.optionalInt("fontSizeSp", 24),
                bottomMarginPercent = values.optionalInt("bottomMarginPercent", 12),
                primaryColorHex = values["primaryColorHex"].orDefault("#FFFFFF"),
                secondaryColorHex = values["secondaryColorHex"].orDefault("#F4E7A1"),
                outlineColorHex = values["outlineColorHex"].orDefault("#000000"),
            ),
            burnInSubtitles = values.optionalBoolean("burnInSubtitles", true),
        )
        val captions = if (body.isBlank()) {
            emptyList()
        } else {
            srtParser.parse(body).also {
                if (it.isEmpty()) throw ProjectArchiveFormatException("Project subtitles are invalid.")
            }
        }
        return ProjectSnapshot(
            videoUri = values["videoUri"].orEmpty().ifBlank { null },
            videoDurationMs = values.optionalLong("videoDurationMs", null),
            captions = captions,
            exportProfile = profile,
        )
    }

    private fun readV2(raw: String): ProjectSnapshot {
        val (values, _) = splitHeader(raw)
        val style = SubtitleStyle(
            fontSizeSp = values.optionalInt("fontSizeSp", 24),
            bottomMarginPercent = values.optionalInt("bottomMarginPercent", 12),
            primaryColorHex = values.decodeOptional("primaryColorHex", "#FFFFFF"),
            secondaryColorHex = values.decodeOptional("secondaryColorHex", "#F4E7A1"),
            outlineColorHex = values.decodeOptional("outlineColorHex", "#000000"),
        )
        val profile = ExportProfile(
            outputName = values.decodeOptional("outputName", "lyric-captioner-output.mp4"),
            subtitleStyle = style,
            burnInSubtitles = values.optionalBoolean("burnInSubtitles", true),
        )
        return ProjectSnapshot(
            videoUri = values.decodeOptionalNullable("videoUri"),
            videoDurationMs = values.optionalLong("videoDurationMs", null),
            captions = decodeCaptions(values["captions"]),
            exportProfile = profile,
        )
    }

    private fun splitHeader(raw: String): Pair<Map<String, String>, String> {
        val separator = raw.indexOf("\n\n")
        val headerText = if (separator >= 0) raw.substring(0, separator) else raw
        val body = if (separator >= 0) raw.substring(separator + 2) else ""
        val values = linkedMapOf<String, String>()
        headerText.lines().drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val index = line.indexOf('=')
            if (index <= 0) throw ProjectArchiveFormatException("Malformed project field.")
            val key = line.substring(0, index)
            if (key in values) throw ProjectArchiveFormatException("Duplicate project field: $key")
            values[key] = line.substring(index + 1)
        }
        return values to body
    }

    private fun StringBuilder.appendField(key: String, value: String?) {
        append(key).append('=').appendLine(value.orEmpty())
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun encodeCaptions(captions: List<CaptionCue>): String {
        val payload = captions.joinToString(RECORD_SEPARATOR) { cue ->
            listOf(
                encode(cue.id), cue.startMs.toString(), cue.endMs.toString(),
                encode(cue.english), encode(cue.chinese), cue.confidence.toString(),
                cue.confirmed.toString(),
                cue.correctionCandidates.joinToString(CANDIDATE_SEPARATOR, transform = ::encode),
            ).joinToString(FIELD_SEPARATOR)
        }
        return encode(payload)
    }

    private fun decodeCaptions(encoded: String?): List<CaptionCue> {
        if (encoded.isNullOrBlank()) return emptyList()
        val payload = decode(encoded, "captions")
        if (payload.isEmpty()) return emptyList()
        return payload.split(RECORD_SEPARATOR).mapIndexed { index, record ->
            val fields = record.split(FIELD_SEPARATOR, limit = 8)
            if (fields.size != 8) throw ProjectArchiveFormatException("Malformed subtitle record $index.")
            CaptionCue(
                id = decode(fields[0], "caption id"),
                startMs = fields[1].requiredLong("caption startMs"),
                endMs = fields[2].requiredLong("caption endMs"),
                english = decode(fields[3], "caption English text"),
                chinese = decode(fields[4], "caption Chinese text"),
                confidence = fields[5].requiredFloat("caption confidence"),
                confirmed = fields[6].requiredBoolean("caption confirmed"),
                correctionCandidates = if (fields[7].isBlank()) emptyList() else fields[7].split(CANDIDATE_SEPARATOR).map {
                    decode(it, "caption correction candidate")
                },
            )
        }
    }

    private fun Map<String, String>.optionalLong(key: String, default: Long?): Long? =
        get(key)?.takeIf { it.isNotBlank() }?.requiredLong(key) ?: default

    private fun Map<String, String>.optionalInt(key: String, default: Int): Int =
        get(key)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: if (containsKey(key) && get(key).orEmpty().isNotBlank())
                throw ProjectArchiveFormatException("Invalid number for $key.") else default

    private fun Map<String, String>.optionalBoolean(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isNotBlank() }?.requiredBoolean(key) ?: default

    private fun Map<String, String>.decodeOptional(key: String, default: String): String =
        get(key)?.takeIf { it.isNotBlank() }?.let { decode(it, key) }?.ifBlank { default } ?: default

    private fun Map<String, String>.decodeOptionalNullable(key: String): String? =
        get(key)?.takeIf { it.isNotBlank() }?.let { decode(it, key).ifBlank { null } }

    private fun String?.orDefault(default: String): String = this?.ifBlank { default } ?: default
    private fun String.requiredLong(field: String): Long = toLongOrNull()
        ?: throw ProjectArchiveFormatException("Invalid number for $field.")
    private fun String.requiredFloat(field: String): Float = toFloatOrNull()?.takeIf { it.isFinite() }
        ?: throw ProjectArchiveFormatException("Invalid number for $field.")
    private fun String.requiredBoolean(field: String): Boolean = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw ProjectArchiveFormatException("Invalid boolean for $field.")
    }
    private fun decode(value: String, field: String): String = runCatching {
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
    }.getOrElse { error -> throw ProjectArchiveFormatException("Invalid encoded value for $field.", error) }

    private companion object {
        const val MAGIC_V1 = "# LyricCaptionerProject v1"
        const val MAGIC_V2 = "# LyricCaptionerProject v2"
        const val FIELD_SEPARATOR = "\u001F"
        const val RECORD_SEPARATOR = "\u001E"
        const val CANDIDATE_SEPARATOR = ","
    }
}

open class ProjectArchiveException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class ProjectArchiveFormatException(message: String, cause: Throwable? = null) : ProjectArchiveException(message, cause)
class UnsupportedProjectArchiveVersionException(message: String) : ProjectArchiveException(message)
