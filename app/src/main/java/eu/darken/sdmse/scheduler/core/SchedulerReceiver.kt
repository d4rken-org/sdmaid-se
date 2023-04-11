package eu.darken.sdmse.scheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SchedulerReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var schedulerManager: SchedulerManager
    @Inject lateinit var schedulerSettings: SchedulerSettings
    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var powerManager: PowerManager

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (intent.action != SchedulerManager.SCHEDULE_INTENT) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        log(TAG, INFO) { "Schedule triggered for ${intent.data}" }

        val scheduleId: ScheduleId = intent.data
            ?.encodedAuthority
            ?.removePrefix("alarm.")
            ?: return

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Scheduler triggered")

        appScope.launch {
            val schedule = schedulerManager.getSchedule(scheduleId)
            if (schedule == null) {
                log(TAG, ERROR) { "Unknown schedule: $scheduleId" }
                return@launch
            }

            if (schedulerSettings.skipWhenPowerSaving.value() && powerManager.isPowerSaveMode) {
                log(TAG, WARN) { "Phone is in power-saving mode, skipping execution." }
                return@launch
            }


            val tasks = mutableListOf<SDMTool.Task>()

            if (schedule.useCorpseFinder) tasks.add(CorpseFinderSchedulerTask(schedule.id))
            if (schedule.useSystemCleaner) tasks.add(SystemCleanerSchedulerTask(schedule.id))
            if (schedule.useAppCleaner) tasks.add(AppCleanerSchedulerTask(schedule.id))

            tasks.forEach { task ->
                appScope.launch {
                    try {
                        taskManager.submit(task)
                    } catch (e: Exception) {
                        log(TAG, ERROR) { "Scheduler task failed ($task): ${e.asLog()}" }
                    }
                }
            }

            schedulerManager.updateExecutedNow(scheduleId)
        }

        appScope.launch {
            delay(3000)
            log(TAG) { "Finished processing schedule alarm" }
            asyncPi.finish()
        }
    }

    companion object {
        internal val TAG = logTag("Scheduler", "Receiver")
    }
}
