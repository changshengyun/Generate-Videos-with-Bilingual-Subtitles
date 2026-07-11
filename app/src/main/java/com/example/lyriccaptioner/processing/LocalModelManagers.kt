package com.example.lyriccaptioner.processing

class WhisperModelManager : LocalModelManager {
    override suspend fun status(): List<LocalModelStatus> {
        return listOf(
            LocalModelStatus(
                name = "whisper.cpp tiny/base English model",
                ready = false,
                detail = "Native model download and JNI loading are not wired yet.",
            ),
        )
    }
}

class DemoMlKitTranslationModelManager : LocalModelManager {
    override suspend fun status(): List<LocalModelStatus> {
        return listOf(
            LocalModelStatus(
                name = "ML Kit English to Chinese translation model",
                ready = false,
                detail = "Model availability should be checked before offline translation.",
            ),
        )
    }
}
