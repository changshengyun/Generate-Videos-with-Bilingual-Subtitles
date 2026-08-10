package com.example.lyriccaptioner.processing.enhancement.byok

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekByokSecurityRegressionTest {
    @Test
    fun r1R01InFlightInitialSaveCancellationNeverWrites() = runBlocking {
        val store = TrackingStore()
        val probe = HangingProbe()
        val manager = DeepSeekByokManagerImpl(store, probe)

        val validation = async { manager.validateAndSave(NEW_KEY) }
        probe.entered.await()
        validation.cancelAndJoin()
        val cancelledStatus = manager.cancelInput()
        probe.release.complete(Unit)

        assertTrue(validation.isCancelled)
        assertEquals(0, store.writeCount.get())
        assertEquals(DeepSeekKeyState.UNCONFIGURED, cancelledStatus.state)
        assertEquals(DeepSeekKeyState.UNCONFIGURED, manager.status().state)
        assertNull(store.readEncrypted())
    }

    @Test
    fun r1R02InFlightReplacementCancellationPreservesOldRecord() = runBlocking {
        val store = TrackingStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave(OLD_KEY)
        val oldRecord = requireNotNull(store.readEncrypted())
        val oldCiphertext = oldRecord.ciphertext.copyOf()
        val oldIv = oldRecord.iv.copyOf()
        val oldMasked = oldRecord.maskedKey
        val probe = HangingProbe()
        val replacementManager = DeepSeekByokManagerImpl(store, probe)

        val replacement = async { replacementManager.replace(NEW_KEY) }
        probe.entered.await()
        replacement.cancelAndJoin()
        val cancelledStatus = replacementManager.cancelInput()
        probe.release.complete(Unit)

        val preserved = requireNotNull(store.readEncrypted())
        assertTrue(replacement.isCancelled)
        assertEquals(1, store.writeCount.get())
        assertArrayEquals(oldCiphertext, preserved.ciphertext)
        assertArrayEquals(oldIv, preserved.iv)
        assertEquals(oldMasked, preserved.maskedKey)
        assertEquals(OLD_KEY, store.decrypt())
        assertEquals(DeepSeekKeyState.CONFIGURED, cancelledStatus.state)
        assertEquals(oldMasked, cancelledStatus.maskedKey)
    }

    @Test
    fun r1R03DeleteDuringValidationCannotBeFollowedByWriteBack() = runBlocking {
        val store = TrackingStore()
        val initialManager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        initialManager.validateAndSave(OLD_KEY)
        val probe = HangingProbe()
        val manager = DeepSeekByokManagerImpl(store, probe)

        val replacement = async { manager.replace(NEW_KEY) }
        probe.entered.await()
        val deleted = manager.delete()
        probe.release.complete(Unit)
        replacement.join()

        assertEquals(DeepSeekKeyState.UNCONFIGURED, deleted.state)
        assertNull(store.readEncrypted())
        assertEquals(DeepSeekKeyState.UNCONFIGURED, manager.status().state)
    }

    @Test
    fun r1R04DeleteFailureIsVisibleAndSanitized() = runBlocking {
        val secret = "sk-test-delete-failure-secret"
        val store = TrackingStore().apply {
            writeEncrypted(secret)
            deleteFailure = IllegalStateException("private/path/$secret")
        }
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })

        val failure = assertThrows(RuntimeException::class.java) { manager.delete() }

        val rendered = failure.stackTraceToString()
        assertFalse(rendered.contains(secret))
        assertFalse(rendered.contains("private/path"))
        assertTrue(store.readEncrypted() != null)
        assertFalse(manager.status().state == DeepSeekKeyState.UNCONFIGURED)
    }

    @Test
    fun r1R05StatusAndCancelDoNotDecryptPlaintext() = runBlocking {
        val store = TrackingStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave(OLD_KEY)
        store.decryptCount.set(0)

        assertEquals(DeepSeekKeyState.CONFIGURED, manager.status().state)
        assertEquals(DeepSeekKeyState.CONFIGURED, manager.cancelInput().state)
        assertEquals(0, store.decryptCount.get())

        val suffix = manager.withDecryptedKey { it.takeLast(4) }
        assertEquals(OLD_KEY.takeLast(4), suffix)
        assertEquals(1, store.decryptCount.get())
    }

    @Test
    fun r1R06ReplacementOperationsRemainSerialized() = runBlocking {
        val store = TrackingStore(writeDelayMs = 25)
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { delay(10) })
        manager.validateAndSave(OLD_KEY)

        val replacements = listOf(NEW_KEY, THIRD_KEY).map { key ->
            async { manager.replace(key) }
        }
        replacements.forEach { it.await() }

        assertEquals(1, store.maxActiveWrites.get())
        assertEquals(3, store.writeCount.get())
        assertTrue(store.decrypt() in setOf(NEW_KEY, THIRD_KEY))
    }

    private class HangingProbe : DeepSeekKeyProbe {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun validate(apiKey: String) {
            entered.complete(Unit)
            release.await()
        }
    }

    private class TrackingStore(
        private val writeDelayMs: Long = 0,
    ) : DeepSeekKeyStore {
        private var record: EncryptedDeepSeekKeyRecord? = null
        private var plaintext: String? = null
        var deleteFailure: RuntimeException? = null
        val writeCount = AtomicInteger(0)
        val decryptCount = AtomicInteger(0)
        val activeWrites = AtomicInteger(0)
        val maxActiveWrites = AtomicInteger(0)

        override fun readEncrypted(): EncryptedDeepSeekKeyRecord? = record

        override fun writeEncrypted(apiKey: String): EncryptedDeepSeekKeyRecord {
            val active = activeWrites.incrementAndGet()
            maxActiveWrites.updateAndGet { maxOf(it, active) }
            try {
                if (writeDelayMs > 0) Thread.sleep(writeDelayMs)
                val sequence = writeCount.incrementAndGet()
                val iv = ByteArray(12) { index -> (sequence + index).toByte() }
                val ciphertext = apiKey.encodeToByteArray().mapIndexed { index, byte ->
                    (byte.toInt() xor iv[index % iv.size].toInt()).toByte()
                }.toByteArray()
                return EncryptedDeepSeekKeyRecord(ciphertext, iv, DeepSeekKeyMasker.mask(apiKey)).also {
                    record = it
                    plaintext = apiKey
                }
            } finally {
                activeWrites.decrementAndGet()
            }
        }

        override fun decrypt(): String? {
            decryptCount.incrementAndGet()
            return plaintext
        }

        override fun delete() {
            deleteFailure?.let { throw it }
            record = null
            plaintext = null
        }
    }

    private companion object {
        const val OLD_KEY = "sk-test-old-key-123456"
        const val NEW_KEY = "sk-test-new-key-123456"
        const val THIRD_KEY = "sk-test-third-key-123456"
    }
}
