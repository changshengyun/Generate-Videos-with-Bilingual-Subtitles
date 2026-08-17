package com.example.lyriccaptioner.processing.enhancement.byok

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException

/** Android Keystore-backed AES-256-GCM storage for the user-provided DeepSeek key. */
class AndroidKeystoreDeepSeekKeyStore(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
    private val recordFileName: String = DEFAULT_RECORD_FILE_NAME,
    private val aliasDelete: (KeyStore, String) -> Unit = { keyStore, entryAlias ->
        keyStore.deleteEntry(entryAlias)
    },
) : DeepSeekKeyStore {
    init {
        require(SAFE_IDENTIFIER.matches(alias)) { "Invalid secure-key alias." }
        require(SAFE_IDENTIFIER.matches(recordFileName)) { "Invalid secure record name." }
    }

    private val appContext = context.applicationContext
    private val recordFile: File
        get() = File(appContext.noBackupFilesDir, recordFileName)

    override fun readEncrypted(): EncryptedDeepSeekKeyRecord? {
        val file = recordFile
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC) return damagedRecord()
                val version = input.readInt()
                if (version !in LEGACY_VERSION..VERSION) return damagedRecord()
                val ivLength = input.readInt()
                val cipherLength = input.readInt()
                val maskedLength = input.readInt()
                val healthIvLength = if (version >= VERSION) input.readInt() else 0
                val healthCipherLength = if (version >= VERSION) input.readInt() else 0
                if (ivLength != IV_LENGTH || cipherLength <= 0 || cipherLength > MAX_CIPHERTEXT || maskedLength !in 0..MAX_MASKED) {
                    return damagedRecord()
                }
                if (version >= VERSION &&
                    (healthIvLength != IV_LENGTH || healthCipherLength !in MIN_GCM_TAG_BYTES..MAX_HEALTH_CIPHERTEXT)
                ) {
                    return damagedRecord()
                }
                val iv = ByteArray(ivLength)
                val ciphertext = ByteArray(cipherLength)
                val masked = ByteArray(maskedLength)
                val healthIv = ByteArray(healthIvLength)
                val healthCiphertext = ByteArray(healthCipherLength)
                input.readFully(iv)
                input.readFully(ciphertext)
                input.readFully(masked)
                input.readFully(healthIv)
                input.readFully(healthCiphertext)
                if (input.read() != -1) return damagedRecord()
                EncryptedDeepSeekKeyRecord(
                    ciphertext = ciphertext,
                    iv = iv,
                    maskedKey = masked.toString(StandardCharsets.UTF_8),
                    healthCiphertext = healthCiphertext,
                    healthIv = healthIv,
                )
            }
        } catch (_: Throwable) {
            // Keep a non-null marker so the manager reports NEEDS_REENTRY rather than UNCONFIGURED.
            damagedRecord()
        }
    }

    override fun prepareWrite(apiKey: String): DeepSeekKeyWriteTransaction {
        val previousBytes = try {
            recordFile.takeIf(File::isFile)?.readBytes()
        } catch (_: Throwable) {
            throw DeepSeekKeyStorageException()
        }
        val aliasWasPresent = try {
            keyStore().containsAlias(alias)
        } catch (_: Throwable) {
            throw DeepSeekKeyStorageException()
        }
        return try {
            val key = loadOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            if (iv.size != IV_LENGTH) throw DeepSeekKeyStorageException()
            val plaintext = apiKey.toByteArray(StandardCharsets.UTF_8)
            val ciphertext = try {
                cipher.doFinal(plaintext)
            } finally {
                plaintext.fill(0)
            }
            val unauthenticated = EncryptedDeepSeekKeyRecord(
                ciphertext = ciphertext,
                iv = iv,
                maskedKey = DeepSeekKeyMasker.mask(apiKey),
            )
            val healthCipher = Cipher.getInstance(TRANSFORMATION)
            healthCipher.init(Cipher.ENCRYPT_MODE, key)
            healthCipher.updateAAD(authenticationAad(unauthenticated))
            val record = unauthenticated.copy(
                healthCiphertext = healthCipher.doFinal(EMPTY_HEALTH_PLAINTEXT),
                healthIv = healthCipher.iv,
            )
            PreparedWrite(record, previousBytes, aliasWasPresent)
        } catch (cancelled: CancellationException) {
            cleanupNewAlias(aliasWasPresent)
            throw cancelled
        } catch (_: Throwable) {
            cleanupNewAlias(aliasWasPresent)
            throw DeepSeekKeyStorageException()
        }
    }

    override fun health(): DeepSeekKeyStoreHealth {
        val record = readEncrypted() ?: return try {
            if (keyStore().containsAlias(alias)) {
                DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY)
            } else {
                DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.ABSENT)
            }
        } catch (_: Throwable) {
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY)
        }
        if (record.iv.size != IV_LENGTH ||
            record.ciphertext.isEmpty() ||
            record.healthIv.size != IV_LENGTH ||
            record.healthCiphertext.size < MIN_GCM_TAG_BYTES
        ) {
            return DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
        }
        val key = loadKey()
            ?: return DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, record.healthIv))
            cipher.updateAAD(authenticationAad(record))
            val healthPlaintext = cipher.doFinal(record.healthCiphertext)
            try {
                if (healthPlaintext.isEmpty()) {
                    DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.AVAILABLE, record.maskedKey)
                } else {
                    DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
                }
            } finally {
                healthPlaintext.fill(0)
            }
        } catch (_: Throwable) {
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
        }
    }

    override fun decrypt(): String? {
        return try {
            val record = readEncrypted() ?: return null
            if (record.iv.size != IV_LENGTH || record.ciphertext.isEmpty()) return null
            val key = loadKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, record.iv))
            val plaintext = cipher.doFinal(record.ciphertext)
            try {
                plaintext.toString(StandardCharsets.UTF_8)
            } finally {
                plaintext.fill(0)
            }
        } catch (_: Throwable) {
            // Includes malformed ciphertext and Keystore alias invalidation.
            null
        }
    }

    override fun delete() {
        try {
            val file = recordFile
            if (file.exists() && !file.delete()) throw DeepSeekKeyStorageException()
            val keyStore = keyStore()
            if (keyStore.containsAlias(alias)) aliasDelete(keyStore, alias)
            if (file.exists() || keyStore.containsAlias(alias)) throw DeepSeekKeyStorageException()
        } catch (_: Throwable) {
            throw DeepSeekKeyStorageException()
        }
    }

    private inner class PreparedWrite(
        override val record: EncryptedDeepSeekKeyRecord,
        private val previousBytes: ByteArray?,
        private val aliasWasPresent: Boolean,
    ) : DeepSeekKeyWriteTransaction {
        private var committed = false
        private var rolledBack = false

        override fun commit(commitAllowed: () -> Boolean) {
            if (rolledBack) throw DeepSeekKeyStorageException()
            if (!commitAllowed()) {
                rollback()
                throw CancellationException("Secure key write cancelled before commit.")
            }
            try {
                persistAtomically(record)
                committed = true
                if (!commitAllowed()) {
                    rollback()
                    throw CancellationException("Secure key write cancelled during commit.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                rollback()
                throw DeepSeekKeyStorageException()
            }
        }

        override fun rollback() {
            if (rolledBack) return
            try {
                if (committed) {
                    if (previousBytes == null) {
                        val file = recordFile
                        if (file.exists() && !file.delete()) throw IOException("Could not restore empty secure storage")
                    } else {
                        persistRawAtomically(previousBytes)
                    }
                }
                cleanupNewAlias(aliasWasPresent)
                rolledBack = true
            } catch (_: Throwable) {
                throw DeepSeekKeyStorageException()
            }
        }
    }

    private fun persistAtomically(record: EncryptedDeepSeekKeyRecord) =
        persistRawAtomically(encodeRecord(record))

    private fun encodeRecord(record: EncryptedDeepSeekKeyRecord): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(record.iv.size)
                output.writeInt(record.ciphertext.size)
                val maskedBytes = record.maskedKey.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(maskedBytes.size)
                output.writeInt(record.healthIv.size)
                output.writeInt(record.healthCiphertext.size)
                output.write(record.iv)
                output.write(record.ciphertext)
                output.write(maskedBytes)
                output.write(record.healthIv)
                output.write(record.healthCiphertext)
                output.flush()
            }
            bytes.toByteArray()
        }

    private fun persistRawAtomically(encoded: ByteArray) {
        val destination = recordFile
        val parent = destination.parentFile ?: throw IOException("No private storage directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create private storage directory")
        val temporary = File(parent, "$recordFileName.tmp")
        try {
            temporary.outputStream().buffered().use { output -> output.write(encoded) }
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun authenticationAad(record: EncryptedDeepSeekKeyRecord): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                val maskedBytes = record.maskedKey.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(record.iv.size)
                output.write(record.iv)
                output.writeInt(record.ciphertext.size)
                output.write(record.ciphertext)
                output.writeInt(maskedBytes.size)
                output.write(maskedBytes)
            }
            bytes.toByteArray()
        }

    private fun cleanupNewAlias(aliasWasPresent: Boolean) {
        if (aliasWasPresent) return
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } catch (_: Throwable) {
            throw DeepSeekKeyStorageException()
        }
    }

    private fun loadOrCreateKey(): SecretKey = loadKey() ?: run {
        // A permanently invalidated alias cannot be used for a new entry; replace only
        // the local AES wrapping key (the encrypted record remains untouched until commit).
        runCatching {
            val keyStore = keyStore()
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
    }

    private fun loadKey(): SecretKey? = runCatching {
        keyStore().getKey(alias, null) as? SecretKey
    }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun damagedRecord() = EncryptedDeepSeekKeyRecord(ByteArray(1), ByteArray(0), "")

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "lyriccaptioner.deepseek.byok.v1"
        const val DEFAULT_RECORD_FILE_NAME = "deepseek_byok_record.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAGIC = 0x4453424B // DSBK
        const val LEGACY_VERSION = 1
        const val VERSION = 2
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val MIN_GCM_TAG_BYTES = TAG_BITS / 8
        const val MAX_CIPHERTEXT = 1024 * 1024
        const val MAX_HEALTH_CIPHERTEXT = 1024
        const val MAX_MASKED = 256
        val EMPTY_HEALTH_PLAINTEXT = ByteArray(0)
        val SAFE_IDENTIFIER = Regex("[A-Za-z0-9._-]+")
    }
}
