package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface TaskSubmitter {
    suspend fun submit(task: SDMTool.Task, notifyOnFinish: Boolean = true): SDMTool.Task.Result

    /**
     * Like [submit], but atomically declines when the task's tool already has an incomplete task.
     * The check and the registration happen under the same lock, so concurrent callers can't both
     * see an idle tool and both queue up work.
     *
     * Returns null when the submission was declined.
     */
    suspend fun submitIfToolIdle(task: SDMTool.Task, notifyOnFinish: Boolean = true): SDMTool.Task.Result?

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
        val error: Throwable? = null,
        val notifyOnFinish: Boolean = true,
    ) {
        val isComplete: Boolean = completedAt != null
        val isCancelling: Boolean = cancelledAt != null && completedAt == null
        val isActive: Boolean = !isComplete && startedAt != null
        val isQueued: Boolean = !isComplete && startedAt == null && cancelledAt == null
    }
}
