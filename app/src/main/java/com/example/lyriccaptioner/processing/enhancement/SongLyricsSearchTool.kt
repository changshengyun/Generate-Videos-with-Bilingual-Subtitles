package com.example.lyriccaptioner.processing.enhancement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

data class SongIdentityCandidate(
    val title: String,
    val artist: String,
) {
    override fun toString(): String = "SongIdentityCandidate(title=$title, artist=$artist)"
}

/** A complete lyric candidate. Debug rendering intentionally excludes the lyric body. */
data class SongLyricsCandidate(
    val sourceId: String,
    val title: String,
    val artist: String,
    val completeEnglishLyrics: String,
) {
    override fun toString(): String =
        "SongLyricsCandidate(sourceId=$sourceId, title=$title, artist=$artist, lyricLength=${completeEnglishLyrics.length})"
}

interface SongLyricsSearchTool {
    suspend fun search(candidate: SongIdentityCandidate): List<SongLyricsCandidate>

    /**
     * Fuzzy full-text lookup used when metadata search cannot find the song, e.g. because the
     * ASR-mangled title does not exist. Implementations that do not support it return no result.
     */
    suspend fun searchByLyricText(queryText: String): List<SongLyricsCandidate> = emptyList()
}

enum class SongLyricsSearchFailureKind {
    CONNECTION,
    TIMEOUT,
    RATE_LIMITED,
    SOURCE_FAILURE,
    INVALID_RESPONSE,
}

class SongLyricsSearchException(
    val kind: SongLyricsSearchFailureKind,
    @Suppress("UNUSED_PARAMETER") safeDetail: String,
    @Suppress("UNUSED_PARAMETER") cause: Throwable? = null,
) : IOException("Song lyrics search failed.")

/** Read-only LRCLIB `/api/search` client with a fixed HTTPS origin and bounded response. */
class LrclibSongLyricsSearchTool(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : SongLyricsSearchTool {
    override suspend fun search(candidate: SongIdentityCandidate): List<SongLyricsCandidate> =
        withContext(Dispatchers.IO) { execute(candidate) }

    override suspend fun searchByLyricText(queryText: String): List<SongLyricsCandidate> =
        withContext(Dispatchers.IO) { executeByQuery(queryText) }

    private fun execute(candidate: SongIdentityCandidate): List<SongLyricsCandidate> {
        validateCandidate(candidate)
        val url = URL(
            "$SEARCH_ENDPOINT?track_name=${encode(candidate.title)}&artist_name=${encode(candidate.artist)}",
        )
        return execute(url)
    }

    private fun executeByQuery(queryText: String): List<SongLyricsCandidate> {
        val query = queryText.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .take(MAX_QUERY_TEXT_LENGTH)
            .trim()
        if (query.isEmpty()) return emptyList()
        return execute(URL("$SEARCH_ENDPOINT?q=${encode(query)}"))
    }

    private fun execute(url: URL): List<SongLyricsCandidate> {
        check(url.protocol == "https" && url.host == SEARCH_HOST) { "Unexpected lyrics search origin." }
        val connection = try {
            connectionFactory(url)
        } catch (error: IOException) {
            throw failure(SongLyricsSearchFailureKind.CONNECTION, error)
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", CLIENT_IDENTIFIER)
            when (val status = connection.responseCode) {
                in 200..299 -> parseCandidates(readBounded(connection.inputStream))
                408 -> throw failure(SongLyricsSearchFailureKind.TIMEOUT)
                429 -> throw failure(SongLyricsSearchFailureKind.RATE_LIMITED)
                in 500..599 -> throw failure(SongLyricsSearchFailureKind.SOURCE_FAILURE)
                else -> throw failure(SongLyricsSearchFailureKind.INVALID_RESPONSE)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SongLyricsSearchException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw failure(SongLyricsSearchFailureKind.TIMEOUT, error)
        } catch (error: JsonParseException) {
            throw failure(SongLyricsSearchFailureKind.INVALID_RESPONSE, error)
        } catch (error: IOException) {
            throw failure(SongLyricsSearchFailureKind.CONNECTION, error)
        } catch (error: IllegalArgumentException) {
            throw failure(SongLyricsSearchFailureKind.INVALID_RESPONSE, error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCandidates(bytes: ByteArray): List<SongLyricsCandidate> {
        val body = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw JsonParseException("Lyrics response is not valid UTF-8")
        }
        val items = StrictJsonParser(body).parseArrayDocument().values
        if (items.size > MAX_CANDIDATES) throw JsonParseException("Too many lyrics candidates")
        return items.mapNotNull { value ->
            val item = value.asObject()
            val id = item.requiredLong("id")
            val title = item.requiredString("trackName").trim()
            val artist = item.requiredString("artistName").trim()
            val lyrics = item.optionalString("plainLyrics")?.trim().orEmpty()
            val instrumental = item.optionalBoolean("instrumental") ?: false
            if (id < 0L || title.isBlank() || artist.isBlank()) {
                throw JsonParseException("Invalid lyrics candidate metadata")
            }
            if (title.length > MAX_METADATA_LENGTH || artist.length > MAX_METADATA_LENGTH) {
                throw JsonParseException("Lyrics candidate metadata is too long")
            }
            if (instrumental || lyrics.isBlank()) return@mapNotNull null
            if (lyrics.length > MAX_LYRICS_LENGTH || nonEmptyLineCount(lyrics) < MIN_COMPLETE_LYRIC_LINES) {
                return@mapNotNull null
            }
            if (!isSafeText(lyrics)) throw JsonParseException("Invalid lyrics text")
            SongLyricsCandidate(
                sourceId = "lrclib:$id",
                title = title,
                artist = artist,
                completeEnglishLyrics = lyrics,
            )
        }
    }

    private fun readBounded(input: InputStream): ByteArray = input.use { source ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw JsonParseException("Lyrics response is too large")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun validateCandidate(candidate: SongIdentityCandidate) {
        if (candidate.title.isBlank() || candidate.artist.isBlank() ||
            candidate.title.length > MAX_METADATA_LENGTH || candidate.artist.length > MAX_METADATA_LENGTH ||
            !isSafeText(candidate.title) || !isSafeText(candidate.artist)
        ) {
            throw failure(SongLyricsSearchFailureKind.INVALID_RESPONSE)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun failure(
        kind: SongLyricsSearchFailureKind,
        cause: Throwable? = null,
    ) = SongLyricsSearchException(kind, "Lyrics source request failed.", cause)

    companion object {
        const val SEARCH_ENDPOINT = "https://lrclib.net/api/search"
        const val SEARCH_HOST = "lrclib.net"
        const val CLIENT_IDENTIFIER =
            "LyricCaptioner/0.1.0 (https://github.com/changshengyun/Generate-Videos-with-Bilingual-Subtitles)"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RESPONSE_BYTES = 2 * 1_024 * 1_024
        const val MAX_CANDIDATES = 20
        const val MAX_METADATA_LENGTH = 300
        const val MAX_QUERY_TEXT_LENGTH = 300
        const val MAX_LYRICS_LENGTH = 250_000
        const val MIN_COMPLETE_LYRIC_LINES = 3
    }
}

private fun nonEmptyLineCount(value: String): Int = value.lineSequence().count { it.isNotBlank() }

private fun isSafeText(value: String): Boolean {
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (Character.isHighSurrogate(character)) {
            if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
            index += 2
            continue
        }
        if (Character.isLowSurrogate(character)) return false
        if (Character.isISOControl(character) && character !in setOf('\n', '\r', '\t')) return false
        index++
    }
    return true
}
