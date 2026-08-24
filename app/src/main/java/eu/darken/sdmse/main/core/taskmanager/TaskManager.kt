package eu.darken.sdmse.main.core.taskmanager

import eu.darken.sdmse.common.backup.BackupOperationGate
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.withPrevious
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.rngString
import eu.darken.sdmse.common.sharedresource.KeepAlive
import eu.darken.sdmse.common.sharedresource.SharedResource
import eu.darken.sdmse.main.core.PartialResultException
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val tools: Set<@JvmSuppressWildcards SDMTool>,
    private val taskWorkerControl: TaskWorkerControl,
    private val backupGate: BackupOperationGate,
) : TaskSubmitter {

    private val sharedResource = SharedResource.createKeepAlive(TAG, appScope)

    private val managerLock = Mutex()
    private val concurrencyLock = Semaphore(2)
    private val taskEntries = MutableStateFlow(emptyMap<String, TaskEntry>())

    private data class TaskEntry(
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
        val notifyOnFinish: Boolean = true,
    ) {
        val toolType: SDMTool.Type
            get() = tool.type

        val isComplete: Boolean = completedAt != null
        val isCancelling: Boolean = cancelledAt != null && completedAt == null
        val isActive: Boolean = !isComplete && startedAt != null
        val isQueued: Boolean = !isComplete && startedAt == null && cancelledAt == null

        fun toPublic() = TaskSubmitter.ManagedTask(
            id = id,
            toolType = toolType,
            task = task,
            queuedAt = queuedAt,
            startedAt = startedAt,
            cancelledAt = cancelledAt,
            completedAt = completedAt,
            result = result,
            error = error,
            notifyOnFinish = notifyOnFinish,
        )

        override fun toString(): String {
            return "TaskEntry(${toolType}: ${task.javaClass.simpleName} - queued=$queuedAt, started=$startedAt, completed=$completedAt, cancelled=$cancelledAt) - result=$result, error=$error)"
        }
    }

    override val state = taskEntries
        .map { entries ->
            TaskSubmitter.State(
                tasks = entries.values.map { it.toPublic() }
            )
        }

    init {
        state
            .distinctUntilChanged()
            .onEach {
                log(TAG, VERBOSE) { "Task map changed:" }
                taskEntries.value.values.forEachIndexed { index, entry ->
                    log(TAG, VERBOSE) { "#$index - $entry" }
                }
            }
            .launchIn(appScope)
        state
            .distinctUntilChanged()
            .onEach {
                updateTasks {
                    // We want to keep one result of each type
                    val tasksByType = this.entries
                        .asSequence()
                        .filter { it.value.isComplete }
                        .groupBy { it.value.toolType }

                    // Keep the newest for each type
                    val tasksToRemove = tasksByType
                        .flatMap { (_, tasks) -> tasks.sortedByDescending { it.value.completedAt }.drop(1) }
                        .map { it.key }

                    tasksToRemove
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

    private suspend fun updateTasks(
        update: MutableMap<String, TaskEntry>.() -> Unit
    ): Map<String, TaskEntry> = withContext(NonCancellable) {
        managerLock.withLock {
            val modMap = taskEntries.value.toMutableMap()
            update(modMap)
            modMap.toMap().also {
                taskEntries.value = it
            }
        }
    }

    private suspend fun stage(taskId: String) {
        log(TAG) { "stage(): Staging $taskId" }
        var tempEntry: TaskEntry? = null
        updateTasks {
            this[taskId] = this[taskId]!!
                .also { tempEntry = it }
        }
        val entry: TaskEntry = tempEntry ?: throw IllegalStateException("Can't find task $taskId")
        val tool = entry.tool

        tool.updateProgress {
            it ?: Progress.Data(
                primary = eu.darken.sdmse.common.R.string.general_progress_queued.toCaString(),
                count = Progress.Count.Indeterminate(),
            )
        }
    }

    // A config restore rewrites the same databases and settings tool tasks touch. Wait for an active
    // backup operation BEFORE taking an execution permit, so waiting tasks don't hog the permits.
    private suspend fun execute(
        taskId: String,
    ): SDMTool.Task.Result = backupGate.runShared {
        concurrencyLock.withPermit {
            executeGuarded(taskId)
        }
    }

    private suspend fun executeGuarded(taskId: String): SDMTool.Task.Result {
        log(TAG) { "execute(): Starting $taskId" }
        val start = System.currentTimeMillis()

        var tempEntry: TaskEntry? = null
        updateTasks {
            this[taskId] = this[taskId]!!
                .copy(startedAt = Instant.now())
                .also { tempEntry = it }
        }
        val entry: TaskEntry = tempEntry ?: throw IllegalStateException("Can't find task $taskId")

        val tool = entry.tool
        val timeout = getTaskTimeout(entry.task.type)
        val result = try {
            withTimeout(timeout) {
                tool.useRes { tool.submit(entry.task) }
            }
        } catch (e: TimeoutCancellationException) {
            throw TaskTimeoutException(entry.task.type, timeout)
        }

        val stop = System.currentTimeMillis()
        log(TAG) { "execute() after ${stop - start}ms: $result : $tempEntry" }
        log(TAG) { "execute(): Task entries now:\n${taskEntries.value.values.joinToString("\n")}" }
        return result
    }

    override suspend fun submit(task: SDMTool.Task, notifyOnFinish: Boolean): SDMTool.Task.Result {
        log(TAG, INFO) { "submit(): $task (notifyOnFinish=$notifyOnFinish)" }
        return submitInternal(task, notifyOnFinish, declineIfToolBusy = false)!!
    }

    override suspend fun submitIfToolIdle(task: SDMTool.Task, notifyOnFinish: Boolean): SDMTool.Task.Result? {
        log(TAG, INFO) { "submitIfToolIdle(): $task (notifyOnFinish=$notifyOnFinish)" }
        return submitInternal(task, notifyOnFinish, declineIfToolBusy = true)
    }

    /**
     * Shared submit implementation. With [declineIfToolBusy] the "does this tool already have an
     * incomplete task?" check and the registration happen inside a single [updateTasks] block, i.e.
     * under [managerLock], so two concurrent callers can't both observe an idle tool and both
     * register a task. Returns null when the submission was declined, which cannot happen while
     * [declineIfToolBusy] is false.
     */
    private suspend fun submitInternal(
        task: SDMTool.Task,
        notifyOnFinish: Boolean,
        declineIfToolBusy: Boolean,
    ): SDMTool.Task.Result? {
        val taskId = rngString

        // The task's outcome is signalled through this deferred, NOT through the task map: a failing
        // bookkeeping step must not be able to strand a submit() caller waiting for a completion
        // state that is never published.
        val outcome = CompletableDeferred<Result<SDMTool.Task.Result>>()

        val job = appScope.launch(
            context = dispatcherProvider.IO,
            start = CoroutineStart.LAZY,
        ) {
            var result: SDMTool.Task.Result? = null
            var error: Throwable? = null
            try {
                stage(taskId)
                result = execute(taskId)

                log(TAG) { "Result for ${task.type}-$taskId is $result" }
            } catch (e: Throwable) {
                // Throwable, not Exception: AppScope has no CoroutineExceptionHandler, so an Error
                // escaping a tool would take the process down with it.
                // A tool that got part of its work done before failing wraps both in a
                // PartialResultException. Record each half where it belongs, so the completion is a
                // partial success while the caller below still sees the failure.
                val partial = e as? PartialResultException
                if (partial != null) result = partial.partialResult
                val unwrapped = partial?.cause ?: e
                if (unwrapped is CancellationException) {
                    log(TAG, INFO) { "execute(): Task was cancelled (${task.type}-$taskId): $task" }
                } else {
                    log(TAG, ERROR) { "execute(): Execution failed (${task.type}-$taskId): $task\n${unwrapped.asLog()}" }
                }
                error = unwrapped
            } finally {
                try {
                    updateTasks {
                        // Throwable, not Exception: an Error from a tool's progress reset must not
                        // skip the resource lock release or the completion publish below.
                        try {
                            this[taskId]!!.tool.updateProgress { null }
                        } catch (e: Throwable) {
                            runCatching {
                                log(TAG, WARN) { "Failed to reset progress for ${task.type}-$taskId: ${e.asLog()}" }
                            }
                        }
                        runCatching { log(TAG) { "Releasing resource lock for ${task.type}-$taskId" } }
                        try {
                            this[taskId]!!.resourceLock!!.close()
                        } catch (e: Throwable) {
                            runCatching {
                                log(TAG, WARN) { "Failed to release resource lock for ${task.type}-$taskId: ${e.asLog()}" }
                            }
                        }
                        this[taskId] = this[taskId]!!.copy(
                            completedAt = Instant.now(),
                            error = error,
                            result = result,
                        )
                    }
                } catch (e: Throwable) {
                    log(TAG, ERROR) { "Bookkeeping failed for ${task.type}-$taskId: ${e.asLog()}" }
                }

                outcome.complete(
                    when {
                        error != null -> Result.failure(error)
                        result != null -> Result.success(result)
                        else -> Result.failure(IllegalStateException("Task produced neither result nor error"))
                    }
                )
            }
        }

        job.invokeOnCompletion { log(TAG, VERBOSE) { "Task completion: ${taskEntries.value[taskId]}" } }
        // Safety net for a LAZY job that is cancelled before its body (and with it the finally above)
        // ever ran — nothing else would ever complete the deferred, and nothing would run the entry's
        // bookkeeping either. A stranded entry is not merely stale: completedAt stays null forever, so
        // isComplete never turns true and anything waiting for the tool to fall idle waits forever.
        job.invokeOnCompletion { cause ->
            if (!outcome.isCompleted) {
                outcome.complete(Result.failure(cause ?: IllegalStateException("Task job completed without outcome")))
            }
            // Cheap pre-check only; the authoritative one runs under managerLock below. A missing
            // entry means cancellation beat registration, which leaves nothing to clean up.
            val pending = taskEntries.value[taskId]
            if (pending == null || pending.isComplete) return@invokeOnCompletion
            // updateTasks() is suspend and takes managerLock, so it can't run in this handler.
            appScope.launch {
                try {
                    updateTasks {
                        // The normal finally may have completed the entry while we waited for the lock.
                        val entry = this[taskId] ?: return@updateTasks
                        if (entry.isComplete) return@updateTasks
                        runCatching {
                            log(TAG, WARN) { "Completing entry whose body never ran: ${task.type}-$taskId" }
                        }
                        // Progress is tool-wide, not per-task, and this cleanup runs asynchronously
                        // (updateTasks is suspend), so a newer task for the same tool may have taken
                        // over the progress in the meantime — clearing it would drop the dashboard's
                        // running state and Cancel action for work that is still going. Registration
                        // takes managerLock too, so any such task is visible here. The rest of the
                        // cleanup below is entry-specific and always safe to run.
                        val hasOtherIncompleteTask = values.any {
                            it.id != taskId &&
                                    it.toolType == entry.toolType &&
                                    !it.isComplete
                        }
                        if (!hasOtherIncompleteTask) {
                            // Throwable, not Exception: an Error from a tool's progress reset must
                            // not skip the resource lock release or the completion publish below.
                            try {
                                entry.tool.updateProgress { null }
                            } catch (e: Throwable) {
                                runCatching {
                                    log(TAG, WARN) { "Failed to reset progress for ${task.type}-$taskId: ${e.asLog()}" }
                                }
                            }
                        }
                        try {
                            entry.resourceLock?.close()
                        } catch (e: Throwable) {
                            runCatching {
                                log(TAG, WARN) { "Failed to release resource lock for ${task.type}-$taskId: ${e.asLog()}" }
                            }
                        }
                        this[taskId] = entry.copy(
                            completedAt = Instant.now(),
                            error = cause,
                        )
                    }
                } catch (e: Throwable) {
                    runCatching {
                        log(TAG, ERROR) { "Safety-net bookkeeping failed for ${task.type}-$taskId: ${e.asLog()}" }
                    }
                }
            }
        }

        var declined = false

        withContext(NonCancellable) {
            // Any task causes the taskmanager to stay "alive" and with it any depending resources
            // Only release all resources once all tasks are finished.
            val keepAlive = sharedResource.get()

            val tool = tools.single { it.type == task.type }
            sharedResource.addChild(tool.sharedResource)

            updateTasks {
                if (declineIfToolBusy && values.any { it.toolType == task.type && !it.isComplete }) {
                    declined = true
                    log(TAG, INFO) { "submit(): Declined, ${task.type} already has an incomplete task" }
                    return@updateTasks
                }

                val entry = TaskEntry(
                    id = taskId,
                    task = task,
                    tool = tool,
                    job = job,
                    resourceLock = keepAlive,
                    notifyOnFinish = notifyOnFinish,
                )

                this[entry.id] = entry

                log(TAG) { "submit(): Queued: $entry" }
            }

            if (declined) {
                // The job is LAZY and was never registered, so nothing would ever start it.
                // Cancelling it completes the outcome deferred, and the safety net above no-ops on
                // the missing entry; the keep-alive taken for it has to go back here.
                job.cancel()
                runCatching { keepAlive.close() }
            }
        }

        if (declined) return null

        job.join()

        return outcome.await().getOrElse { error ->
            // Callers expect an Exception: a CancellationException passes through raw, anything else
            // that isn't an Exception (i.e. an Error) arrives wrapped with its cause preserved.
            throw (error as? Exception ?: TaskFatalErrorException(error))
        }
    }

    /** Drops completed task entries for [type], removing their results from [state]. */
    suspend fun forgetCompleted(type: SDMTool.Type) {
        log(TAG, INFO) { "forgetCompleted($type)" }
        updateTasks {
            values.removeAll { it.toolType == type && it.isComplete }
        }
    }

    override fun cancel(type: SDMTool.Type) {
        appScope.launch {
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
    }

    private fun getTaskTimeout(type: SDMTool.Type): Duration = when (type) {
        SDMTool.Type.APPCLEANER -> 4.hours
        SDMTool.Type.CORPSEFINDER -> 4.hours
        SDMTool.Type.SYSTEMCLEANER -> 4.hours
        SDMTool.Type.DEDUPLICATOR -> 6.hours
        SDMTool.Type.ANALYZER -> 4.hours
        SDMTool.Type.APPCONTROL -> 2.hours
        SDMTool.Type.SQUEEZER -> 4.hours
        SDMTool.Type.SWIPER -> 2.hours
    }

    companion object {
        private val TAG = logTag("TaskManager")
    }
}
