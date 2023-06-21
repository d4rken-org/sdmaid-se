package eu.darken.sdmse.corpsefinder.core.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
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
import eu.darken.sdmse.common.pkgs.toPkgId
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.tasks.UninstallWatcherTask
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UninstallWatcherReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var corpseFinderSettings: CorpseFinderSettings

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

        // TODO did we uninstall this?
        if (AppControl.lastUninstalledPkg == pkg) {
            log(TAG, INFO) { "Skipping check, SD Maid was open, we did this" }
            return
        }

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Uninstall event")

        appScope.launch {
            if (!corpseFinderSettings.isWatcherEnabled.value()) {
                log(TAG, WARN) { "Uninstall watcher is disabled in settings, skipping." }
                return@launch
            }

            val task = UninstallWatcherTask(
                target = pkg,
                autoDelete = corpseFinderSettings.isWatcherAutoDeleteEnabled.value()
            )
            try {
                log(TAG) { "Submitting task: $task" }
                taskManager.submit(task)
            } catch (e: Exception) {
                log(TAG, ERROR) { "Uninstall task ($task) failed: ${e.asLog()}" }
            }
        }

        appScope.launch {
            delay(3000)
            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }
    }

    companion object {
        internal val TAG = logTag("CorpseFinder", "Watcher", "Uninstall", "Receiver")
    }
}
