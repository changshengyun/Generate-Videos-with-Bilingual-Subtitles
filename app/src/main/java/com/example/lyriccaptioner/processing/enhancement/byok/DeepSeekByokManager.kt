package com.example.lyriccaptioner.processing.enhancement.byok

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates validation and persistence without ever exposing a plaintext key to storage. */
class DeepSeekByokManagerImpl(
    private val store: DeepSeekKeyStore,
    private val probe: DeepSeekKeyProbe,
) : DeepSeekByokManager {
    private val stateLock = Any()
    private val operationMutex = Mutex()

    @Volatile
    private var transientStatus: DeepSeekKeyStatus? = null

    override fun status(): DeepSeekKeyStatus = synchronized(stateLock) {
        val transient = transientStatus
        if (transient?.state == DeepSeekKeyState.VALIDATING_NEW_KEY) return@synchronized transient

        val record = runCatching { store.readEncrypted() }.getOrNull()
        if (record == null) {
            val status = if (transient?.state == DeepSeekKeyState.VALIDATION_FAILED) {
                transient
            } else {
                DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
            }
            transientStatus = status
            return@synchronized status
        }

        // A damaged ciphertext or an invalidated Keystore alias is recoverable by re-entry.
        val decrypted = runCatching { store.decrypt() }.getOrNull()
        val status = if (decrypted == null) {
            DeepSeekKeyStatus(DeepSeekKeyState.NEEDS_REENTRY, record.maskedKey)
        } else if (transient?.state == DeepSeekKeyState.VALIDATION_FAILED) {
            transient
        } else {
            DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, record.maskedKey)
        }
        transientStatus = status
        return@synchronized status
    }

    override suspend fun validateAndSave(apiKey: String): DeepSeekKeyStatus =
        validateAndPersist(apiKey)

    override suspend fun replace(apiKey: String): DeepSeekKeyStatus =
        validateAndPersist(apiKey)

    private suspend fun validateAndPersist(apiKey: String): DeepSeekKeyStatus = operationMutex.withLock {
        if (!DeepSeekKeyMasker.isPlausibleFormat(apiKey)) {
            return DeepSeekKeyStatus(
                DeepSeekKeyState.VALIDATION_FAILED,
                detail = "Invalid API key format.",
            ).also { transientStatus = it }
        }

        val before = status()
        transientStatus = DeepSeekKeyStatus(
            DeepSeekKeyState.VALIDATING_NEW_KEY,
            maskedKey = DeepSeekKeyMasker.mask(apiKey),
        )
        val result = try {
            // Probe is intentionally performed before encryption/write, preserving the old record.
            probe.validate(apiKey)
            store.writeEncrypted(apiKey)
            DeepSeekKeyStatus(
                DeepSeekKeyState.CONFIGURED,
                maskedKey = DeepSeekKeyMasker.mask(apiKey),
            ).also { transientStatus = it }
        } catch (cancelled: CancellationException) {
            transientStatus = before
            throw cancelled
        } catch (_: Throwable) {
            // Do not expose provider exception text (it may contain headers or request payloads).
            DeepSeekKeyStatus(
                DeepSeekKeyState.VALIDATION_FAILED,
                maskedKey = before.maskedKey,
                detail = "Validation or secure storage failed.",
            ).also { transientStatus = it }
        }
        return result
    }

    override fun cancelInput(): DeepSeekKeyStatus = synchronized(stateLock) {
        val record = runCatching { store.readEncrypted() }.getOrNull()
        val status = if (record == null) {
            DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        } else if (runCatching { store.decrypt() }.getOrNull() == null) {
            DeepSeekKeyStatus(DeepSeekKeyState.NEEDS_REENTRY, record.maskedKey)
        } else {
            DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, record.maskedKey)
        }
        transientStatus = status
        status
    }

    override fun delete(): DeepSeekKeyStatus = synchronized(stateLock) {
        runCatching { store.delete() }
        val status = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
        transientStatus = status
        status
    }

    override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T {
        val key = operationMutex.withLock {
            val record = runCatching { store.readEncrypted() }.getOrNull()
            val decrypted = runCatching { store.decrypt() }.getOrNull()
            if (decrypted == null) {
                transientStatus = if (record == null) {
                    DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
                } else {
                    DeepSeekKeyStatus(DeepSeekKeyState.NEEDS_REENTRY, record.maskedKey)
                }
                throw DeepSeekKeyUnavailableException()
            }
            decrypted
        }
        return block(key)
    }
}
