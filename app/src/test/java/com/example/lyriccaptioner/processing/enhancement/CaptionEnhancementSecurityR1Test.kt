package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.LocalTranslator
import com.example.lyriccaptioner.processing.TranslationModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionEnhancementSecurityR1Test {
    @Test
    fun providerExceptionDoesNotRetainSensitiveCauseInMessageStringOrStack() {
        val secret = "sk-test-sensitive-sentinel-never-real"
        val sensitive = "$secret Authorization: Bearer $secret content://private/media/secret complete-lyrics-batch"
        val error = CaptionEnhancementProviderException(
            CaptionEnhancementErrorKind.CONNECTION,
            "Provider connection failed.",
            IllegalStateException(sensitive),
        )

        assertNull(error.cause)
        assertFalse(error.message.orEmpty().contains(secret))
        assertFalse(error.toString().contains(secret))
        assertFalse(error.stackTraceToString().contains(secret))
    }

    @Test
    fun fallbackRecoverabilityIsAnExplicitAllowlist() {
        val allowlisted = listOf(
            CaptionEnhancementErrorKind.OFFLINE,
            CaptionEnhancementErrorKind.CONNECTION,
            CaptionEnhancementErrorKind.TIMEOUT,
            CaptionEnhancementErrorKind.RETRYABLE_SERVER,
            CaptionEnhancementErrorKind.INVALID_RESPONSE,
        )
        allowlisted.forEach { kind ->
            assertTrue(CaptionEnhancementProviderException(kind, "safe").recoverable)
        }
        listOf(
            CaptionEnhancementErrorKind.AUTHENTICATION,
            CaptionEnhancementErrorKind.UNKNOWN,
            CaptionEnhancementErrorKind.LOCAL_TRANSLATION,
        ).forEach { kind ->
            assertFalse(CaptionEnhancementProviderException(kind, "safe").recoverable)
        }
    }

    @Test
    fun unknownProviderFailureDoesNotStartLocalFallback() = runBlocking {
        val translator = RecordingTranslator()
        val provider = CaptionEnhancementProvider { throw IllegalStateException("unknown provider failure") }
        val coordinator = CaptionEnhancementCoordinator(
            provider = provider,
            localTranslation = TranslationModule(translator),
        )

        var thrown: CaptionEnhancementProviderException? = null
        try {
            coordinator.enhance("job-unknown", rawCues())
        } catch (error: CaptionEnhancementProviderException) {
            thrown = error
        }

        assertEquals(CaptionEnhancementErrorKind.UNKNOWN, thrown?.kind)
        assertEquals(0, translator.calls)
    }

    private class RecordingTranslator : LocalTranslator {
        var calls = 0
        override suspend fun translateEnglishToChinese(text: String): String {
            calls += 1
            return "local:$text"
        }
    }
}
