package com.example.lyriccaptioner.processing.enhancement.byok

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

/** Android Keystore-backed AES-256-GCM storage for the user-provided DeepSeek key. */
class AndroidKeystoreDeepSeekKeyStore(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
    private val recordFileName: String = DEFAULT_RECORD_FILE_NAME,
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
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return damagedRecord()
                val ivLength = input.readInt()
                val cipherLength = input.readInt()
                val maskedLength = input.readInt()
                if (ivLength != IV_LENGTH || cipherLength <= 0 || cipherLength > MAX_CIPHERTEXT || maskedLength !in 0..MAX_MASKED) {
                    return damagedRecord()
                }
                val iv = ByteArray(ivLength)
                val ciphertext = ByteArray(cipherLength)
                val masked = ByteArray(maskedLength)
                input.readFully(iv)
                input.readFully(ciphertext)
                input.readFully(masked)
                EncryptedDeepSeekKeyRecord(ciphertext, iv, masked.toString(StandardCharsets.UTF_8))
            }
        } catch (_: Throwable) {
            // Keep a non-null marker so the manager reports NEEDS_REENTRY rather than UNCONFIGURED.
            damagedRecord()
        }
    }

    override fun writeEncrypted(apiKey: String): EncryptedDeepSeekKeyRecord {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val iv = cipher.iv
        if (iv.size != IV_LENGTH) throw DeepSeekKeyStorageException()
        val plaintext = apiKey.toByteArray(StandardCharsets.UTF_8)
        val ciphertext = try {
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val record = EncryptedDeepSeekKeyRecord(ciphertext, iv, DeepSeekKeyMasker.mask(apiKey))
        persistAtomically(record)
        return record
    }

    override fun health(): DeepSeekKeyStoreHealth {
        val record = readEncrypted()
            ?: return DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.ABSENT)
        if (record.iv.size != IV_LENGTH || record.ciphertext.isEmpty()) {
            return DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
        }
        val key = loadKey()
            ?: return DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.NEEDS_REENTRY, record.maskedKey)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, record.iv))
            val plaintext = cipher.doFinal(record.ciphertext)
            plaintext.fill(0)
            DeepSeekKeyStoreHealth(DeepSeekKeyAvailability.AVAILABLE, record.maskedKey)
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
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            if (file.exists() || keyStore.containsAlias(alias)) throw DeepSeekKeyStorageException()
        } catch (_: Throwable) {
            throw DeepSeekKeyStorageException()
        }
    }

    private fun persistAtomically(record: EncryptedDeepSeekKeyRecord) {
        val destination = recordFile
        val parent = destination.parentFile ?: throw IOException("No private storage directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create private storage directory")
        val temporary = File(parent, "$recordFileName.tmp")
        try {
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(record.iv.size)
                output.writeInt(record.ciphertext.size)
                val maskedBytes = record.maskedKey.toByteArray(StandardCharsets.UTF_8)
                output.writeInt(maskedBytes.size)
                output.write(record.iv)
                output.write(record.ciphertext)
                output.write(maskedBytes)
                output.flush()
            }
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
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
        const val VERSION = 1
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val MAX_CIPHERTEXT = 1024 * 1024
        const val MAX_MASKED = 256
        val SAFE_IDENTIFIER = Regex("[A-Za-z0-9._-]+")
    }
}
