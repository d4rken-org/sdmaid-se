package eu.darken.sdmse.scheduler.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.hasCause
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.automation.core.errors.AutomationException
import eu.darken.sdmse.automation.core.errors.AutomationSchedulerException
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.setup.SetupHealer
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take


@HiltWorker
class SchedulerWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    dispatcherProvider: DispatcherProvider,
    private val taskManager: TaskManager,
    private val schedulerManager: SchedulerManager,
    private val schedulerSettings: SchedulerSettings,
    private val schedulerNotifications: SchedulerNotifications,
    private val setupHealer: SetupHealer,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val shellOps: ShellOps,
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
        return schedulerNotifications.getForegroundInfo(scheduleId)
    }

    override suspend fun doWork(): Result = try {
        log(TAG, VERBOSE) { "Executing $inputData now (runAttemptCount=$runAttemptCount)" }

        try {
            log(TAG) { "Declaring as foreground task" }
            setForeground(getForegroundInfo())
            log(TAG, INFO) { "Foreground state declared!" }
        } catch (e: IllegalStateException) {
            log(TAG, ERROR) { "Can't execute in foreground: ${e.asLog()}" }
        }

        val schedule = getSchedule()

        if (runAttemptCount > 0) {
            log(TAG, WARN) { "Repeat execution attempt ($runAttemptCount) for $schedule" }
            Result.failure(inputData)
        } else {
            val start = System.currentTimeMillis()
            log(TAG, INFO) { "Executing schedule $schedule" }
            Bugs.leaveBreadCrumb("Executing schedule")

            schedulerNotifications.notifyState(schedule)

            doDoWork(schedule)

            val duration = System.currentTimeMillis() - start
            log(TAG, INFO) { "Execution finished after ${duration}ms: $schedule" }

            Result.success(inputData)
        }
    } catch (e: Throwable) {
        if (e !is CancellationException) {
            log(TAG, ERROR) { "Execution failed: ${e.asLog()}" }
            finishedWithError = true
            schedulerNotifications.notifyError(scheduleId)

            Result.failure(inputData)
        } else {
            Result.success()
        }
    } finally {
        schedulerManager.updateExecutedNow(scheduleId)

        try {
            schedulerNotifications.cancel(scheduleId)
            schedulerManager.reschedule(scheduleId)
        } catch (e: Exception) {
            log(
                TAG,
                ERROR
            ) { "Failed to clean up notifications and reschedule (error=$finishedWithError): ${e.asLog()}" }
        }

        try {
            workerScope.cancel("Worker finished (withError?=$finishedWithError).")
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to cancel worker scope (error=$finishedWithError): ${e.asLog()}" }
        }
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
            workerScope.async {
                try {
                    log(TAG) { "Launching $task" }
                    val result = taskManager.submit(task)
                    log(TAG) { "Finished $task -> $result" }
                    SchedulerNotifications.Results(task, result = result)
                } catch (e: Exception) {
                    log(TAG, ERROR) { "Scheduler task failed ($task): ${e.asLog()}" }
                    val wrappedError = if (e.hasCause(AutomationException::class)) {
                        AutomationSchedulerException(e)
                    } else {
                        e
                    }
                    SchedulerNotifications.Results(task, error = wrappedError)
                }
            }
        }

        log(TAG) { "Waiting for jobs to complete: $taskJobs" }
        val taskResults = taskJobs.awaitAll().toSet()
        schedulerNotifications.notifyResult(taskResults)
        log(TAG) { "All task jobs have finished." }

        schedule.commandsAfterSchedule.takeIf { it.isNotEmpty() }?.let { cmds ->
            log(TAG, INFO) { "Post-schedule commands are available" }
            cmds.forEachIndexed { index, s -> log(TAG, INFO) { "Command #$index: $s" } }

            val shellOpsMode = when {
                rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
                adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
                else -> ShellOps.Mode.NORMAL
            }
            val result = shellOps.execute(ShellOpsCmd(cmds = cmds), shellOpsMode)
            log(TAG, INFO) { "Post-schedule ShellOps result: $result" }
        }
    }

    companion object {
        const val INPUT_SCHEDULE_ID = "scheduler.worker.input.scheduleid"
        val TAG = logTag("Scheduler", "Worker")
    }
}
