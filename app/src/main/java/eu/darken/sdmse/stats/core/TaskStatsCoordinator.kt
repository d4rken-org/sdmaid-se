package eu.darken.sdmse.stats.core

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskStatsCoordinator @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val taskManager: TaskManager,
    private val statsRepo: StatsRepo,
) {
    @Volatile private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true

        taskManager.state
            .map { state -> state.tasks.associateBy { task -> task.id } }
            .withPrevious()
            .onEach { (oldTasks, newTasks) ->
                val oldById = oldTasks ?: emptyMap()
                val newlyCompleted = newTasks.values.filter { task ->
                    task.isComplete && oldById[task.id]?.isComplete != true
                }
                newlyCompleted.forEach { task ->
                    try {
                        statsRepo.report(task)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to report task ${task.id}: ${e.asLog()}" }
                    }
                }
            }
            .launchIn(appScope)

        taskManager.state
            .map { it.isIdle }
            .distinctUntilChanged()
            .withPrevious()
            .onEach { (oldIdle, newIdle) ->
                if (oldIdle == false && newIdle) {
                    try {
                        statsRepo.recordSnapshot(force = true)
                    } catch (e: Exception) {
                        log(TAG, WARN) { "Failed to record idle snapshot: ${e.asLog()}" }
                    }
                }
            }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Stats", "TaskCoordinator")
    }
}
