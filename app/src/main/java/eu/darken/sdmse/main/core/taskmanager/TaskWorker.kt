package eu.darken.sdmse.main.core.taskmanager

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach


@HiltWorker
class TaskWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val taskManager: TaskManager,
    private val taskWorkerNotifications: TaskWorkerNotifications,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, params) {

    private val workerScope = CoroutineScope(dispatcherProvider.Default + SupervisorJob())
    private var finishedWithError = false

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val state = withTimeoutOrNull(3000) { taskManager.state.first() }
        if (state == null) log(TAG, WARN) { "TaskManager state was not available" }
        return taskWorkerNotifications.getForegroundInfo(state)
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        doDoWork()

        val duration = System.currentTimeMillis() - start

        log(TAG, VERBOSE) { "Execution finished after ${duration}ms, $inputData" }

        Result.success(inputData)
    } catch (e: Throwable) {
        if (e !is CancellationException) {
            log(TAG, ERROR) { "Execution failed: ${e.asLog()}" }
            finishedWithError = true
            Result.failure(inputData)
        } else {
            Result.success()
        }
    } finally {
        notificationManager.cancel(TaskWorkerNotifications.NOTIFICATION_ID)
        this.workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork() {
        log(TAG, VERBOSE) { "Monitoring task states" }

        taskManager.state
            .onEach { state ->
                val notification = taskWorkerNotifications.getNotification(state)
                notificationManager.notify(TaskWorkerNotifications.NOTIFICATION_ID, notification)
            }
            .launchIn(workerScope)

        val job = taskManager.state
            .mapLatest { state ->
                when {
                    state.isIdle -> {
                        log(TAG) { "We are idle, granting grace period then canceling" }
                        delay(5 * 1000)
                        log(TAG) { "No active tasks, grace period passed, canceling now" }
                        workerScope.cancel()
                    }
                    else -> {
                        val activeTasks = state.tasks.filter { !it.isComplete }
                        log(TAG) { "Active tasks: ${activeTasks.size}" }
                        Unit
                    }
                }
            }
            .launchIn(workerScope)

        job.join()

        log(TAG, VERBOSE) { "Finished monitoring task states" }
    }

    companion object {
        val TAG = logTag("TaskManager", "Worker")
    }
}
