package com.example.lyriccaptioner.model

object DerivedOutputPolicy {
    fun invalidateDerivedOutputs(state: EditorState): EditorState = state.copy(
        exportUri = null,
        exportState = ExportState.IDLE,
    )
}
