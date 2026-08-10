package com.example.lyriccaptioner.processing

/** JNI implementation of the explicit, process-local Whisper context lifecycle. */
object WhisperNativeSessionBridge : WhisperSessionNativeClient {
    override val isAvailable: Boolean

    init {
        isAvailable = runCatching {
            System.loadLibrary("lyriccaptioner_whisper")
        }.isSuccess
    }

    override fun createContext(modelPath: String): Long {
        checkAvailable()
        return nativeCreateContext(modelPath).also { handle ->
            check(handle > 0L) { "Whisper native context creation returned an invalid handle." }
        }
    }

    override fun transcribe(
        contextHandle: Long,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken,
    ): List<WhisperSegment> {
        checkAvailable()
        require(contextHandle > 0L) { "Whisper context handle must be positive." }
        return nativeTranscribeContext(
            contextHandle,
            audioPath,
            sampleRate,
            channels,
            cancellationToken,
        ).toList()
    }

    override fun requestAbort(contextHandle: Long) {
        checkAvailable()
        require(contextHandle > 0L) { "Whisper context handle must be positive." }
        nativeRequestAbort(contextHandle)
    }

    override fun freeContext(contextHandle: Long) {
        checkAvailable()
        if (contextHandle <= 0L) return
        nativeFreeContext(contextHandle)
    }

    private fun checkAvailable() {
        if (!isAvailable) {
            throw WhisperJniUnavailableException(
                "Whisper native library is not available. Build with enableWhisperNative before using local ASR.",
            )
        }
    }

    private external fun nativeCreateContext(modelPath: String): Long

    private external fun nativeTranscribeContext(
        contextHandle: Long,
        audioPath: String,
        sampleRate: Int,
        channels: Int,
        cancellationToken: WhisperCancellationToken,
    ): Array<WhisperSegment>

    private external fun nativeRequestAbort(contextHandle: Long)

    private external fun nativeFreeContext(contextHandle: Long)
}
