package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.sharedresource.KeepAlive
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.stats.StatsRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val tools: Set<@JvmSuppressWildcards SDMTool>,
    private val taskWorkerControl: TaskWorkerControl,
    private val statsRepo: StatsRepo,
) {

    private val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val managerLock = Mutex()
    private val concurrencyLock = Semaphore(2)
    private val managedTasks = MutableStateFlow(emptyMap<String, ManagedTask>())

    data class ManagedTask(
        val id: String,
        val task: SDMTool.Task,
        val tool: SDMTool,
        val queuedAt: Instant = Instant.now(),
        val startedAt: Instant? = null,
        val cancelledAt: Instant? = null,
        val completedAt: Instant? = null,
        val job: Job? = null,
        val resourceLock: KeepAlive? = null,
        val result: SDMTool.Task.Result? = null,
        val error: Throwable? = null,
    ) {
        val isComplete: Boolean = completedAt != null
        val isCancelling: Boolean = cancelledAt != null && completedAt == null
        val isActive: Boolean = !isComplete && startedAt != null
        val isQueued: Boolean = !isComplete && startedAt == null && cancelledAt == null

        override fun toString(): String {
            return "ManagedTask(${tool.type}: ${task.javaClass.simpleName} - queued=$queuedAt, started=$startedAt, completed=$completedAt, cancelled=$cancelledAt) - result=$result, error=$error)"
        }
    }

    data class State(
        val tasks: Collection<ManagedTask> = emptySet()
    ) {
        val isIdle: Boolean
            get() = tasks.all { it.isComplete }

        val hasCancellable: Boolean
            get() = tasks.any { !it.isComplete && !it.isCancelling }
    }

    val state = managedTasks
        .map { manTasks ->
            State(
                tasks = manTasks.values
            )
        }

    init {
        state
            .distinctUntilChanged()
            .onEach {
                log(TAG, VERBOSE) { "Task map changed:" }
                managedTasks.value.values.forEachIndexed { index, managedTask ->
                    log(TAG, VERBOSE) { "#$index - $managedTask" }
                }
            }
            .launchIn(appScope)
        state
            .distinctUntilChanged()
            .onEach {
                updateTasks {
                    this.entries
                        .asSequence()
                        .filter { it.value.isComplete }
                        .sortedBy { it.value.completedAt }
                        .map { it.key }
                        .drop(10)
                        .onEach {
                            log(TAG, VERBOSE) { "Pruning old task: $it" }
                            remove(it)
                        }
                        .toList()
                }
            }
            .launchIn(appScope)
        state
            .map { it.isIdle }
            .distinctUntilChanged()
            .withPrevious()
            .onEach { (isOldIdle, newIdle) ->
                if (isOldIdle != false && !newIdle) {
                    taskWorkerControl.startMonitor()
                }
            }
            .launchIn(appScope)
    }

    private suspend fun updateTasks(update: MutableMap<String, ManagedTask>.() -> Unit) = withContext(NonCancellable) {
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
                primary = eu.darken.sdmse.common.R.string.general_progress_queued.toCaString(),
                count = Progress.Count.Indeterminate(),
            )
        }
    }

    private suspend fun execute(taskId: String): SDMTool.Task.Result = concurrencyLock.withPermit {
        log(TAG) { "execute(): Starting $taskId" }
        val start = System.currentTimeMillis()

        var tempTask: ManagedTask? = null
        updateTasks {
            this[taskId] = this[taskId]!!
                .copy(startedAt = Instant.now())
                .also { tempTask = it }
        }
        val managedTask: ManagedTask = tempTask ?: throw IllegalStateException("Can't find task $taskId")

        val tool = managedTask.tool
        val result = tool.useRes { tool.submit(managedTask.task) }

        val stop = System.currentTimeMillis()
        log(TAG) { "execute() after ${stop - start}ms: $result : $tempTask" }
        log(TAG) { "execute(): Managed tasks now:\n${managedTasks.value.values.joinToString("\n")}" }
        result
    }

    suspend fun submit(task: SDMTool.Task): SDMTool.Task.Result {
        log(TAG, INFO) { "submit(): $task" }
        val taskId = rngString

        val job = appScope.launch(
            context = dispatcherProvider.IO,
            start = CoroutineStart.LAZY,
        ) {
            var result: SDMTool.Task.Result? = null
            var error: Exception? = null
            try {
                stage(taskId)
                result = execute(taskId)

                log(TAG) { "Result for $taskId is $result" }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    log(TAG, INFO) { "execute(): Task was cancelled ($taskId): $task" }
                } else {
                    log(TAG, ERROR) { "execute(): Execution failed ($taskId): $task\n${e.asLog()}" }
                }
                error = e
            } finally {
                updateTasks {
                    this[taskId]!!.tool.updateProgress { null }
                    log(TAG) { "Releasing resource lock for $taskId" }
                    this[taskId]!!.resourceLock!!.close()
                    this[taskId] = this[taskId]!!.copy(
                        completedAt = Instant.now(),
                        error = error,
                        result = result,
                    )
                }
            }
        }

        job.invokeOnCompletion { log(TAG, VERBOSE) { "Task completion: ${managedTasks.value[taskId]}" } }

        withContext(NonCancellable) {
            // Any task causes the taskmanager to stay "alive" and with it any depending resources
            // Only release all resources once all tasks are finished.
            val keepAlive = sharedResource.get()

            val tool = tools.single { it.type == task.type }
            sharedResource.addChild(tool.sharedResource)

            updateTasks {
                val managedTask = ManagedTask(
                    id = taskId,
                    task = task,
                    tool = tool,
                    job = job,
                    resourceLock = keepAlive,
                )

                this[managedTask.id] = managedTask

                log(TAG) { "submit(): Queued: $managedTask" }
            }
        }

        job.join()

        val endTask = managedTasks
            .mapNotNull { it[taskId] }
            .filter { it.isComplete }
            .first()

        return endTask.result ?: throw endTask.error!!
    }

    fun cancel(type: SDMTool.Type) = appScope.launch {
        log(TAG, INFO) { "cancel($type)" }

        updateTasks {
            this
                .filter { it.value.tool.type == type && it.value.cancelledAt == null }
                .onEach { (key, value) ->
                    log(TAG) { "Cancelling $value" }
                    value.job?.cancel()
                    this[key] = this[key]!!.copy(cancelledAt = Instant.now())
                }
        }
    }

    companion object {
        private val TAG = logTag("TaskManager")
    }
}