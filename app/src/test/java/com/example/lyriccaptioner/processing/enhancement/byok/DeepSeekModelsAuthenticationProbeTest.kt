package com.example.lyriccaptioner.processing.enhancement.byok

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekModelsAuthenticationProbeTest {
    @Test
    fun successfulProbeUsesFixedModelsGetWithoutReadingBodyOrFollowingRedirects() = runBlocking {
        val connection = FakeConnection(responseStatus = 200)
        val probe = DeepSeekModelsAuthenticationProbe { url ->
            connection.requestedUrl = url
            connection
        }
        val key = "sk-test-network-boundary-123456"

        probe.validate(key)

        assertEquals(DeepSeekModelsAuthenticationProbe.DEEPSEEK_MODELS_URL, connection.requestedUrl.toString())
        assertEquals("GET", connection.requestMethod)
        assertEquals("Bearer $key", connection.getRequestProperty("Authorization"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.doOutput)
        assertFalse(connection.responseBodyRead)
        assertTrue(connection.disconnected)
    }

    @Test
    fun authenticationRejectionIsClassifiedWithoutReadingResponseBody() {
        val connection = FakeConnection(responseStatus = 401)
        val probe = DeepSeekModelsAuthenticationProbe { connection }

        val failure = assertThrows(DeepSeekAuthenticationException::class.java) {
            runBlocking { probe.validate("sk-test-invalid-network-key-123456") }
        }

        assertEquals(DeepSeekAuthFailureCategory.AUTHENTICATION_REJECTED, failure.category)
        assertEquals(401, failure.httpStatusCode)
        assertFalse(connection.responseBodyRead)
        assertTrue(connection.disconnected)
    }

    @Test
    fun accountAndRateLimitResponsesHaveDeterministicSanitizedCategories() {
        listOf(
            402 to DeepSeekAuthFailureCategory.ACCOUNT_RESTRICTED,
            429 to DeepSeekAuthFailureCategory.RATE_LIMITED,
            503 to DeepSeekAuthFailureCategory.PROVIDER_UNAVAILABLE,
            302 to DeepSeekAuthFailureCategory.UNEXPECTED_HTTP_RESPONSE,
        ).forEach { (status, category) ->
            val failure = assertThrows(DeepSeekAuthenticationException::class.java) {
                runBlocking { DeepSeekModelsAuthenticationProbe { FakeConnection(status) }.validate(TEST_KEY) }
            }
            assertEquals(category, failure.category)
            assertEquals(status, failure.httpStatusCode)
            assertFalse(failure.stackTraceToString().contains(TEST_KEY))
        }
    }

    @Test
    fun transportFailureIsSanitizedWithoutPropagatingUrlOrKey() {
        val probe = DeepSeekModelsAuthenticationProbe { ThrowingConnection() }

        val failure = assertThrows(DeepSeekAuthenticationException::class.java) {
            runBlocking { probe.validate(TEST_KEY) }
        }

        assertEquals(DeepSeekAuthFailureCategory.NETWORK_UNAVAILABLE, failure.category)
        assertFalse(failure.stackTraceToString().contains(TEST_KEY))
        assertFalse(failure.stackTraceToString().contains("private-query"))
    }

    private open class FakeConnection(
        private val responseStatus: Int,
    ) : HttpURLConnection(URL("https://api.deepseek.com/models")) {
        var requestedUrl: URL = url
        var responseBodyRead = false
        var disconnected = false

        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
        override fun disconnect() {
            disconnected = true
        }

        override fun getResponseCode(): Int = responseStatus

        override fun getInputStream(): InputStream {
            responseBodyRead = true
            return ByteArrayInputStream("ignored".encodeToByteArray())
        }

        override fun getErrorStream(): InputStream {
            responseBodyRead = true
            return ByteArrayInputStream("ignored-error".encodeToByteArray())
        }
    }

    private class ThrowingConnection : FakeConnection(0) {
        override fun getResponseCode(): Int = throw IOException("private-query must not escape")
    }

    private companion object {
        const val TEST_KEY = "sk-test-network-secret-123456"
    }
}
