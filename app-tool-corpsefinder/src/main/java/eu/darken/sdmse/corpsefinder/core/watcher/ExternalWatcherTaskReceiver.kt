package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.isValidHiltContext
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExternalWatcherTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (intent.action != TASK_INTENT) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        @Suppress("DEPRECATION")
        val externalTask = intent.getParcelableExtra<ExternalWatcherTask>(EXTRA_TASK)

        if (externalTask == null) {
            log(TAG, WARN) { "Task was NULL: $intent ($context)" }
            return
        } else {
            log(TAG, INFO) { "Received task is $externalTask" }
        }

        if (!context.isValidHiltContext()) {
            log(TAG, WARN) { "Invalid Hilt context (${context.applicationContext.javaClass}), skipping (backup/restore?)" }
            return
        }

        val entryPoint = try {
            EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to get entry point: $e" }
            return
        }

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Watcher task event")

        entryPoint.appScope().launch {
            entryPoint.uninstallWatcherNotifications().clearNotifications()

            val internalTask = when (externalTask) {
                is ExternalWatcherTask.Delete -> UninstallWatcherTask(
                    target = externalTask.target,
                    autoDelete = true
                )
            }

            try {
                log(TAG) { "Submitting task: $internalTask" }
                entryPoint.taskSubmitter().submit(internalTask)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Uninstall task ($internalTask) failed: ${e.asLog()}" }
            }
        }

        entryPoint.appScope().launch {
            delay(3000)
            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }
    }

    companion object {
        const val EXTRA_TASK = "corpsefinder.watcher.uninstall.task"
        const val TASK_INTENT = "corpsefinder.watcher.uninstall.intent.NEW_TASK"
        internal val TAG = logTag("CorpseFinder", "Watcher", "Task", "Receiver")

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface ReceiverEntryPoint {
            @AppScope fun appScope(): CoroutineScope
            fun taskSubmitter(): TaskSubmitter
            fun uninstallWatcherNotifications(): UninstallWatcherNotifications
        }
    }
}
