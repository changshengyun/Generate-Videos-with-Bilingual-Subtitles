package com.example.lyriccaptioner.processing

/**
 * Renderer-independent export session state.  This model deliberately owns
 * only the task row/temporary-output lifecycle; UI and exporter integration
 * remain responsible for invoking the transitions around real I/O.
 */
enum class ExportLifecycleState {
    PENDING,
    RUNNING,
    CANCELLING,
    PUBLISHED,
    CANCELLED,
    FAILED,
}

enum class ExportFailureStage {
    INSERT,
    ENCODE,
    VALIDATE,
    COPY,
    PUBLISH,
}

data class ExportRelinkSnapshot(
    val sourceUri: String,
    val captionRevision: Long,
    val styleRevision: Long,
    val projectRevision: Long,
)

data class ExportLifecycleSnapshot(
    val taskId: String,
    val sourceUri: String,
    val destinationUri: String,
    val state: ExportLifecycleState,
    val captionRevision: Long,
    val styleRevision: Long,
    val projectRevision: Long,
    val cleanupRequired: Boolean,
    val failureStage: ExportFailureStage? = null,
) {
    val isTerminal: Boolean
        get() = state == ExportLifecycleState.PUBLISHED ||
            state == ExportLifecycleState.CANCELLED ||
            state == ExportLifecycleState.FAILED

    /** Diagnostic output intentionally excludes all URI/path/media values. */
    fun privacyLogLine(): String =
        "export task=$taskId state=$state cleanup=$cleanupRequired" +
            (failureStage?.let { " failure=$it" } ?: "")
}

/**
 * Small deterministic state machine used to prove race/cleanup semantics.
 * Every mutating operation is idempotent for an already terminal task.
 */
class ExportLifecycle private constructor(
    private var current: ExportLifecycleSnapshot,
) {
    @Synchronized
    fun snapshot(): ExportLifecycleSnapshot = current

    @Synchronized
    fun markRunning(): ExportLifecycleSnapshot {
        if (current.state == ExportLifecycleState.PENDING) {
            current = current.copy(state = ExportLifecycleState.RUNNING)
        }
        return current
    }

    @Synchronized
    fun requestCancel(): ExportLifecycleSnapshot {
        current = when (current.state) {
            ExportLifecycleState.PENDING,
            ExportLifecycleState.RUNNING,
            ExportLifecycleState.CANCELLING,
            -> current.copy(
                state = ExportLifecycleState.CANCELLING,
                cleanupRequired = true,
            )
            ExportLifecycleState.PUBLISHED,
            ExportLifecycleState.CANCELLED,
            ExportLifecycleState.FAILED,
            -> current
        }
        return current
    }

    /** Complete cancellation after the encoder/copy operation has stopped. */
    @Synchronized
    fun completeCancel(): ExportLifecycleSnapshot {
        if (current.state == ExportLifecycleState.CANCELLING) {
            current = current.copy(state = ExportLifecycleState.CANCELLED, cleanupRequired = true)
        }
        return current
    }

    @Synchronized
    fun fail(stage: ExportFailureStage): ExportLifecycleSnapshot {
        if (!current.isTerminal) {
            current = current.copy(
                state = ExportLifecycleState.FAILED,
                cleanupRequired = true,
                failureStage = stage,
            )
        }
        return current
    }

    @Synchronized
    fun publish(): ExportLifecycleSnapshot {
        if (current.state == ExportLifecycleState.RUNNING) {
            current = current.copy(state = ExportLifecycleState.PUBLISHED, cleanupRequired = false)
        }
        return current
    }

    companion object {
        fun begin(
            taskId: String,
            sourceUri: String,
            destinationUri: String,
            captionRevision: Long = 0L,
            styleRevision: Long = 0L,
            projectRevision: Long = 0L,
        ): ExportLifecycle {
            require(taskId.isNotBlank()) { "Export task id must not be blank" }
            require(sourceUri.isNotBlank()) { "Export source must not be blank" }
            require(destinationUri.isNotBlank()) { "Export destination must not be blank" }
            require(sourceUri != destinationUri) { "Export destination must differ from source" }
            return ExportLifecycle(
                ExportLifecycleSnapshot(
                    taskId = taskId,
                    sourceUri = sourceUri,
                    destinationUri = destinationUri,
                    state = ExportLifecycleState.PENDING,
                    captionRevision = captionRevision,
                    styleRevision = styleRevision,
                    projectRevision = projectRevision,
                    cleanupRequired = true,
                ),
            )
        }

        fun relink(
            current: ExportRelinkSnapshot,
            newSourceUri: String,
        ): ExportRelinkSnapshot = current.copy(sourceUri = newSourceUri)
    }
}

/** A coordinator may own at most one active export at a time. */
class SingleExportSlot {
    private var active: ExportLifecycle? = null

    @Synchronized
    fun start(
        taskId: String,
        sourceUri: String,
        destinationUri: String,
        captionRevision: Long = 0L,
        styleRevision: Long = 0L,
        projectRevision: Long = 0L,
    ): ExportLifecycle {
        check(active == null || active!!.snapshot().isTerminal) {
            "Only one export task may be active"
        }
        val task = ExportLifecycle.begin(
            taskId = taskId,
            sourceUri = sourceUri,
            destinationUri = destinationUri,
            captionRevision = captionRevision,
            styleRevision = styleRevision,
            projectRevision = projectRevision,
        )
        active = task
        return task
    }

    @Synchronized
    fun activeSnapshot(): ExportLifecycleSnapshot? = active?.snapshot()
}
