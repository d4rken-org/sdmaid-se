package eu.darken.sdmse.scheduler.core

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.setup.SetupHealer
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch


@HiltWorker
class SchedulerWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val dispatcherProvider: DispatcherProvider,
    private val taskManager: TaskManager,
    private val schedulerManager: SchedulerManager,
    private val schedulerSettings: SchedulerSettings,
    private val workerNotifications: SchedulerNotifications,
    private val notificationManager: NotificationManager,
    private val setupHealer: SetupHealer,
) : CoroutineWorker(context, params) {

    init {
        log(TAG, VERBOSE) { "init(): workerId=$id" }
    }

    private val workerScope = CoroutineScope(dispatcherProvider.Default + SupervisorJob())
    private var finishedWithError = false

    private val scheduleId: ScheduleId
        get() = inputData.getString(INPUT_SCHEDULE_ID) as ScheduleId

    private suspend fun getSchedule(): Schedule {
        return schedulerManager.getSchedule(scheduleId) ?: throw IllegalStateException("Schedule not found $scheduleId")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return workerNotifications.getForegroundInfo(getSchedule())
    }

    override suspend fun doWork(): Result = try {
        val start = System.currentTimeMillis()
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        val schedule = getSchedule()

        log(TAG, INFO) { "Executing schedule $schedule" }
        Bugs.leaveBreadCrumb("Executing schedule")

        workerNotifications.notify(schedule)

        doDoWork(schedule)

        schedulerManager.updateExecutedNow(scheduleId)

        val duration = System.currentTimeMillis() - start
        log(TAG, INFO) { "Execution finished after ${duration}ms: $schedule" }

        schedulerManager.reschedule(scheduleId)

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
        workerNotifications.cancel(scheduleId)
        workerScope.cancel("Worker finished (withError?=$finishedWithError).")
    }

    private suspend fun doDoWork(schedule: Schedule) {
        val tasks = mutableListOf<SDMTool.Task>()

        if (schedule.useCorpseFinder) {
            tasks.add(CorpseFinderSchedulerTask(schedule.id))
        }
        if (schedule.useSystemCleaner) {
            tasks.add(SystemCleanerSchedulerTask(schedule.id))
        }
        if (schedule.useAppCleaner) {
            val useAutomation = schedulerSettings.useAutomation.value()
            tasks.add(AppCleanerSchedulerTask(schedule.id, useAutomation = useAutomation))
        }

        delay(1000)

        // If the worker was launched from a cold-app-start some permission may be broken
        // Instead of driving straight into an error, let's slightly delay and see if we can fix any setup issues
        setupHealer.state
            .filter { it.healAttemptCount > 0 }
            .take(1)
            .first()

        val taskJobs = tasks.map { task ->
            workerScope.launch {
                try {
                    taskManager.submit(task)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Scheduler task failed ($task): ${e.asLog()}" }
                }
            }
        }

        log(TAG) { "Waiting for jobs to complete: $taskJobs" }
        taskJobs.joinAll()
        log(TAG) { "All task jobs have finished." }
    }

    companion object {
        const val INPUT_SCHEDULE_ID = "scheduler.worker.input.scheduleid"
        val TAG = logTag("Scheduler", "Worker")
    }
}
