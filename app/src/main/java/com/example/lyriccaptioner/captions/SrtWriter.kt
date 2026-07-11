package com.example.lyriccaptioner.captions

import com.example.lyriccaptioner.model.CaptionCue
import java.util.Locale

class SrtWriter {
    fun write(captions: List<CaptionCue>): String {
        return captions
            .sortedBy { it.startMs }
            .mapIndexed { index, cue -> writeBlock(index + 1, cue) }
            .joinToString(separator = "\n\n", postfix = "\n")
    }

    private fun writeBlock(index: Int, cue: CaptionCue): String {
        val body = listOf(cue.english, cue.chinese)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return buildString {
            appendLine(index)
            append(formatTimestamp(cue.startMs))
            append(" --> ")
            appendLine(formatTimestamp(cue.endMs))
            append(body)
        }
    }

    private fun formatTimestamp(valueMs: Long): String {
        val safeMs = valueMs.coerceAtLeast(0)
        val hours = safeMs / HOUR_MS
        val minutes = (safeMs % HOUR_MS) / MINUTE_MS
        val seconds = (safeMs % MINUTE_MS) / SECOND_MS
        val millis = safeMs % SECOND_MS
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private companion object {
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60 * SECOND_MS
        const val HOUR_MS = 60 * MINUTE_MS
    }
}
