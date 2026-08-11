package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Production DeepSeek adapter. The API key is held only for one request construction. */
class DeepSeekCaptionEnhancementProvider(
    private val byokManager: DeepSeekByokManager,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : CaptionEnhancementProvider {
    override suspend fun enhance(request: CaptionEnhancementRequest): CaptionEnhancementResponse =
        byokManager.withDecryptedKey { apiKey ->
            withContext(Dispatchers.IO) {
                executeRequest(apiKey, request)
            }
        }

    private fun executeRequest(
        apiKey: String,
        request: CaptionEnhancementRequest,
    ): CaptionEnhancementResponse {
        val connection = try {
            connectionFactory(URL(ENDPOINT))
        } catch (error: IOException) {
            throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
        }
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { output ->
                output.write(DeepSeekCaptionEnhancementJson.requestBody(request).toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) throw httpFailure(status)
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().take(MAX_RESPONSE_BYTES)
            }
            DeepSeekCaptionEnhancementJson.parseResponse(body)
        } catch (error: CaptionEnhancementProviderException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw providerFailure(CaptionEnhancementErrorKind.TIMEOUT, error)
        } catch (error: JsonParseException) {
            throw providerFailure(CaptionEnhancementErrorKind.INVALID_RESPONSE, error)
        } catch (error: IOException) {
            throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpFailure(status: Int): CaptionEnhancementProviderException {
        val kind = when {
            status == 401 || status == 403 -> CaptionEnhancementErrorKind.AUTHENTICATION
            status == 408 -> CaptionEnhancementErrorKind.TIMEOUT
            status == 429 || status >= 500 -> CaptionEnhancementErrorKind.RETRYABLE_SERVER
            else -> CaptionEnhancementErrorKind.INVALID_RESPONSE
        }
        return providerFailure(kind, null)
    }

    private fun providerFailure(kind: CaptionEnhancementErrorKind, cause: Throwable?): CaptionEnhancementProviderException =
        CaptionEnhancementProviderException(kind, "DeepSeek request failed.", cause)

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"
        const val PROCESSING_VERSION = "deepseek-chat-caption-enhancement.v1"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        val SYSTEM_PROMPT = """
You enhance an existing Whisper subtitle batch. Return JSON only, with no Markdown.
Never add, remove, split, merge, reorder, or retime cues. Keep every cue id and timestamp exact.
Correct the English conservatively from the supplied transcript and provide one Simplified Chinese translation per cue.
Song matching is optional: mark it CONFIRMED only when title, artist, source and confidence are reliable; otherwise use UNCONFIRMED or NOT_FOUND.
The response must contain schema_version, job_id, processing_version, cues, and optional song_match.
""".trimIndent()
    }
}

internal object DeepSeekCaptionEnhancementJson {
    fun requestBody(request: CaptionEnhancementRequest): String {
        val input = mapOf(
            "schema_version" to request.schemaVersion,
            "job_id" to request.jobId,
            "cues" to request.cues.map { cue -> mapOf(
                "id" to cue.id,
                "start_ms" to cue.startMs,
                "end_ms" to cue.endMs,
                "raw_english" to cue.rawEnglish,
            ) },
        )
        return json(mapOf(
            "model" to DeepSeekCaptionEnhancementProvider.MODEL,
            "temperature" to 0,
            "max_tokens" to (request.cues.size * 96).coerceIn(128, 4_096),
            "stream" to false,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf("role" to "system", "content" to DeepSeekCaptionEnhancementProvider.SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to json(input)),
            ),
        ))
    }

    fun parseResponse(body: String): CaptionEnhancementResponse {
        val envelope = JsonParser(body).parseObject()
        val content = envelope.array("choices").first().obj("message").string("content")
        val json = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = JsonParser(json).parseObject()
        val cues = root.array("cues").map { cue ->
            cue.obj().let {
                CaptionEnhancementResponseCue(
                    id = it.string("id"),
                    startMs = it.long("start_ms"),
                    endMs = it.long("end_ms"),
                    correctedEnglish = it.string("corrected_english"),
                    chinese = it.string("chinese"),
                )
            }
        }
        return CaptionEnhancementResponse(
            schemaVersion = root.string("schema_version"),
            jobId = root.string("job_id"),
            processingVersion = root.string("processing_version"),
            cues = cues,
            songMatch = root.optionalObject("song_match")?.let(::parseSongMatch),
        )
    }

    private fun parseSongMatch(json: JsonObject): SongMatch {
        val status = runCatching { SongMatchStatus.valueOf(json.string("status")) }
            .getOrElse { throw JsonParseException("Invalid song match status") }
        return SongMatch(
            status = status,
            title = json.optionalString("title"),
            artist = json.optionalString("artist"),
            confidence = json.optionalDouble("confidence")?.toFloat(),
            source = json.optionalString("source"),
        )
    }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
        is Boolean, is Number -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) -> json(key.toString()) + ":" + json(item) }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", transform = ::json)
        else -> throw IllegalArgumentException("Unsupported JSON value")
    }
}

private class JsonParseException(message: String) : IllegalArgumentException(message)

private class JsonParser(private val source: String) {
    private var index = 0

    fun parseObject(): JsonObject = parseValue().asObject()

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= source.length) throw JsonParseException("Unexpected end of JSON")
        return when (source[index]) {
            '{' -> parseMap()
            '[' -> parseArray()
            '"' -> JsonValue.StringValue(parseString())
            't' -> consume("true", JsonValue.BooleanValue(true))
            'f' -> consume("false", JsonValue.BooleanValue(false))
            'n' -> consume("null", JsonValue.NullValue)
            else -> parseNumber()
        }
    }

    private fun parseMap(): JsonValue.ObjectValue {
        expect('{'); skipWhitespace()
        val map = linkedMapOf<String, JsonValue>()
        if (peek('}')) { index++; return JsonValue.ObjectValue(map) }
        while (true) {
            skipWhitespace(); val key = parseString(); skipWhitespace(); expect(':')
            map[key] = parseValue(); skipWhitespace()
            if (peek('}')) { index++; return JsonValue.ObjectValue(map) }
            expect(',')
        }
    }

    private fun parseArray(): JsonValue.ArrayValue {
        expect('['); skipWhitespace()
        val values = mutableListOf<JsonValue>()
        if (peek(']')) { index++; return JsonValue.ArrayValue(values) }
        while (true) {
            values += parseValue(); skipWhitespace()
            if (peek(']')) { index++; return JsonValue.ArrayValue(values) }
            expect(',')
        }
    }

    private fun parseString(): String {
        expect('"'); val out = StringBuilder()
        while (index < source.length) {
            when (val char = source[index++]) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= source.length) throw JsonParseException("Invalid escape")
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000C'); 'n' -> out.append('\n')
                        'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> { val hex = source.substring(index, index + 4); out.append(hex.toInt(16).toChar()); index += 4 }
                        else -> throw JsonParseException("Invalid escape")
                    }
                }
                else -> out.append(char)
            }
        }
        throw JsonParseException("Unterminated string")
    }

    private fun parseNumber(): JsonValue {
        val start = index
        while (index < source.length && source[index] !in ",]} \t\r\n") index++
        val raw = source.substring(start, index)
        return raw.toDoubleOrNull()?.let { JsonValue.NumberValue(raw) } ?: throw JsonParseException("Invalid number")
    }

    private fun <T : JsonValue> consume(token: String, value: T): T {
        if (!source.startsWith(token, index)) throw JsonParseException("Invalid JSON token")
        index += token.length; return value
    }
    private fun expect(char: Char) { skipWhitespace(); if (index >= source.length || source[index++] != char) throw JsonParseException("Expected $char") }
    private fun peek(char: Char): Boolean = index < source.length && source[index] == char
    private fun skipWhitespace() { while (index < source.length && source[index].isWhitespace()) index++ }
}

private sealed class JsonValue {
    class ObjectValue(val values: Map<String, JsonValue>) : JsonValue()
    class ArrayValue(val values: List<JsonValue>) : JsonValue()
    class StringValue(val value: String) : JsonValue()
    class NumberValue(val value: String) : JsonValue()
    class BooleanValue(val value: Boolean) : JsonValue()
    data object NullValue : JsonValue()
    fun asObject() = this as? ObjectValue ?: throw JsonParseException("Expected object")
    fun obj(key: String? = null) = if (key == null) asObject() else asObject().values[key]?.asObject() ?: throw JsonParseException("Expected object")
    fun string(key: String? = null): String {
        val value = if (key == null) this else obj().values[key]
        return (value as? StringValue)?.value ?: throw JsonParseException("Expected string")
    }
    fun long(key: String) = (obj().values[key] as? NumberValue)?.value?.toLongOrNull() ?: throw JsonParseException("Expected integer")
    fun array(key: String) = (obj().values[key] as? ArrayValue)?.values ?: throw JsonParseException("Expected array")
}

private typealias JsonObject = JsonValue.ObjectValue
private fun JsonObject.string(key: String) = values[key]?.let { (it as? JsonValue.StringValue)?.value } ?: throw JsonParseException("Expected string")
private fun JsonObject.optionalString(key: String) = (values[key] as? JsonValue.StringValue)?.value?.takeIf { it.isNotBlank() }
private fun JsonObject.optionalDouble(key: String) = (values[key] as? JsonValue.NumberValue)?.value?.toDoubleOrNull()
private fun JsonObject.optionalObject(key: String) = (values[key] as? JsonValue.ObjectValue)
private fun JsonValue.ArrayValue.first() = values.firstOrNull() ?: throw JsonParseException("Expected array item")
