package com.example.lyriccaptioner.project

import com.example.lyriccaptioner.captions.SrtParser
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.CaptionAlignment
import com.example.lyriccaptioner.model.CaptionLayout
import com.example.lyriccaptioner.model.CaptionLayoutOverride
import com.example.lyriccaptioner.model.CaptionProcessingSnapshot
import com.example.lyriccaptioner.model.CaptionStyleOverride
import com.example.lyriccaptioner.model.DefaultCaptionStyle
import com.example.lyriccaptioner.model.ExportProfile
import com.example.lyriccaptioner.model.MAX_CAPTION_FONT_SIZE_SP
import com.example.lyriccaptioner.model.MIN_CAPTION_FONT_SIZE_SP
import com.example.lyriccaptioner.model.ProjectSnapshot
import com.example.lyriccaptioner.model.SUBTITLE_FONT_MONO
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SANS
import com.example.lyriccaptioner.model.SUBTITLE_FONT_SERIF
import com.example.lyriccaptioner.model.SubtitleStyle
import com.example.lyriccaptioner.model.isValidSubtitleColorHex
import com.example.lyriccaptioner.model.toCaptionLayout
import com.example.lyriccaptioner.model.toDefaultCaptionStyle
import com.example.lyriccaptioner.model.resolveCaptionLayout
import com.example.lyriccaptioner.model.validated
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementErrorKind
import com.example.lyriccaptioner.processing.enhancement.CaptionEnhancementState
import com.example.lyriccaptioner.processing.enhancement.CaptionResultSource
import com.example.lyriccaptioner.processing.enhancement.SongMatch
import com.example.lyriccaptioner.processing.enhancement.SongMatchStatus
import java.util.Base64

/** Versioned, text-based project archive codec. Media bytes are never copied into an archive. */
class ProjectArchive(
    private val srtParser: SrtParser = SrtParser(),
) {
    fun write(snapshot: ProjectSnapshot): String = buildString {
        val defaultStyle = snapshot.defaultCaptionStyle.validated()
        appendLine(MAGIC_V5)
        appendField("videoUri", snapshot.videoUri?.let(::encode))
        appendField("videoDurationMs", snapshot.videoDurationMs?.toString())
        appendField("outputName", encode(snapshot.exportProfile.outputName))
        appendField("burnInSubtitles", snapshot.exportProfile.burnInSubtitles.toString())
        appendField("fontSizeSp", snapshot.exportProfile.subtitleStyle.fontSizeSp.toString())
        appendField("bottomMarginPercent", snapshot.exportProfile.subtitleStyle.bottomMarginPercent.toString())
        appendField("primaryColorHex", encode(snapshot.exportProfile.subtitleStyle.primaryColorHex))
        appendField("secondaryColorHex", encode(snapshot.exportProfile.subtitleStyle.secondaryColorHex))
        appendField("outlineColorHex", encode(snapshot.exportProfile.subtitleStyle.outlineColorHex))
        appendField("fontFamily", encode(snapshot.exportProfile.subtitleStyle.fontFamily))
        appendField("layoutXRatio", snapshot.captionLayout.xRatio.toString())
        appendField("layoutYRatio", snapshot.captionLayout.yRatio.toString())
        appendField("layoutWidthRatio", snapshot.captionLayout.widthRatio.toString())
        appendField("defaultFontSizeSp", defaultStyle.fontSizeSp.toString())
        appendField("defaultPrimaryColorHex", encode(defaultStyle.primaryColorHex))
        appendField("defaultSecondaryColorHex", encode(defaultStyle.secondaryColorHex))
        appendField("defaultOutlineColorHex", encode(defaultStyle.outlineColorHex))
        appendField("defaultFontFamily", encode(defaultStyle.fontFamily))
        appendField("defaultBold", defaultStyle.bold.toString())
        appendField("defaultItalic", defaultStyle.italic.toString())
        appendField("defaultAlignment", defaultStyle.alignment.name)
        appendField("captions", encodeCaptionsV5(snapshot.captions))
        appendField("captionState", snapshot.captionProcessing.state.name)
        appendField("captionSource", snapshot.captionProcessing.source.name)
        appendField("captionProcessingVersion", snapshot.captionProcessing.processingVersion?.let(::encode))
        appendField("captionErrorKind", snapshot.captionProcessing.lastErrorKind?.name)
        appendField("songMatchStatus", snapshot.captionProcessing.songMatch?.status?.name)
        appendField("songMatchTitle", snapshot.captionProcessing.songMatch?.title?.let(::encode))
        appendField("songMatchArtist", snapshot.captionProcessing.songMatch?.artist?.let(::encode))
        appendField("songMatchConfidence", snapshot.captionProcessing.songMatch?.confidence?.toString())
        appendField("songMatchSource", snapshot.captionProcessing.songMatch?.source?.let(::encode))
    }

    fun read(raw: String): ProjectSnapshot {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val header = normalized.substringBefore('\n').trimEnd()
        return when {
            header == MAGIC_V1 -> readV1(normalized)
            header == MAGIC_V2 -> readV2(normalized)
            header == MAGIC_V3 -> readV3(normalized)
            header == MAGIC_V4 -> readV4(normalized)
            header == MAGIC_V5 -> readV5(normalized)
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
            ).validated(),
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
            captionProcessing = CaptionProcessingSnapshot(),
            captionLayout = profile.subtitleStyle.toCaptionLayout(),
            defaultCaptionStyle = profile.subtitleStyle.toDefaultCaptionStyle(),
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
            fontFamily = values.decodeOptional("fontFamily", "sans"),
        ).validated()
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
            captionProcessing = CaptionProcessingSnapshot(),
            captionLayout = style.toCaptionLayout(),
            defaultCaptionStyle = style.toDefaultCaptionStyle(),
        )
    }

    private fun readV3(raw: String): ProjectSnapshot {
        val (values, _) = splitHeader(raw)
        val style = SubtitleStyle(
            fontSizeSp = values.optionalInt("fontSizeSp", 24),
            bottomMarginPercent = values.optionalInt("bottomMarginPercent", 12),
            primaryColorHex = values.decodeOptional("primaryColorHex", "#FFFFFF"),
            secondaryColorHex = values.decodeOptional("secondaryColorHex", "#F4E7A1"),
            outlineColorHex = values.decodeOptional("outlineColorHex", "#000000"),
            fontFamily = values.decodeOptional("fontFamily", "sans"),
        ).validated()
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
            captionProcessing = values.readCaptionProcessing(),
            captionLayout = style.toCaptionLayout(),
            defaultCaptionStyle = style.toDefaultCaptionStyle(),
        )
    }

    private fun readV4(raw: String): ProjectSnapshot {
        val (values, _) = splitHeader(raw)
        val legacyStyle = SubtitleStyle(
            fontSizeSp = values.optionalInt("fontSizeSp", 24),
            bottomMarginPercent = values.optionalInt("bottomMarginPercent", 12),
            primaryColorHex = values.decodeOptional("primaryColorHex", "#FFFFFF"),
            secondaryColorHex = values.decodeOptional("secondaryColorHex", "#F4E7A1"),
            outlineColorHex = values.decodeOptional("outlineColorHex", "#000000"),
            fontFamily = values.decodeOptional("fontFamily", SUBTITLE_FONT_SANS),
        ).validated()
        val profile = ExportProfile(
            outputName = values.decodeOptional("outputName", "lyric-captioner-output.mp4"),
            subtitleStyle = legacyStyle,
            burnInSubtitles = values.optionalBoolean("burnInSubtitles", true),
        )
        return ProjectSnapshot(
            videoUri = values.decodeOptionalNullable("videoUri"),
            videoDurationMs = values.optionalLong("videoDurationMs", null),
            captions = decodeCaptionsV4(values["captions"]),
            exportProfile = profile,
            captionProcessing = values.readCaptionProcessing(),
            captionLayout = values.readCaptionLayout(),
            defaultCaptionStyle = values.readDefaultCaptionStyle(),
        )
    }

    private fun readV5(raw: String): ProjectSnapshot {
        val (values, _) = splitHeader(raw)
        val legacyStyle = SubtitleStyle(
            fontSizeSp = values.optionalInt("fontSizeSp", 24),
            bottomMarginPercent = values.optionalInt("bottomMarginPercent", 12),
            primaryColorHex = values.decodeOptional("primaryColorHex", "#FFFFFF"),
            secondaryColorHex = values.decodeOptional("secondaryColorHex", "#F4E7A1"),
            outlineColorHex = values.decodeOptional("outlineColorHex", "#000000"),
            fontFamily = values.decodeOptional("fontFamily", SUBTITLE_FONT_SANS),
        ).validated()
        val profile = ExportProfile(
            outputName = values.decodeOptional("outputName", "lyric-captioner-output.mp4"),
            subtitleStyle = legacyStyle,
            burnInSubtitles = values.optionalBoolean("burnInSubtitles", true),
        )
        val projectLayout = values.readCaptionLayout()
        return ProjectSnapshot(
            videoUri = values.decodeOptionalNullable("videoUri"),
            videoDurationMs = values.optionalLong("videoDurationMs", null),
            captions = decodeCaptionsV5(values["captions"], projectLayout),
            exportProfile = profile,
            captionProcessing = values.readCaptionProcessing(),
            captionLayout = projectLayout,
            defaultCaptionStyle = values.readDefaultCaptionStyle(),
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

    private fun encodeCaptionsV4(captions: List<CaptionCue>): String {
        val payload = captions.joinToString(RECORD_SEPARATOR) { cue ->
            val style = cue.styleOverride?.validated()
            listOf(
                encode(cue.id), cue.startMs.toString(), cue.endMs.toString(),
                encode(cue.english), encode(cue.chinese), cue.confidence.toString(),
                cue.confirmed.toString(),
                cue.correctionCandidates.joinToString(CANDIDATE_SEPARATOR, transform = ::encode),
                (style != null).toString(),
                style?.fontSizeSp?.toString().orEmpty(),
                style?.primaryColorHex?.let(::encode).orEmpty(),
                style?.secondaryColorHex?.let(::encode).orEmpty(),
                style?.outlineColorHex?.let(::encode).orEmpty(),
                style?.fontFamily?.let(::encode).orEmpty(),
                style?.bold?.toString().orEmpty(),
                style?.italic?.toString().orEmpty(),
                style?.alignment?.name.orEmpty(),
            ).joinToString(FIELD_SEPARATOR)
        }
        return encode(payload)
    }

    private fun encodeCaptionsV5(captions: List<CaptionCue>): String {
        val payload = captions.joinToString(RECORD_SEPARATOR) { cue ->
            val style = cue.styleOverride?.validated()
            val layout = cue.layoutOverride?.validated()?.takeUnless { it.isEmpty }
            listOf(
                encode(cue.id), cue.startMs.toString(), cue.endMs.toString(),
                encode(cue.english), encode(cue.chinese), cue.confidence.toString(),
                cue.confirmed.toString(),
                cue.correctionCandidates.joinToString(CANDIDATE_SEPARATOR, transform = ::encode),
                (style != null).toString(),
                style?.fontSizeSp?.toString().orEmpty(),
                style?.primaryColorHex?.let(::encode).orEmpty(),
                style?.secondaryColorHex?.let(::encode).orEmpty(),
                style?.outlineColorHex?.let(::encode).orEmpty(),
                style?.fontFamily?.let(::encode).orEmpty(),
                style?.bold?.toString().orEmpty(),
                style?.italic?.toString().orEmpty(),
                style?.alignment?.name.orEmpty(),
                (layout != null).toString(),
                layout?.xRatio?.toString().orEmpty(),
                layout?.yRatio?.toString().orEmpty(),
                layout?.widthRatio?.toString().orEmpty(),
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

    private fun decodeCaptionsV4(encoded: String?): List<CaptionCue> {
        if (encoded.isNullOrBlank()) return emptyList()
        val payload = decode(encoded, "captions")
        if (payload.isEmpty()) return emptyList()
        return payload.split(RECORD_SEPARATOR).mapIndexed { index, record ->
            val fields = record.split(FIELD_SEPARATOR, limit = 17)
            if (fields.size != 17) throw ProjectArchiveFormatException("Malformed subtitle record $index.")
            val hasOverride = fields[8].requiredBoolean("caption style override presence")
            CaptionCue(
                id = decode(fields[0], "caption id"),
                startMs = fields[1].requiredLong("caption startMs"),
                endMs = fields[2].requiredLong("caption endMs"),
                english = decode(fields[3], "caption English text"),
                chinese = decode(fields[4], "caption Chinese text"),
                confidence = fields[5].requiredFloat("caption confidence"),
                confirmed = fields[6].requiredBoolean("caption confirmed"),
                correctionCandidates = if (fields[7].isBlank()) emptyList() else fields[7]
                    .split(CANDIDATE_SEPARATOR).map { decode(it, "caption correction candidate") },
                styleOverride = if (hasOverride) readCaptionStyleOverride(fields, index) else {
                    if (fields.drop(9).any(String::isNotBlank)) {
                        throw ProjectArchiveFormatException("Caption style override $index is inconsistent.")
                    }
                    null
                },
            )
        }
    }

    private fun decodeCaptionsV5(
        encoded: String?,
        projectLayout: CaptionLayout,
    ): List<CaptionCue> {
        if (encoded.isNullOrBlank()) return emptyList()
        val payload = decode(encoded, "captions")
        if (payload.isEmpty()) return emptyList()
        return payload.split(RECORD_SEPARATOR).mapIndexed { index, record ->
            val fields = record.split(FIELD_SEPARATOR, limit = 21)
            if (fields.size != 21) throw ProjectArchiveFormatException("Malformed subtitle record $index.")
            val hasStyle = fields[8].requiredBoolean("caption style override presence")
            val hasLayout = fields[17].requiredBoolean("caption layout override presence")
            val style = if (hasStyle) readCaptionStyleOverride(fields, index) else {
                if (fields.subList(9, 17).any(String::isNotBlank)) {
                    throw ProjectArchiveFormatException("Caption style override $index is inconsistent.")
                }
                null
            }
            val layout = if (hasLayout) readCaptionLayoutOverride(fields, index).also {
                if (it.isEmpty) {
                    throw ProjectArchiveFormatException("Caption layout override $index is empty.")
                }
                runCatching { resolveCaptionLayout(projectLayout, it) }.getOrElse { error ->
                    throw ProjectArchiveFormatException("Caption layout override $index is out of bounds.", error)
                }
            } else {
                if (fields.subList(18, 21).any(String::isNotBlank)) {
                    throw ProjectArchiveFormatException("Caption layout override $index is inconsistent.")
                }
                null
            }
            CaptionCue(
                id = decode(fields[0], "caption id"),
                startMs = fields[1].requiredLong("caption startMs"),
                endMs = fields[2].requiredLong("caption endMs"),
                english = decode(fields[3], "caption English text"),
                chinese = decode(fields[4], "caption Chinese text"),
                confidence = fields[5].requiredFloat("caption confidence"),
                confirmed = fields[6].requiredBoolean("caption confirmed"),
                correctionCandidates = if (fields[7].isBlank()) emptyList() else fields[7]
                    .split(CANDIDATE_SEPARATOR).map { decode(it, "caption correction candidate") },
                styleOverride = style,
                layoutOverride = layout,
            )
        }
    }

    private fun readCaptionStyleOverride(fields: List<String>, index: Int): CaptionStyleOverride =
        CaptionStyleOverride(
            fontSizeSp = fields[9].optionalStrictFontSize("caption $index override fontSizeSp"),
            primaryColorHex = fields[10].decodeStrictColorOrNull("caption $index override primaryColorHex"),
            secondaryColorHex = fields[11].decodeStrictColorOrNull("caption $index override secondaryColorHex"),
            outlineColorHex = fields[12].decodeStrictColorOrNull("caption $index override outlineColorHex"),
            fontFamily = fields[13].decodeStrictFontOrNull("caption $index override fontFamily"),
            bold = fields[14].optionalStrictBoolean("caption $index override bold"),
            italic = fields[15].optionalStrictBoolean("caption $index override italic"),
            alignment = fields[16].optionalStrictEnum<CaptionAlignment>("caption $index override alignment"),
        )

    private fun readCaptionLayoutOverride(fields: List<String>, index: Int): CaptionLayoutOverride =
        try {
            CaptionLayoutOverride(
                xRatio = fields[18].optionalStrictFloat("caption $index layout override xRatio"),
                yRatio = fields[19].optionalStrictFloat("caption $index layout override yRatio"),
                widthRatio = fields[20].optionalStrictFloat("caption $index layout override widthRatio"),
            )
        } catch (error: ProjectArchiveFormatException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw ProjectArchiveFormatException("Invalid caption layout override $index.", error)
        }

    private fun Map<String, String>.readCaptionLayout(): CaptionLayout {
        val x = optionalFloat("layoutXRatio", CaptionLayout.DEFAULT_X_RATIO)
        val y = optionalFloat("layoutYRatio", CaptionLayout.DEFAULT_Y_RATIO)
        val width = optionalFloat("layoutWidthRatio", CaptionLayout.DEFAULT_WIDTH_RATIO)
        return runCatching { CaptionLayout(x, y, width) }.getOrElse { error ->
            throw ProjectArchiveFormatException("Invalid caption layout.", error)
        }
    }

    private fun Map<String, String>.readDefaultCaptionStyle(): DefaultCaptionStyle = DefaultCaptionStyle(
        fontSizeSp = get("defaultFontSizeSp")?.takeIf(String::isNotBlank)
            ?.requiredStrictFontSize("defaultFontSizeSp") ?: 24,
        primaryColorHex = decodeStrictColor("defaultPrimaryColorHex", "#FFFFFF"),
        secondaryColorHex = decodeStrictColor("defaultSecondaryColorHex", "#F4E7A1"),
        outlineColorHex = decodeStrictColor("defaultOutlineColorHex", "#000000"),
        fontFamily = decodeStrictFont("defaultFontFamily", SUBTITLE_FONT_SANS),
        bold = optionalBoolean("defaultBold", false),
        italic = optionalBoolean("defaultItalic", false),
        alignment = optionalEnum("defaultAlignment", CaptionAlignment.CENTER),
    )

    private fun Map<String, String>.optionalLong(key: String, default: Long?): Long? =
        get(key)?.takeIf { it.isNotBlank() }?.requiredLong(key) ?: default

    private fun Map<String, String>.optionalInt(key: String, default: Int): Int =
        get(key)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: if (containsKey(key) && get(key).orEmpty().isNotBlank())
                throw ProjectArchiveFormatException("Invalid number for $key.") else default

    private fun Map<String, String>.optionalFloat(key: String, default: Float): Float =
        get(key)?.takeIf { it.isNotBlank() }?.requiredFloat(key) ?: default

    private fun Map<String, String>.optionalBoolean(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isNotBlank() }?.requiredBoolean(key) ?: default

    private fun Map<String, String>.decodeOptional(key: String, default: String): String =
        get(key)?.takeIf { it.isNotBlank() }?.let { decode(it, key) }?.ifBlank { default } ?: default

    private fun Map<String, String>.decodeOptionalNullable(key: String): String? =
        get(key)?.takeIf { it.isNotBlank() }?.let { decode(it, key).ifBlank { null } }

    private fun Map<String, String>.decodeStrictColor(key: String, default: String): String =
        get(key)?.takeIf(String::isNotBlank)?.let { encoded ->
            decode(encoded, key).requireValidColor(key)
        } ?: default

    private fun Map<String, String>.decodeStrictFont(key: String, default: String): String =
        get(key)?.takeIf(String::isNotBlank)?.let { encoded ->
            decode(encoded, key).requireValidFont(key)
        } ?: default

    private fun Map<String, String>.readCaptionProcessing(): CaptionProcessingSnapshot {
        val source = optionalEnum("captionSource", CaptionResultSource.RAW_ASR)
        val state = optionalEnum("captionState", CaptionEnhancementState.RAW_ASR_READY)
        val errorKind = optionalEnumOrNull<CaptionEnhancementErrorKind>("captionErrorKind")
        val songStatus = optionalEnumOrNull<SongMatchStatus>("songMatchStatus")
        val songMatch = songStatus?.let { status ->
            val title = decodeOptionalNullable("songMatchTitle")
            val artist = decodeOptionalNullable("songMatchArtist")
            val confidence = get("songMatchConfidence")?.takeIf { it.isNotBlank() }
                ?.requiredFloat("songMatchConfidence")
            val matchSource = decodeOptionalNullable("songMatchSource")
            if (status == SongMatchStatus.NOT_FOUND && (title != null || artist != null || confidence != null)) {
                throw ProjectArchiveFormatException("Song match metadata is inconsistent.")
            }
            SongMatch(status, title, artist, confidence, matchSource)
        }
        return CaptionProcessingSnapshot(
            state = state,
            source = source,
            processingVersion = decodeOptionalNullable("captionProcessingVersion"),
            lastErrorKind = errorKind,
            songMatch = songMatch,
        )
    }

    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnum(
        key: String,
        default: T,
    ): T = get(key)?.takeIf { it.isNotBlank() }?.let { value ->
        runCatching { enumValueOf<T>(value) }.getOrElse {
            throw ProjectArchiveFormatException("Invalid value for $key.", it)
        }
    } ?: default

    private inline fun <reified T : Enum<T>> Map<String, String>.optionalEnumOrNull(key: String): T? =
        get(key)?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { enumValueOf<T>(value) }.getOrElse {
                throw ProjectArchiveFormatException("Invalid value for $key.", it)
            }
        }

    private fun String?.orDefault(default: String): String = this?.ifBlank { default } ?: default
    private fun String.requiredLong(field: String): Long = toLongOrNull()
        ?: throw ProjectArchiveFormatException("Invalid number for $field.")
    private fun String.requiredFloat(field: String): Float = toFloatOrNull()?.takeIf { it.isFinite() }
        ?: throw ProjectArchiveFormatException("Invalid number for $field.")
    private fun String.requiredStrictFontSize(field: String): Int = toIntOrNull()
        ?.takeIf { it in MIN_CAPTION_FONT_SIZE_SP..MAX_CAPTION_FONT_SIZE_SP }
        ?: throw ProjectArchiveFormatException("Invalid font size for $field.")
    private fun String.optionalStrictFontSize(field: String): Int? =
        takeIf(String::isNotBlank)?.requiredStrictFontSize(field)
    private fun String.decodeStrictColorOrNull(field: String): String? =
        takeIf(String::isNotBlank)?.let { decode(it, field).requireValidColor(field) }
    private fun String.decodeStrictFontOrNull(field: String): String? =
        takeIf(String::isNotBlank)?.let { decode(it, field).requireValidFont(field) }
    private fun String.optionalStrictBoolean(field: String): Boolean? =
        takeIf(String::isNotBlank)?.requiredBoolean(field)
    private fun String.optionalStrictFloat(field: String): Float? =
        takeIf(String::isNotBlank)?.let { value ->
            value.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: throw ProjectArchiveFormatException("Invalid number for $field.")
        }
    private inline fun <reified T : Enum<T>> String.optionalStrictEnum(field: String): T? =
        takeIf(String::isNotBlank)?.let { value ->
            runCatching { enumValueOf<T>(value) }.getOrElse {
                throw ProjectArchiveFormatException("Invalid value for $field.", it)
            }
        }
    private fun String.requireValidColor(field: String): String {
        if (!isValidSubtitleColorHex(this)) throw ProjectArchiveFormatException("Invalid color for $field.")
        return uppercase()
    }
    private fun String.requireValidFont(field: String): String = when (this) {
        SUBTITLE_FONT_SANS, SUBTITLE_FONT_SERIF, SUBTITLE_FONT_MONO -> this
        else -> throw ProjectArchiveFormatException("Invalid font family for $field.")
    }
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
        const val MAGIC_V3 = "# LyricCaptionerProject v3"
        const val MAGIC_V4 = "# LyricCaptionerProject v4"
        const val MAGIC_V5 = "# LyricCaptionerProject v5"
        const val FIELD_SEPARATOR = "\u001F"
        const val RECORD_SEPARATOR = "\u001E"
        const val CANDIDATE_SEPARATOR = ","
    }
}

open class ProjectArchiveException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class ProjectArchiveFormatException(message: String, cause: Throwable? = null) : ProjectArchiveException(message, cause)
class UnsupportedProjectArchiveVersionException(message: String) : ProjectArchiveException(message)
