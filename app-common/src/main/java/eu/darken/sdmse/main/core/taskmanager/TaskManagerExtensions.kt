package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.time.Instant

fun TaskSubmitter.State.getLatestTask(tool: SDMTool.Type): TaskSubmitter.ManagedTask? {
    return tasks.filter { it.toolType == tool && it.isComplete }.maxByOrNull { it.completedAt!! }
}

fun TaskSubmitter.State.getLatestResult(tool: SDMTool.Type): SDMTool.Task.Result? {
    return getLatestTask(tool)?.result
}

/**
 * Cold flow of completed [R] task results for [toolType], deduplicated by task id and limited to
 * tasks that finished after the flow was assembled (so we don't replay results that fired before
 * the subscribing screen existed).
 *
 * [accept] sees the full [TaskSubmitter.ManagedTask] so callers can filter on the submitted task
 * (e.g. skip single-target deletions that have their own confirmation surface).
 *
 * Single-collector: the start-time and dedup set are captured once per call to this function and
 * shared across collectors of the returned flow. Call [uniqueTaskResults] again if you need a
 * fresh dedup window. (Intended call site is one `launchInViewModel()` per VM init.)
 */
inline fun <reified R : SDMTool.Task.Result> TaskSubmitter.uniqueTaskResults(
    toolType: SDMTool.Type,
    crossinline accept: (TaskSubmitter.ManagedTask) -> Boolean = { true },
): Flow<R> {
    val startedAt = Instant.now()
    val handled = mutableSetOf<String>()
    return state
        .map { it.getLatestTask(toolType) }
        .filterNotNull()
        .filter { it.completedAt!! > startedAt }
        .mapNotNull { task ->
            val result = task.result as? R ?: return@mapNotNull null
            if (!handled.add(task.id)) return@mapNotNull null
            if (!accept(task)) return@mapNotNull null
            result
        }
}
