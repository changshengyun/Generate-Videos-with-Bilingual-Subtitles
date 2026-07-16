package com.example.lyriccaptioner.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class FfmpegKitSubtitleExporterTest {
    @Test
    fun buildSubtitleFilter_handlesAndroidPrivateDirectoryPath() {
        assertEquals(
            "subtitles=filename='/data/user/0/com.example.lyriccaptioner/cache/ffmpeg-exports/job-1/captions.ass'",
            buildSubtitleFilter(
                "/data/user/0/com.example.lyriccaptioner/cache/ffmpeg-exports/job-1/captions.ass",
            ),
        )
    }

    @Test
    fun buildSubtitleFilter_quotesPathContainingSpaces() {
        assertEquals(
            "subtitles=filename='/data/user/0/com.example.lyriccaptioner/cache/My Export/captions final.ass'",
            buildSubtitleFilter(
                "/data/user/0/com.example.lyriccaptioner/cache/My Export/captions final.ass",
            ),
        )
    }

    @Test
    fun buildSubtitleFilter_escapesColonBackslashAndSingleQuoteAcrossAvFilterLayers() {
        assertEquals(
            "subtitles=filename='C\\:\\\\exports\\\\director'\\\\\\''s captions.ass'",
            buildSubtitleFilter("C:\\exports\\director's captions.ass"),
        )
    }
}
