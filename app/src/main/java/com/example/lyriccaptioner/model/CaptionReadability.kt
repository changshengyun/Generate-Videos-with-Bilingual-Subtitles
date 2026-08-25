package com.example.lyriccaptioner.model

enum class CaptionReadabilityIssue {
    ENGLISH_LINE_TOO_LONG,
    CHINESE_LINE_TOO_LONG,
    ENGLISH_READING_TOO_FAST,
    CHINESE_READING_TOO_FAST,
    DURATION_TOO_SHORT,
    DURATION_TOO_LONG,
}

object CaptionReadability {
    const val MAX_ENGLISH_CHARACTERS = 42
    const val MAX_CHINESE_CHARACTERS = 16
    const val MAX_ENGLISH_CPS = 20.0
    const val MAX_CHINESE_CPS = 9.0
    const val MAX_DURATION_MS = 7_000L

    fun issues(cue: CaptionCue): Set<CaptionReadabilityIssue> {
        val durationMs = (cue.endMs - cue.startMs).coerceAtLeast(1L)
        val durationSeconds = durationMs / 1_000.0
        val englishCount = cue.english.count { !it.isWhitespace() }
        val chineseCount = cue.chinese.count { !it.isWhitespace() }
        return buildSet {
            if (englishCount > MAX_ENGLISH_CHARACTERS) add(CaptionReadabilityIssue.ENGLISH_LINE_TOO_LONG)
            if (chineseCount > MAX_CHINESE_CHARACTERS) add(CaptionReadabilityIssue.CHINESE_LINE_TOO_LONG)
            if (englishCount / durationSeconds > MAX_ENGLISH_CPS) add(CaptionReadabilityIssue.ENGLISH_READING_TOO_FAST)
            if (chineseCount / durationSeconds > MAX_CHINESE_CPS) add(CaptionReadabilityIssue.CHINESE_READING_TOO_FAST)
            if (durationMs < CaptionCueSplitPolicy.PREFERRED_MIN_DURATION_MS) add(CaptionReadabilityIssue.DURATION_TOO_SHORT)
            if (durationMs > MAX_DURATION_MS) add(CaptionReadabilityIssue.DURATION_TOO_LONG)
        }
    }
}
