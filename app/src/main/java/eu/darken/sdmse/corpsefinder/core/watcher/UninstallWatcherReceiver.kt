package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.isValidHiltContext
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UninstallWatcherReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
        }

        val uri = intent.data
        val pkg = uri?.schemeSpecificPart?.toPkgId()
        if (pkg == null) {
            log(TAG, ERROR) { "Package data was null" }
            return
        }

        log(TAG, INFO) { "$pkg was uninstalled" }

        if (AppControl.lastUninstalledPkg == pkg) {
            log(TAG, INFO) { "We uninstall this app, but let's still do our thing :)" }
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

        Bugs.leaveBreadCrumb("Uninstall event")

        entryPoint.appScope().launch {
            if (!entryPoint.corpseFinderSettings().isWatcherEnabled.value()) {
                log(TAG, WARN) { "Uninstall watcher is disabled in settings, skipping." }
                return@launch
            }

            val task = UninstallWatcherTask(
                target = pkg,
                autoDelete = entryPoint.corpseFinderSettings().isWatcherAutoDeleteEnabled.value()
            )
            try {
                log(TAG) { "Submitting task: $task" }
                entryPoint.taskManager().submit(task)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Uninstall task ($task) failed: ${e.asLog()}" }
            }
        }

        entryPoint.appScope().launch {
            delay(3000)
            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }
    }

    companion object {
        internal val TAG = logTag("CorpseFinder", "Watcher", "Uninstall", "Receiver")

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface ReceiverEntryPoint {
            @AppScope fun appScope(): CoroutineScope
            fun taskManager(): TaskManager
            fun corpseFinderSettings(): CorpseFinderSettings
        }
    }
}
