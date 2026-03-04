package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskSubmitter {
    suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result
    fun cancel(type: SDMTool.Type)
    val state: Flow<State>

    data class State(
        val tasks: Collection<ManagedTask> = emptySet(),
    ) {
        val isIdle: Boolean
            get() = tasks.all { it.isComplete }

        val hasCancellable: Boolean
            get() = tasks.any { !it.isComplete && !it.isCancelling }
    }

    data class ManagedTask(
        val id: String,
        val toolType: SDMTool.Type,
        val task: SDMTool.Task,
        val queuedAt: Instant = Instant.now(),
        val startedAt: Instant? = null,
        val cancelledAt: Instant? = null,
        val completedAt: Instant? = null,
        val result: SDMTool.Task.Result? = null,
        val error: Exception? = null,
    ) {
        val isComplete: Boolean = completedAt != null
        val isCancelling: Boolean = cancelledAt != null && completedAt == null
        val isActive: Boolean = !isComplete && startedAt != null
        val isQueued: Boolean = !isComplete && startedAt == null && cancelledAt == null
    }
}
