package com.example.lyriccaptioner.processing.enhancement.byok

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
        if (
            transient?.state == DeepSeekKeyState.VALIDATING_NEW_KEY ||
            transient?.state == DeepSeekKeyState.TESTING_CONNECTION
        ) return@synchronized transient
        if (transient?.state == DeepSeekKeyState.NEEDS_REENTRY) return@synchronized transient
        val persistent = persistentStatus()
        val status = if (transient?.state == DeepSeekKeyState.VALIDATION_FAILED) transient else persistent
        transientStatus = status
        status
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

        val before = persistentStatus()
        transientStatus = DeepSeekKeyStatus(
            DeepSeekKeyState.VALIDATING_NEW_KEY,
            maskedKey = before.maskedKey,
        )
        var transaction: DeepSeekKeyWriteTransaction? = null
        val result = try {
            // Probe is intentionally performed before encryption/write, preserving the old record.
            probe.validate(apiKey)
            val operationContext = currentCoroutineContext()
            operationContext.ensureActive()
            transaction = store.prepareWrite(apiKey)
            operationContext.ensureActive()
            transaction.commit { operationContext.isActive }
            operationContext.ensureActive()
            DeepSeekKeyStatus(
                DeepSeekKeyState.CONFIGURED,
                maskedKey = DeepSeekKeyMasker.mask(apiKey),
                detail = "Authentication verified (HTTP 2xx).",
            ).also { transientStatus = it }
        } catch (cancelled: CancellationException) {
            try {
                transaction?.rollback()
                transientStatus = before
                throw cancelled
            } catch (_: DeepSeekKeyStorageException) {
                DeepSeekKeyStatus(
                    DeepSeekKeyState.NEEDS_REENTRY,
                    maskedKey = before.maskedKey,
                    detail = "Secure write rollback failed.",
                ).also { transientStatus = it }
                throw DeepSeekKeyStorageException()
            }
        } catch (failure: Throwable) {
            val rollbackFailed = runCatching { transaction?.rollback() }.isFailure
            if (rollbackFailed) {
                return@withLock DeepSeekKeyStatus(
                    DeepSeekKeyState.NEEDS_REENTRY,
                    maskedKey = before.maskedKey,
                    detail = "Secure write rollback failed.",
                ).also { transientStatus = it }
            }
            // Do not expose provider exception text (it may contain headers or request payloads).
            DeepSeekKeyStatus(
                DeepSeekKeyState.VALIDATION_FAILED,
                maskedKey = before.maskedKey,
                detail = safeFailureDetail(failure),
            ).also { transientStatus = it }
        }
        return result
    }

    override suspend fun testConnection(): DeepSeekKeyStatus {
        val before = persistentStatus()
        if (before.state != DeepSeekKeyState.CONFIGURED) return before
        transientStatus = DeepSeekKeyStatus(DeepSeekKeyState.TESTING_CONNECTION, before.maskedKey)
        return try {
            withDecryptedKey { apiKey -> probe.validate(apiKey) }
            DeepSeekKeyStatus(
                DeepSeekKeyState.CONFIGURED,
                before.maskedKey,
                detail = "Connection verified (HTTP 2xx).",
            ).also { transientStatus = it }
        } catch (cancelled: CancellationException) {
            transientStatus = before
            throw cancelled
        } catch (failure: Throwable) {
            val persistent = persistentStatus()
            if (persistent.state != DeepSeekKeyState.CONFIGURED) {
                persistent.also { transientStatus = it }
            } else {
                DeepSeekKeyStatus(
                    DeepSeekKeyState.VALIDATION_FAILED,
                    persistent.maskedKey,
                    detail = safeFailureDetail(failure),
                ).also { transientStatus = it }
            }
        }
    }

    override suspend fun cancelInput(): DeepSeekKeyStatus = operationMutex.withLock {
        val status = persistentStatus()
        transientStatus = status
        status
    }

    override suspend fun delete(): DeepSeekKeyStatus = operationMutex.withLock {
        val before = persistentStatus()
        try {
            store.delete()
            if (store.health().availability != DeepSeekKeyAvailability.ABSENT) {
                throw DeepSeekKeyStorageException()
            }
            DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED).also { transientStatus = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            transientStatus = DeepSeekKeyStatus(
                DeepSeekKeyState.NEEDS_REENTRY,
                before.maskedKey,
                detail = "Secure deletion failed.",
            )
            throw DeepSeekKeyStorageException()
        }
    }

    override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T = operationMutex.withLock {
        val key = try {
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        block(key)
    }

    private fun persistentStatus(): DeepSeekKeyStatus {
        val health = runCatching { store.health() }.getOrElse {
            return DeepSeekKeyStatus(DeepSeekKeyState.NEEDS_REENTRY)
        }
        return when (health.availability) {
            DeepSeekKeyAvailability.ABSENT -> DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
            DeepSeekKeyAvailability.AVAILABLE -> DeepSeekKeyStatus(
                DeepSeekKeyState.CONFIGURED,
                health.maskedKey,
            )
            DeepSeekKeyAvailability.NEEDS_REENTRY -> DeepSeekKeyStatus(
                DeepSeekKeyState.NEEDS_REENTRY,
                health.maskedKey,
            )
        }
    }

    private fun safeFailureDetail(failure: Throwable): String {
        val authenticationFailure = failure as? DeepSeekAuthenticationException
            ?: return "Validation or secure storage failed."
        val suffix = authenticationFailure.httpStatusCode?.let { " (HTTP $it)." }.orEmpty()
        return when (authenticationFailure.category) {
            DeepSeekAuthFailureCategory.AUTHENTICATION_REJECTED -> "Authentication rejected$suffix"
            DeepSeekAuthFailureCategory.ACCOUNT_RESTRICTED -> "Account or balance prevents authentication$suffix"
            DeepSeekAuthFailureCategory.RATE_LIMITED -> "Authentication rate limited$suffix"
            DeepSeekAuthFailureCategory.PROVIDER_UNAVAILABLE -> "DeepSeek service unavailable$suffix"
            DeepSeekAuthFailureCategory.NETWORK_UNAVAILABLE -> "Network connection failed."
            DeepSeekAuthFailureCategory.UNEXPECTED_HTTP_RESPONSE -> "Unexpected authentication response$suffix"
        }
    }
}
