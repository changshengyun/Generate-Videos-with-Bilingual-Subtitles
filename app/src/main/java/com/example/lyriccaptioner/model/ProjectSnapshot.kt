package com.example.lyriccaptioner.model

data class ProjectSnapshot(
    val videoUri: String?,
    val videoDurationMs: Long?,
    val captions: List<CaptionCue>,
    val exportProfile: ExportProfile,
    val captionProcessing: CaptionProcessingSnapshot = CaptionProcessingSnapshot(),
)
