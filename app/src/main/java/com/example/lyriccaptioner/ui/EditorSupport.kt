package com.example.lyriccaptioner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.lyriccaptioner.model.EditorState

internal fun shareExportedVideo(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "分享双语视频"))
}

internal fun buildEditorSnapshot(state: EditorState): String = buildString {
    append("video=").append(if (state.videoUri == null) "none" else "present")
    append(";duration=").append(state.videoDurationMs)
    append(";media=").append(state.mediaState)
    append(";requiresAssociation=").append(state.requiresVideoAssociation)
    append(";export=").append(if (state.exportUri == null) "none" else "present")
    append(";style=").append(state.exportProfile.subtitleStyle)
    append(";layout=").append(state.captionLayout)
    append(";defaultStyle=").append(state.defaultCaptionStyle)
    append(";caption_count=").append(state.captions.size)
    append(";captions=")
    state.captions.forEach { cue ->
        append(cue.id).append(',')
            .append(cue.startMs).append(',')
            .append(cue.endMs).append(',')
            .append(cue.english).append(',')
            .append(cue.chinese).append(',')
            .append(cue.confirmed).append('|')
    }
}

internal fun localizeStatus(status: String): String {
    return when {
        status.isBlank() -> "等待操作"
        status.startsWith("Import a video") -> "导入 5 分钟以内的视频开始编辑"
        status.startsWith("Checking video access") -> "正在检查视频访问权限…"
        status.startsWith("Video imported with persistent") -> "视频已导入：已保留持久访问权限"
        status.startsWith("Video imported for this session only") -> "视频已导入：仅本次会话可用"
        status.startsWith("Video imported") -> "视频已导入，可继续识别或编辑"
        status.startsWith("Video re-associated and persisted") -> "视频已重新绑定：已保留持久访问权限"
        status.startsWith("Video re-associated") -> "视频已重新绑定"
        status.startsWith("Could not import video") -> "视频导入失败：${status.substringAfter(": ", "未知原因")}"
        status.startsWith("Preparing") -> "正在准备本地翻译模型…"
        status.startsWith("Translated") -> "中文翻译完成：${status.substringAfter("Translated ").substringBefore(" captions") } 条"
        status.startsWith("Translation") -> "翻译状态：${status.substringAfter(": ", status)}"
        status.startsWith("Created") -> "字幕已生成：${status.substringAfter("Created ").substringBefore(" lyric captions")} 条"
        status.startsWith("Rendering") -> "正在渲染双语字幕…"
        status.startsWith("Export saved") -> "视频导出完成"
        status.startsWith("Video export") -> "导出状态：${status.substringAfter(": ", status)}"
        status.startsWith("ASR") -> "识别状态：${status.substringAfter(": ", status)}"
        status.startsWith("Project restored; video access is session-only") -> "项目已恢复：视频仅本次会话可用"
        status.startsWith("Project restored with persistent") -> "项目已恢复：视频持久访问有效"
        status.startsWith("Project restored; video is unavailable") -> "项目已恢复：视频不可用，请重新绑定视频"
        status.startsWith("Project restored without a video") -> "项目已恢复：没有视频，请先绑定视频"
        status.startsWith("Project restored") -> "项目已恢复：${status.substringAfter(": ", status)}"
        status.startsWith("Project") -> "项目状态：${status.substringAfter(": ", status)}"
        status.startsWith("SRT") -> "SRT 状态：${status.substringAfter(": ", status)}"
        else -> status
    }
}
