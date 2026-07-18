package com.example.lyriccaptioner.processing

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object WhisperModelValidator {
    const val EXPECTED_MODEL_BYTES = 147_951_465L
    const val EXPECTED_MODEL_SHA1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac"

    fun isValid(file: File): Boolean {
        if (!file.isFile || file.length() != EXPECTED_MODEL_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-1")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex() == EXPECTED_MODEL_SHA1
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
