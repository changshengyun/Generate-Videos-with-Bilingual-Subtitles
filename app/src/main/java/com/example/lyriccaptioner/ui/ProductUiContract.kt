package com.example.lyriccaptioner.ui

/** Product sections; diagnostic/development screens are intentionally absent. */
enum class ProductUiSection {
    IMPORT,
    PROCESSING,
    EDITOR,
    EXPORT,
}

enum class ProductUiOperation {
    IMPORT,
    RECOGNITION,
    SAVE_PROJECT,
    EXPORT,
}

enum class ProductUiStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    CANCELLED,
    FAILED,
    SAVED,
    EXPORTED,
}

data class ProductUiState(
    val section: ProductUiSection = ProductUiSection.IMPORT,
    val operation: ProductUiOperation? = null,
    val lastOperation: ProductUiOperation? = null,
    val status: ProductUiStatus = ProductUiStatus.IDLE,
    val captionCount: Int = 0,
) {
    val captionsVisible: Boolean
        get() = section == ProductUiSection.EDITOR

    val canEnterEditor: Boolean
        get() = operation == null && status == ProductUiStatus.SUCCESS && captionCount > 0

    val canRetry: Boolean
        get() = operation == null &&
            (status == ProductUiStatus.CANCELLED || status == ProductUiStatus.FAILED) &&
            lastOperation != null

    /** Repeated work actions are blocked while an operation owns the UI. */
    val blocksConflictingActions: Boolean
        get() = operation != null
}

sealed class ProductUiEvent {
    data object StartImport : ProductUiEvent()
    data object ImportCancelled : ProductUiEvent()
    data object ImportFailed : ProductUiEvent()

    data object StartRecognition : ProductUiEvent()
    data class RecognitionSucceeded(val captionCount: Int) : ProductUiEvent()
    data object RecognitionCancelled : ProductUiEvent()
    data object RecognitionFailed : ProductUiEvent()

    data object EnterEditor : ProductUiEvent()
    data object StartSave : ProductUiEvent()
    data object SaveSucceeded : ProductUiEvent()
    data object SaveFailed : ProductUiEvent()
    data object StartExport : ProductUiEvent()
    data object ExportSucceeded : ProductUiEvent()
    data object ExportFailed : ProductUiEvent()
    data object Retry : ProductUiEvent()
    data class SelectSection(val section: ProductUiSection) : ProductUiEvent()
}

/**
 * Pure product navigation/status reducer.  The real UI and ViewModel own
 * effects; this contract only describes which visible state transitions are
 * legal and makes conflict/cancel/retry semantics independently testable.
 */
object ProductUiContract {
    fun reduce(state: ProductUiState, event: ProductUiEvent): ProductUiState {
        return when (event) {
            ProductUiEvent.StartImport -> start(state, ProductUiOperation.IMPORT, ProductUiSection.IMPORT)
            ProductUiEvent.StartRecognition -> start(state, ProductUiOperation.RECOGNITION, state.section)
            ProductUiEvent.StartSave -> start(state, ProductUiOperation.SAVE_PROJECT, state.section)
            ProductUiEvent.StartExport -> start(state, ProductUiOperation.EXPORT, ProductUiSection.EXPORT)

            ProductUiEvent.ImportCancelled -> finish(state, ProductUiOperation.IMPORT, ProductUiStatus.CANCELLED)
            ProductUiEvent.ImportFailed -> finish(state, ProductUiOperation.IMPORT, ProductUiStatus.FAILED)
            ProductUiEvent.RecognitionCancelled -> finish(state, ProductUiOperation.RECOGNITION, ProductUiStatus.CANCELLED)
            ProductUiEvent.RecognitionFailed -> finish(state, ProductUiOperation.RECOGNITION, ProductUiStatus.FAILED)
            ProductUiEvent.SaveFailed -> finish(state, ProductUiOperation.SAVE_PROJECT, ProductUiStatus.FAILED)
            ProductUiEvent.ExportFailed -> finish(state, ProductUiOperation.EXPORT, ProductUiStatus.FAILED)

            is ProductUiEvent.RecognitionSucceeded -> {
                if (state.operation != ProductUiOperation.RECOGNITION) state
                else state.copy(
                    operation = null,
                    status = ProductUiStatus.SUCCESS,
                    captionCount = event.captionCount.coerceAtLeast(0),
                )
            }
            ProductUiEvent.SaveSucceeded -> finish(state, ProductUiOperation.SAVE_PROJECT, ProductUiStatus.SAVED)
            ProductUiEvent.ExportSucceeded -> finish(state, ProductUiOperation.EXPORT, ProductUiStatus.EXPORTED)

            ProductUiEvent.EnterEditor -> if (state.canEnterEditor) {
                // Recognition success deliberately remains in its current
                // section until this explicit user action.
                state.copy(section = ProductUiSection.EDITOR)
            } else state

            ProductUiEvent.Retry -> if (state.canRetry) {
                state.copy(operation = state.lastOperation, status = ProductUiStatus.RUNNING)
            } else state

            is ProductUiEvent.SelectSection -> if (state.operation == null) {
                state.copy(section = event.section)
            } else state
        }
    }

    private fun start(
        state: ProductUiState,
        operation: ProductUiOperation,
        section: ProductUiSection,
    ): ProductUiState = if (state.operation == null) {
        state.copy(
            section = section,
            operation = operation,
            lastOperation = operation,
            status = ProductUiStatus.RUNNING,
        )
    } else state

    private fun finish(
        state: ProductUiState,
        operation: ProductUiOperation,
        status: ProductUiStatus,
    ): ProductUiState = if (state.operation == operation) {
        state.copy(operation = null, status = status)
    } else state
}
