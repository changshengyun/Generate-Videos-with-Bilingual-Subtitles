package com.example.lyriccaptioner.processing.enhancement

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongLyricsSearchToolTest {
    @Test
    fun lrclibSearchUsesFixedHttpsGetClientIdentityAndStableSourceId() = runBlocking {
        val lyricSentinel = "first complete line\nsecond complete line\nthird complete line"
        val response = encodeJson(
            listOf(
                mapOf(
                    "id" to 1234,
                    "trackName" to "Example Song",
                    "artistName" to "Example Artist",
                    "instrumental" to false,
                    "plainLyrics" to lyricSentinel,
                ),
            ),
        )
        val connection = FakeConnection(200, response)
        var requestedUrl: URL? = null
        val tool = LrclibSongLyricsSearchTool { url ->
            requestedUrl = url
            connection
        }

        val result = tool.search(SongIdentityCandidate("Example Song", "Example Artist"))

        assertEquals("https", requestedUrl?.protocol)
        assertEquals("lrclib.net", requestedUrl?.host)
        assertEquals("GET", connection.requestMethod)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(LrclibSongLyricsSearchTool.CLIENT_IDENTIFIER, connection.getRequestProperty("User-Agent"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.doOutput)
        assertTrue(connection.disconnected)
        assertEquals("lrclib:1234", result.single().sourceId)
        assertEquals(lyricSentinel, result.single().completeEnglishLyrics)
        assertFalse(result.single().toString().contains(lyricSentinel))
    }

    @Test
    fun instrumentalAndBlankLyricsAreNotReturnedAsCompleteCandidates() = runBlocking {
        val response = encodeJson(
            listOf(
                mapOf("id" to 1, "trackName" to "A", "artistName" to "B", "instrumental" to true, "plainLyrics" to "one\ntwo\nthree"),
                mapOf("id" to 2, "trackName" to "A", "artistName" to "B", "instrumental" to false, "plainLyrics" to ""),
            ),
        )

        val result = LrclibSongLyricsSearchTool { FakeConnection(200, response) }
            .search(SongIdentityCandidate("A", "B"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun malformedSourceResponseFailsWithoutRetainingBody() {
        val privateBody = "private-lyrics-response-sentinel"
        val tool = LrclibSongLyricsSearchTool { FakeConnection(200, privateBody) }

        val error = assertThrows(SongLyricsSearchException::class.java) {
            runBlocking { tool.search(SongIdentityCandidate("A", "B")) }
        }

        assertEquals(SongLyricsSearchFailureKind.INVALID_RESPONSE, error.kind)
        assertFalse(error.stackTraceToString().contains(privateBody))
    }

    private class FakeConnection(
        private val status: Int,
        private val response: String,
    ) : HttpURLConnection(URL(LrclibSongLyricsSearchTool.SEARCH_ENDPOINT)) {
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream(): InputStream = ByteArrayInputStream(response.toByteArray())
    }
}
