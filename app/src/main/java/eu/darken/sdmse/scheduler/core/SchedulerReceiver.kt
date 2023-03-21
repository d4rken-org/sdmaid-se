package eu.darken.sdmse.scheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerSchedulerTask
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderSchedulerTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerSchedulerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SchedulerReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var schedulerManager: SchedulerManager
    @Inject lateinit var taskManager: TaskManager

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

        appScope.launch {
            val schedule = schedulerManager.getSchedule(scheduleId)
            if (schedule == null) {
                log(TAG, ERROR) { "Unknown schedule: $scheduleId" }
                return@launch
            }

            if (schedule.useCorpseFinder) {
                appScope.launch {
                    taskManager.submit(CorpseFinderSchedulerTask(schedule.id))
                }
            }
            if (schedule.useSystemCleaner) {
                appScope.launch {
                    taskManager.submit(SystemCleanerSchedulerTask(schedule.id))
                }
            }
            if (schedule.useAppCleaner) {
                appScope.launch {
                    taskManager.submit(AppCleanerSchedulerTask(schedule.id))
                }
            }
            schedulerManager.updateExecutedNow(scheduleId)
        }

        log(TAG) { "Finished processing schedule alarm" }
        asyncPi.finish()
    }

    companion object {
        internal val TAG = logTag("Scheduler", "Receiver")
    }
}
