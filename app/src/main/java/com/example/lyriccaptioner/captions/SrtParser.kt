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
        // A merged cue may carry two English lines plus two Chinese lines, so the legacy
        // "first line is English" reading loses text on re-import. Split on the first
        // Chinese-dominant line; blocks without any Chinese line keep the legacy reading.
        val chineseStart = textLines.indexOfFirst(::looksChinese)
        val english: String
        val chinese: String
        if (chineseStart >= 0) {
            english = textLines.subList(0, chineseStart).joinToString("\n")
            chinese = textLines.subList(chineseStart, textLines.size).joinToString("\n")
        } else {
            english = textLines.firstOrNull().orEmpty()
            chinese = textLines.drop(1).joinToString("\n")
        }

        return CaptionCue(
            id = "srt-$index",
            startMs = startMs,
            endMs = endMs,
            english = english,
            chinese = chinese,
            confidence = 1.0f,
            confirmed = english.isNotBlank() && chinese.isNotBlank(),
        )
    }

    private fun looksChinese(line: String): Boolean {
        val cjk = line.count { it in '\u4E00'..'\u9FFF' }
        val latin = line.count { it in 'a'..'z' || it in 'A'..'Z' }
        return cjk > 0 && cjk >= latin
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
