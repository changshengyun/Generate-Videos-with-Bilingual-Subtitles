package com.example.lyriccaptioner.processing

data class ApprovedWhisperModel(
    val fileName: String,
    val sha1: String,
)

object WhisperModelCatalog {
    const val BASELINE_FILE_NAME = "ggml-base.bin"
    const val BASE_EN_FILE_NAME = "ggml-base.en.bin"
    const val SMALL_EN_Q5_1_FILE_NAME = "ggml-small.en-q5_1.bin"

    val baseline = ApprovedWhisperModel(
        fileName = BASELINE_FILE_NAME,
        sha1 = "465707469ff3a37a2b9b8d8f89f2f99de7299dac",
    )
    val baseEn = ApprovedWhisperModel(
        fileName = BASE_EN_FILE_NAME,
        sha1 = "137c40403d78fd54d454da0f9bd998f78703390c",
    )
    val smallEnQ5_1 = ApprovedWhisperModel(
        fileName = SMALL_EN_Q5_1_FILE_NAME,
        sha1 = "20f54878d608f94e4a8ee3ae56016571d47cba34",
    )

    val approved: List<ApprovedWhisperModel> = listOf(baseline, baseEn, smallEnQ5_1)

    fun find(fileName: String): ApprovedWhisperModel? = approved.firstOrNull { it.fileName == fileName }
}

object WhisperModelSelector {
    fun requireInstalled(
        fileName: String,
        installedFileNames: Set<String>,
    ): ApprovedWhisperModel {
        val model = WhisperModelCatalog.find(fileName)
            ?: throw IllegalArgumentException("The selected Whisper model is not approved: $fileName")
        require(model.fileName in installedFileNames) {
            "The approved Whisper model is not installed: ${model.fileName}"
        }
        return model
    }

    fun defaultInstalled(installedFileNames: Set<String>): ApprovedWhisperModel? =
        WhisperModelCatalog.baseline.takeIf { it.fileName in installedFileNames }
}
