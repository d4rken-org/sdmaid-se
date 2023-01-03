package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.common.sharedresource.keepResourceHoldersAlive
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.StatsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val tools: Set<@JvmSuppressWildcards SDMTool>,
    private val statsRepo: StatsRepo,
) : HasSharedResource<Any> {

    override val sharedResource = SharedResource.createKeepAlive(TAG, appScope)
    private val children = tools

    private val concurrencyLock = Semaphore(2)
    private var queuedTasks = 0

    data class State(
        val activeTasks: Collection<ActiveTask> = emptySet()
    )

    data class ActiveTask(
        val task: SDMTool.Task
    )

    val state = MutableStateFlow(State())


    suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result {
        log(TAG) { "submit(task=$task)..." }
        children.forEach { adoptChildResource(it) }

        return try {
            queuedTasks++
            log(TAG) { "Queued tasks: $queuedTasks" }
            keepResourceHoldersAlive(tools) {
                concurrentTasks(task)
            }
        } finally {
            queuedTasks--
            log(TAG) { "Tasks remaining: $queuedTasks" }
        }
    }

    private suspend fun concurrentTasks(task: SDMTool.Task): SDMTool.Task.Result = concurrencyLock.withPermit {
        val start = System.currentTimeMillis()

        val tool = tools.single { it.type == task.type }

        val result = tool.useRes { tool.submit(task) }

        val stop = System.currentTimeMillis()
        log(TAG) { "submit(task=$task) after ${stop - start}ms: $result" }
        result
    }

    suspend fun cancel(type: SDMTool.Type) {
        log(TAG) { "cancel($type)" }
    }

    companion object {
        private val TAG = logTag("TaskManager")
    }
}