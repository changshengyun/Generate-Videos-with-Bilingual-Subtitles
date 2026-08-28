package com.example.lyriccaptioner.processing.enhancement.sandbox

import com.example.lyriccaptioner.processing.enhancement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * HTTP client for DeepSeek Responses API with web_search tool support.
 * Also supports Chat Completions for Phase 3 (bilingual generation).
 */
class SandboxResponsesApiClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    /**
     * Execute a Responses API call with web_search tool.
     * Returns the final text output from the model.
     */
    suspend fun callResponses(
        apiKey: String,
        input: String,
        tools: List<Map<String, Any>> = listOf(mapOf("type" to "web_search")),
    ): SandboxResponsesResult = withContext(Dispatchers.IO) {
        val requestBody = encodeJson(
            mapOf(
                "model" to RESPONSES_MODEL,
                "input" to input,
                "tools" to tools,
            ),
        )
        val responseBody = executeRequest(
            endpoint = RESPONSES_ENDPOINT,
            apiKey = apiKey,
            requestBody = requestBody,
        )
        parseResponsesResult(responseBody)
    }

    /**
     * Execute a Chat Completions call (for Phase 3 bilingual generation).
     * Reuses the existing Chat Completions endpoint.
     */
    suspend fun executeChatRequest(
        apiKey: String,
        requestBody: String,
    ): String = withContext(Dispatchers.IO) {
        executeRequest(
            endpoint = CHAT_ENDPOINT,
            apiKey = apiKey,
            requestBody = requestBody,
        )
    }

    private fun executeRequest(
        endpoint: String,
        apiKey: String,
        requestBody: String,
    ): String {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            val connection = try {
                connectionFactory(URL(endpoint))
            } catch (error: IOException) {
                lastError = error
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                    return@repeat
                }
                throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
            }
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use { output -> output.write(requestBody.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status !in 200..299) {
                    if (status >= 500 && attempt < MAX_RETRIES - 1) {
                        lastError = httpFailure(status)
                        Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                        return@repeat
                    }
                    throw httpFailure(status)
                }
                return decodeUtf8(readBounded(connection.inputStream, MAX_RESPONSE_BYTES))
            } catch (error: SocketTimeoutException) {
                lastError = error
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                    return@repeat
                }
                throw providerFailure(CaptionEnhancementErrorKind.TIMEOUT, error)
            } catch (error: CaptionEnhancementProviderException) {
                throw error
            } catch (error: IOException) {
                lastError = error
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1))
                    return@repeat
                }
                throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, error)
            } finally {
                connection.disconnect()
            }
        }
        throw providerFailure(CaptionEnhancementErrorKind.CONNECTION, lastError)
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray = input.use { source ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            if (total > maximum) throw JsonParseException("Provider response is too large")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: java.nio.charset.CharacterCodingException) {
        throw JsonParseException("Provider response is not valid UTF-8")
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

    private fun providerFailure(
        kind: CaptionEnhancementErrorKind,
        cause: Throwable?,
    ): CaptionEnhancementProviderException =
        CaptionEnhancementProviderException(kind, "Sandbox Responses API request failed.", cause)

    private fun parseResponsesResult(body: String): SandboxResponsesResult {
        // Parse Responses API response format
        // Extract text output and web_search_output items
        val root = StrictJsonParser(body).parseObjectDocument()
        val output = root.requiredArray("output").values
        var textOutput = ""
        val searchOutputs = mutableListOf<String>()

        for (item in output) {
            val obj = item.asObject()
            val type = obj.requiredString("type")
            when (type) {
                "message" -> {
                    val content = obj.requiredArray("content").values
                    for (c in content) {
                        val contentObj = c.asObject()
                        if (contentObj.requiredString("type") == "output_text") {
                            textOutput = contentObj.requiredString("text")
                        }
                    }
                }
                "web_search_call" -> {
                    // Record search actions for diagnostics
                    searchOutputs.add(obj.toString())
                }
            }
        }

        return SandboxResponsesResult(
            textOutput = textOutput,
            searchActions = searchOutputs,
        )
    }

    companion object {
        const val RESPONSES_ENDPOINT = "https://api.deepseek.com/responses"
        const val CHAT_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val RESPONSES_MODEL = "deepseek-v4-pro"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 120_000  // Longer for web_search
        const val MAX_RESPONSE_BYTES = 2_097_152  // 2MB for search results
        const val MAX_RETRIES = 3
        const val RETRY_BACKOFF_MS = 2_000L
    }
}

/** Parsed result from Responses API. */
data class SandboxResponsesResult(
    val textOutput: String,
    val searchActions: List<String>,
)
