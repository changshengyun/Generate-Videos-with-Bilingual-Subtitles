package com.example.lyriccaptioner.processing.enhancement

import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekByokManager
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyState
import com.example.lyriccaptioner.processing.enhancement.byok.DeepSeekKeyStatus
import java.io.File

/**
 * Reads the project-local `.env` so the isolated enhancement flows can run against the real
 * DeepSeek endpoint without the Android keystore. The file is git-ignored; only the variable
 * names are part of the contract. Walks upward from the test working directory so the file is
 * found whether Gradle runs from the module or the project root.
 */
object EnhancementTestEnv {
    val projectRoot: File
        get() = locateEnvFile()?.parentFile ?: File(System.getProperty("user.dir") ?: ".")

    val isConfigured: Boolean
        get() = deepSeekApiKey() != null

    fun deepSeekApiKey(): String? = values["DEEPSEEK_API_KEY"]?.takeIf { it.isNotBlank() }

    fun deepSeekModel(): String? = values["DEEPSEEK_MODEL"]?.takeIf { it.isNotBlank() }

    private val values: Map<String, String> by lazy {
        locateEnvFile()?.readLines().orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val index = line.indexOf('=')
                line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
    }

    private fun locateEnvFile(): File? {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(6) {
            val current = directory ?: return null
            val candidate = File(current, ".env")
            if (candidate.isFile) return candidate
            directory = current.parentFile
        }
        return null
    }
}

/** Supplies the `.env` key to the isolated enhancement flows in place of the Android keystore. */
class EnvFileDeepSeekByokManager : DeepSeekByokManager {
    private val key = EnhancementTestEnv.deepSeekApiKey()

    override fun status(): DeepSeekKeyStatus = if (key != null) {
        DeepSeekKeyStatus(DeepSeekKeyState.CONFIGURED, key.take(6) + "***")
    } else {
        DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)
    }

    override suspend fun validateAndSave(apiKey: String): DeepSeekKeyStatus = status()
    override suspend fun replace(apiKey: String): DeepSeekKeyStatus = status()
    override suspend fun testConnection(): DeepSeekKeyStatus = status()
    override suspend fun cancelInput(): DeepSeekKeyStatus = status()
    override suspend fun delete(): DeepSeekKeyStatus = DeepSeekKeyStatus(DeepSeekKeyState.UNCONFIGURED)

    override suspend fun <T> withDecryptedKey(block: suspend (String) -> T): T {
        val plaintext = requireNotNull(key) {
            "DEEPSEEK_API_KEY is missing; fill it in the project .env file."
        }
        return block(plaintext)
    }
}
