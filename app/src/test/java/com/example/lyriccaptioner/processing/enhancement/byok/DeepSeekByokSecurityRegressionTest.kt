package com.example.lyriccaptioner.processing.enhancement.byok

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun r1R01CancellationDuringEncryptionPreparationNeverCommitsInitialRecord() = runBlocking {
        val store = TrackingStore().apply { blockPreparation = true }
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })

        val validation = async(Dispatchers.Default) { manager.validateAndSave(NEW_KEY) }
        assertTrue(store.prepareEntered.await(5, TimeUnit.SECONDS))
        validation.cancel()
        store.prepareRelease.countDown()
        validation.join()

        assertTrue(validation.isCancelled)
        assertEquals(0, store.writeCount.get())
        assertNull(store.readEncrypted())
        assertEquals(DeepSeekKeyState.UNCONFIGURED, manager.status().state)
    }

    @Test
    fun r1R02CancellationAtCommitBoundaryPreservesOldRecord() = runBlocking {
        val store = TrackingStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave(OLD_KEY)
        val oldRecord = requireNotNull(store.readEncrypted())
        store.blockCommit = true

        val replacement = async(Dispatchers.Default) { manager.replace(NEW_KEY) }
        assertTrue(store.commitEntered.await(5, TimeUnit.SECONDS))
        replacement.cancel()
        store.commitRelease.countDown()
        replacement.join()

        val preserved = requireNotNull(store.readEncrypted())
        assertTrue(replacement.isCancelled)
        assertEquals(1, store.writeCount.get())
        assertEquals(oldRecord, preserved)
        assertEquals(OLD_KEY, store.decrypt())
        assertEquals(DeepSeekKeyState.CONFIGURED, manager.status().state)
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
        val deletion = async { manager.delete() }
        delay(25)
        assertFalse(deletion.isCompleted)
        probe.release.complete(Unit)
        replacement.await()
        val deleted = deletion.await()

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

        val failure = runCatching { manager.delete() }.exceptionOrNull()
        assertTrue(failure is DeepSeekKeyStorageException)

        val rendered = requireNotNull(failure).stackTraceToString()
        assertFalse(rendered.contains(secret))
        assertFalse(rendered.contains("private/path"))
        assertTrue(store.readEncrypted() != null)
        assertFalse(manager.status().state == DeepSeekKeyState.UNCONFIGURED)
    }

    @Test
    fun r1R04AliasDeleteFailureAfterRecordRemovalStaysNeedsReentry() = runBlocking {
        val store = TrackingStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave(OLD_KEY)
        store.failAliasDeleteAfterRecordRemoval = true

        val failure = runCatching { manager.delete() }.exceptionOrNull()

        assertTrue(failure is DeepSeekKeyStorageException)
        assertNull(store.readEncrypted())
        assertTrue(store.aliasPresent)
        assertEquals(DeepSeekKeyState.NEEDS_REENTRY, manager.status().state)
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

    @Test
    fun replacementWriteFailurePreservesOldCiphertextIvMaskAndPlaintext() = runBlocking {
        val store = TrackingStore()
        val manager = DeepSeekByokManagerImpl(store, DeepSeekKeyProbe { })
        manager.validateAndSave(OLD_KEY)
        val before = requireNotNull(store.readEncrypted())
        store.writeFailure = true

        val result = manager.replace(NEW_KEY)

        val after = requireNotNull(store.readEncrypted())
        assertEquals(DeepSeekKeyState.VALIDATION_FAILED, result.state)
        assertArrayEquals(before.ciphertext, after.ciphertext)
        assertArrayEquals(before.iv, after.iv)
        assertEquals(before.maskedKey, after.maskedKey)
        assertEquals(OLD_KEY, store.decrypt())
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
        var writeFailure = false
        var blockPreparation = false
        var blockCommit = false
        var failAliasDeleteAfterRecordRemoval = false
        var aliasPresent = false
            private set
        val prepareEntered = CountDownLatch(1)
        val prepareRelease = CountDownLatch(1)
        val commitEntered = CountDownLatch(1)
        val commitRelease = CountDownLatch(1)
        val writeCount = AtomicInteger(0)
        val decryptCount = AtomicInteger(0)
        val activeWrites = AtomicInteger(0)
        val maxActiveWrites = AtomicInteger(0)

        override fun readEncrypted(): EncryptedDeepSeekKeyRecord? = record

        override fun health(): DeepSeekKeyStoreHealth = if (record == null) {
            DeepSeekKeyStoreHealth(
                if (aliasPresent) DeepSeekKeyAvailability.NEEDS_REENTRY else DeepSeekKeyAvailability.ABSENT,
            )
        } else {
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.AVAILABLE, record?.maskedKey)
        }

        override fun prepareWrite(apiKey: String): DeepSeekKeyWriteTransaction {
            if (writeFailure) throw IllegalStateException("private/write/path/$apiKey")
            val previousRecord = record
            val previousPlaintext = plaintext
            val previousAliasPresent = aliasPresent
            aliasPresent = true
            val sequence = writeCount.get() + 1
            val iv = ByteArray(12) { index -> (sequence + index).toByte() }
            val preparedRecord = EncryptedDeepSeekKeyRecord(
                ciphertext = apiKey.encodeToByteArray().mapIndexed { index, byte ->
                    (byte.toInt() xor iv[index % iv.size].toInt()).toByte()
                }.toByteArray(),
                iv = iv,
                maskedKey = DeepSeekKeyMasker.mask(apiKey),
            )
            if (blockPreparation) {
                prepareEntered.countDown()
                check(prepareRelease.await(5, TimeUnit.SECONDS))
            }
            return object : DeepSeekKeyWriteTransaction {
                private var committed = false
                private var rolledBack = false

                override val record = preparedRecord

                override fun commit(commitAllowed: () -> Boolean) {
                    if (blockCommit) {
                        commitEntered.countDown()
                        check(commitRelease.await(5, TimeUnit.SECONDS))
                    }
                    if (!commitAllowed()) {
                        rollback()
                        throw kotlinx.coroutines.CancellationException("cancelled before fake commit")
                    }
                    val active = activeWrites.incrementAndGet()
                    maxActiveWrites.updateAndGet { maxOf(it, active) }
                    try {
                        if (writeDelayMs > 0) Thread.sleep(writeDelayMs)
                        writeCount.incrementAndGet()
                        this@TrackingStore.record = preparedRecord
                        plaintext = apiKey
                        committed = true
                        if (!commitAllowed()) {
                            rollback()
                            throw kotlinx.coroutines.CancellationException("cancelled during fake commit")
                        }
                    } finally {
                        activeWrites.decrementAndGet()
                    }
                }

                override fun rollback() {
                    if (rolledBack) return
                    if (committed) {
                        this@TrackingStore.record = previousRecord
                        plaintext = previousPlaintext
                        writeCount.decrementAndGet()
                    }
                    aliasPresent = previousAliasPresent
                    committed = false
                    rolledBack = true
                }
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
            if (failAliasDeleteAfterRecordRemoval) {
                throw IllegalStateException("synthetic alias delete failure")
            }
            aliasPresent = false
        }
    }

    private companion object {
        const val OLD_KEY = "sk-test-old-key-123456"
        const val NEW_KEY = "sk-test-new-key-123456"
        const val THIRD_KEY = "sk-test-third-key-123456"
    }
}
