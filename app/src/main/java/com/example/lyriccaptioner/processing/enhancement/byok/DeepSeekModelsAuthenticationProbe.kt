package com.example.lyriccaptioner.processing.enhancement.byok

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal production authentication probe. It sends no user content and never reads a response body. */
class DeepSeekModelsAuthenticationProbe(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : DeepSeekKeyProbe {
    override suspend fun validate(apiKey: String) {
        val connection = connectionFactory(URL(DEEPSEEK_MODELS_URL))
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.doInput = true
            connection.doOutput = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")

            val status = connection.responseCode
            when {
                status in 200..299 -> Unit
                status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN ->
                    throw DeepSeekAuthenticationException(
                        DeepSeekAuthFailureCategory.AUTHENTICATION_REJECTED,
                        status,
                    )
                status == HTTP_PAYMENT_REQUIRED -> throw DeepSeekAuthenticationException(
                    DeepSeekAuthFailureCategory.ACCOUNT_RESTRICTED,
                    status,
                )
                status == HTTP_TOO_MANY_REQUESTS -> throw DeepSeekAuthenticationException(
                    DeepSeekAuthFailureCategory.RATE_LIMITED,
                    status,
                )
                status >= 500 -> throw DeepSeekAuthenticationException(
                    DeepSeekAuthFailureCategory.PROVIDER_UNAVAILABLE,
                    status,
                )
                else -> throw DeepSeekAuthenticationException(
                    DeepSeekAuthFailureCategory.UNEXPECTED_HTTP_RESPONSE,
                    status,
                )
            }
        } catch (failure: DeepSeekAuthenticationException) {
            throw failure
        } catch (_: IOException) {
            throw DeepSeekAuthenticationException(DeepSeekAuthFailureCategory.NETWORK_UNAVAILABLE)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEEPSEEK_MODELS_URL = "$DEEPSEEK_BASE_URL/models"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
