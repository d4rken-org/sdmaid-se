package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ExternalWatcherTaskReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var corpseFinderSettings: CorpseFinderSettings
    @Inject lateinit var uninstallWatcherNotifications: UninstallWatcherNotifications

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (intent.action != TASK_INTENT) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        val externalTask = intent.getParcelableExtra<ExternalWatcherTask>(EXTRA_TASK)

        if (externalTask == null) {
            log(TAG, WARN) { "Task was NULL: $intent ($context)" }
            return
        } else {
            log(TAG, INFO) { "Received task is $externalTask" }
        }

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Watcher task event")

        appScope.launch {
            uninstallWatcherNotifications.clearNotifications()

            val internalTask = when (externalTask) {
                is ExternalWatcherTask.Delete -> UninstallWatcherTask(
                    target = externalTask.target,
                    autoDelete = true
                )
            }

            try {
                log(TAG) { "Submitting task: $internalTask" }
                taskManager.submit(internalTask)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Uninstall task ($internalTask) failed: ${e.asLog()}" }
            }
        }

        appScope.launch {
            delay(3000)
            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }
    }

    companion object {
        const val EXTRA_TASK = "corpsefinder.watcher.uninstall.task"
        const val TASK_INTENT = "corpsefinder.watcher.uninstall.intent.NEW_TASK"
        internal val TAG = logTag("CorpseFinder", "Watcher", "Task", "Receiver")
    }
}
