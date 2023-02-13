package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.sharedresource.HasSharedResource
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.common.sharedresource.adoptChildResource
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.StatsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
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

    private val managerLock = Mutex()
    private val concurrencyLock = Semaphore(2)
    private val managedTasks = MutableStateFlow(emptyMap<String, ManagedTask>())

    data class State(
        val tasks: Collection<ManagedTask> = emptySet()
    ) {
        val isIdle: Boolean
            get() = tasks.all { it.isComplete }
    }

    data class ManagedTask(
        val id: String,
        val task: SDMTool.Task,
        val tool: SDMTool,
        val queuedAt: Instant = Instant.now(),
        val startedAt: Instant? = null,
        val completedAt: Instant? = null,
        val job: Job? = null,
        val result: SDMTool.Task.Result? = null,
        val error: Exception? = null,
    ) {
        val isComplete: Boolean = completedAt != null
    }

    val state = MutableStateFlow(State())

    init {
        managedTasks
            .onEach {

            }
    }

    private suspend fun updateTasks(update: MutableMap<String, ManagedTask>.() -> Unit) {
        managerLock.withLock {
            val modMap = managedTasks.value.toMutableMap()
            update(modMap)
            managedTasks.value = modMap
        }
    }

    private suspend fun stage(taskId: String) {
        log(TAG) { "stage(): Staging $taskId" }
        var tempTask: ManagedTask? = null
        updateTasks {
            this[taskId] = this[taskId]!!
                .also { tempTask = it }
        }
        val managedTask: ManagedTask = tempTask ?: throw IllegalStateException("Can't find task $taskId")
        val tool = managedTask.tool

        tool.updateProgress {
            it ?: Progress.Data(
                primary = R.string.general_progress_queued.toCaString(),
                count = Progress.Count.Indeterminate(),
            )
        }
    }

    private suspend fun execute(taskId: String) = concurrencyLock.withPermit {
        log(TAG) { "execute(): Starting $taskId" }
        val start = System.currentTimeMillis()

        var tempTask: ManagedTask? = null
        updateTasks {
            this[taskId] = this[taskId]!!
                .copy(startedAt = Instant.now())
                .also { tempTask = it }
        }
        val managedTask: ManagedTask = tempTask ?: throw IllegalStateException("Can't find task $taskId")

        var error: Exception? = null

        val result: SDMTool.Task.Result? = try {
            val tool = managedTask.tool
            tool.useRes { tool.submit(managedTask.task) }
        } catch (e: Exception) {
            error = e
            log(TAG, ERROR) { "execute(): Execution failed for $tempTask\n${e.asLog()}" }
            null
        }

        updateTasks {
            this[taskId] = managedTask.copy(
                completedAt = Instant.now(),
                result = result,
                error = error
            )
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "execute() after ${stop - start}ms: $result : $tempTask" }
        log(TAG) { "execute(): Managed tasks now:\n${managedTasks.value.values.joinToString("\n")}" }
    }

    suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result {
        log(TAG) { "submit()... : $task" }
        children.forEach { adoptChildResource(it) }

        val taskId = rngString

        val job = appScope.launch(context = dispatcherProvider.IO, start = CoroutineStart.LAZY) {
            stage(taskId)
            execute(taskId)
        }

        updateTasks {
            val managedTask = ManagedTask(
                id = taskId,
                task = task,
                tool = tools.single { it.type == task.type },
                job = job,
            )
            this[managedTask.id] = managedTask

            log(TAG) { "submit(): Queued: $managedTask" }
            log(TAG) { "submit(): Managed tasks now:\n${managedTasks.value.values.joinToString("\n")}" }
        }

        job.join()

        val endTask = managedTasks
            .mapNotNull { it[taskId] }
            .filter { it.isComplete }
            .first()

        return endTask.result ?: throw endTask.error!!
    }

    fun cancel(type: SDMTool.Type) {
        log(TAG) { "cancel($type)" }
    }

    companion object {
        private val TAG = logTag("TaskManager")
    }
}