package com.example.lyriccaptioner.processing

import android.net.Uri
import com.example.lyriccaptioner.model.CaptionCue
import com.example.lyriccaptioner.model.ExportProfile

data class CaptionProject(
    val videoUri: Uri,
    val captions: List<CaptionCue>,
    val exportProfile: ExportProfile,
)

data class ExportResult(
    val outputUri: Uri,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val hasAudioTrack: Boolean = false,
)

interface ExportEngine {
    suspend fun export(project: CaptionProject, outputUri: Uri): ExportResult
}
