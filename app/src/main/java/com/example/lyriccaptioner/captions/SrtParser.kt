package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue

class SrtParser {
    fun parse(raw: String): List<CaptionCue> {
        return raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .split(Regex("\n{2,}"))
            .mapNotNull(::parseBlock)
    }

    private fun parseBlock(block: String): CaptionCue? {
        val lines = block.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return null

        val index = lines[0].trim()
        val timing = lines[1].split("-->").map { it.trim() }
        if (timing.size != 2) return null

        val startMs = parseTimestamp(timing[0]) ?: return null
        val endMs = parseTimestamp(timing[1]) ?: return null
        val textLines = lines.drop(2)
        val english = textLines.firstOrNull().orEmpty()
        val chinese = textLines.drop(1).joinToString("\n")

        return CaptionCue(
            id = "srt-$index",
            startMs = startMs,
            endMs = endMs,
            english = english,
            chinese = chinese,
            confidence = 1.0f,
            confirmed = true,
        )
    }

    private fun parseTimestamp(value: String): Long? {
        val match = TIMESTAMP.matchEntire(value) ?: return null
        val hours = match.groupValues[1].toLong()
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toLong()
        val millis = match.groupValues[4].toLong()
        return hours * HOUR_MS + minutes * MINUTE_MS + seconds * SECOND_MS + millis
    }

    private companion object {
        val TIMESTAMP = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})""")
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60 * SECOND_MS
        const val HOUR_MS = 60 * MINUTE_MS
    }
}
