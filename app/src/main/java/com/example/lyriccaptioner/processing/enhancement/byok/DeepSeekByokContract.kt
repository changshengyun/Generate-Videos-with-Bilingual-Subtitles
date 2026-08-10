package com.example.lyriccaptioner.processing.enhancement.byok

const val DEEPSEEK_PROVIDER = "DeepSeek"
const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"

enum class DeepSeekKeyState {
    UNCONFIGURED,
    INPUT_NEW_KEY,
    VALIDATING_NEW_KEY,
    CONFIGURED,
    VALIDATION_FAILED,
    NEEDS_REENTRY,
}

data class DeepSeekKeyStatus(
    val state: DeepSeekKeyState,
    val maskedKey: String? = null,
    val detail: String? = null,
)

data class DeepSeekKeyUiModel(
    val provider: String = DEEPSEEK_PROVIDER,
    val baseUrl: String = DEEPSEEK_BASE_URL,
    val state: DeepSeekKeyState,
    val maskedKey: String? = null,
    val showSave: Boolean,
    val showReplace: Boolean,
    val showDelete: Boolean,
    val showCancel: Boolean,
)

object DeepSeekKeyUiMapper {
    fun from(status: DeepSeekKeyStatus): DeepSeekKeyUiModel = DeepSeekKeyUiModel(
        state = status.state,
        maskedKey = status.maskedKey,
        showSave = status.state != DeepSeekKeyState.CONFIGURED,
        showReplace = status.state == DeepSeekKeyState.CONFIGURED,
        showDelete = status.state == DeepSeekKeyState.CONFIGURED || status.state == DeepSeekKeyState.NEEDS_REENTRY,
        showCancel = status.state == DeepSeekKeyState.VALIDATING_NEW_KEY ||
            status.state == DeepSeekKeyState.CONFIGURED ||
            status.state == DeepSeekKeyState.VALIDATION_FAILED ||
            status.state == DeepSeekKeyState.NEEDS_REENTRY,
    )
}

data class EncryptedDeepSeekKeyRecord(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val maskedKey: String,
) {
    override fun equals(other: Any?): Boolean = other is EncryptedDeepSeekKeyRecord &&
        ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv) && maskedKey == other.maskedKey

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + 17 * iv.contentHashCode() + maskedKey.hashCode()
}

interface DeepSeekKeyStore {
    fun readEncrypted(): EncryptedDeepSeekKeyRecord?
    fun health(): DeepSeekKeyStoreHealth
    fun writeEncrypted(apiKey: String): EncryptedDeepSeekKeyRecord
    fun decrypt(): String?
    fun delete()
}

enum class DeepSeekKeyAvailability {
    ABSENT,
    AVAILABLE,
    NEEDS_REENTRY,
}

data class DeepSeekKeyStoreHealth(
    val availability: DeepSeekKeyAvailability,
    val maskedKey: String? = null,
)

fun interface DeepSeekKeyProbe {
    suspend fun validate(apiKey: String)
}

interface DeepSeekByokManager {
    fun status(): DeepSeekKeyStatus
    suspend fun validateAndSave(apiKey: String): DeepSeekKeyStatus
    suspend fun replace(apiKey: String): DeepSeekKeyStatus
    suspend fun cancelInput(): DeepSeekKeyStatus
    suspend fun delete(): DeepSeekKeyStatus
    suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T
}

class DeepSeekKeyUnavailableException(
    message: String = "DeepSeek API key is not configured.",
) : IllegalStateException(message)

class DeepSeekKeyStorageException : IllegalStateException("Secure API key operation failed.")

object DeepSeekKeyMasker {
    fun mask(apiKey: String): String = "••••••••" + apiKey.takeLast(4)

    fun isPlausibleFormat(apiKey: String): Boolean =
        apiKey.matches(Regex("sk-[A-Za-z0-9_-]{8,}"))
}
