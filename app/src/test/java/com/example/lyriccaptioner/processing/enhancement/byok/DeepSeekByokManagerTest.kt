package com.example.lyriccaptioner.processing.enhancement.byok

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekByokManagerTest {
    @Test
    fun firstValidatedSaveEncryptsAndRoundTripsWithoutPlaintextRecord() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        val key = "sk-test-sensitive-sentinel-never-real"

        val status = manager.validateAndSave(key)

        assertEquals(DeepSeekKeyState.CONFIGURED, status.state)
        assertEquals(DeepSeekKeyMasker.mask(key), status.maskedKey)
        assertEquals(key, store.decrypt())
        assertFalse(store.readEncrypted()!!.ciphertext.decodeToString().contains(key))
    }

    @Test
    fun everyWriteUsesANewRandomIv() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave("sk-test-first-key-123456")
        val first = store.readEncrypted()!!.iv
        manager.replace("sk-test-second-key-123456")
        val second = store.readEncrypted()!!.iv
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun initialValidationFailureLeavesNoRecord() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { error("invalid") })
        val status = manager.validateAndSave("sk-test-invalid-key-123456")
        assertEquals(DeepSeekKeyState.VALIDATION_FAILED, status.state)
        assertNull(store.readEncrypted())
    }

    @Test
    fun replacementSuccessAtomicallySwitchesToNewRecord() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave("sk-test-old-key-123456")
        val status = manager.replace("sk-test-new-key-123456")
        assertEquals(DeepSeekKeyState.CONFIGURED, status.state)
        assertEquals("sk-test-new-key-123456", store.decrypt())
    }

    @Test
    fun replacementValidationFailurePreservesOldKey() = runBlocking {
        val store = FakeStore()
        var shouldFail = false
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { if (shouldFail) error("invalid") })
        manager.validateAndSave("sk-test-old-key-123456")
        shouldFail = true
        val status = manager.replace("sk-test-bad-key-123456")
        assertEquals(DeepSeekKeyState.VALIDATION_FAILED, status.state)
        assertEquals("sk-test-old-key-123456", store.decrypt())
    }

    @Test
    fun cancelInputPreservesExistingKey() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave("sk-test-old-key-123456")
        assertEquals(DeepSeekKeyState.CONFIGURED, manager.cancelInput().state)
        assertEquals("sk-test-old-key-123456", store.decrypt())
    }

    @Test
    fun deleteRemovesCiphertextIvAndMaskedMetadata() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave("sk-test-delete-key-123456")
        assertEquals(DeepSeekKeyState.UNCONFIGURED, manager.delete().state)
        assertNull(store.readEncrypted())
    }

    @Test
    fun missingKeyBlocksDecryption() = runBlocking {
        val manager = DeepSeekByokManagerImpl(FakeStore(), DeepSeekKeyProbe { })
        assertThrows(DeepSeekKeyUnavailableException::class.java) {
            runBlocking { manager.withDecryptedKey { error("must not run") } }
        }
    }

    @Test
    fun corruptedCiphertextEntersNeedsReentryWithoutCrashing() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave("sk-test-corrupt-key-123456")
        store.corrupt = true
        assertEquals(DeepSeekKeyState.NEEDS_REENTRY, manager.status().state)
        assertThrows(DeepSeekKeyUnavailableException::class.java) {
            runBlocking { manager.withDecryptedKey { "unreachable" } }
        }
    }

    @Test
    fun concurrentReplacementIsSerializedAndLeavesOneCompleteRecord() = runBlocking {
        val store = FakeStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { delay(5) })
        manager.validateAndSave("sk-test-base-key-123456")
        val results = listOf("sk-test-a-key-123456", "sk-test-b-key-123456").map { key ->
            async { manager.replace(key) }
        }.awaitAll()
        assertTrue(results.all { it.state == DeepSeekKeyState.CONFIGURED })
        assertTrue(store.decrypt() in setOf("sk-test-a-key-123456", "sk-test-b-key-123456"))
        assertEquals(1, store.activeWrites)
    }

    @Test
    fun malformedKeyShapeNeverInvokesProbeOrWrites() = runBlocking {
        val store = FakeStore()
        var probes = 0
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { probes++ })
        val status = manager.validateAndSave("not-a-deepseek-key")
        assertEquals(DeepSeekKeyState.VALIDATION_FAILED, status.state)
        assertEquals(0, probes)
        assertNull(store.readEncrypted())
    }

    private class FakeStore : DeepSeekKeyStore {
        private var record: EncryptedDeepSeekKeyRecord? = null
        private var plainForTest: String? = null
        var corrupt = false
        var activeWrites = 0
            private set

        override fun readEncrypted(): EncryptedDeepSeekKeyRecord? = record

        override fun writeEncrypted(apiKey: String): EncryptedDeepSeekKeyRecord {
            check(activeWrites == 0)
            activeWrites += 1
            try {
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val ciphertext = apiKey.toByteArray().mapIndexed { index, value -> (value.toInt() xor 0x5A xor iv[index % iv.size].toInt()).toByte() }.toByteArray()
                return EncryptedDeepSeekKeyRecord(ciphertext, iv, DeepSeekKeyMasker.mask(apiKey)).also {
                    record = it
                    plainForTest = apiKey
                }
            } finally {
                activeWrites -= 1
            }
        }

        override fun decrypt(): String? = if (corrupt) null else plainForTest
        override fun delete() { record = null; plainForTest = null }
    }
}
