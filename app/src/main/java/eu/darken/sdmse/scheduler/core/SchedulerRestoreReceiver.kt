package eu.darken.sdmse.scheduler.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toPkgId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SchedulerRestoreReceiver : BroadcastReceiver() {

    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var schedulerManager: SchedulerManager

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

        log(TAG, Logging.Priority.INFO) { "$pkg was uninstalled" }


        val asyncPi = goAsync()

        appScope.launch {
//            val scanTask = UninstallWatcherTask(pkg)
//            taskManager.submit(scanTask)

            log(TAG) { "Finished watcher trigger" }
            asyncPi.finish()
        }


    }

    companion object {
        internal val TAG = logTag("Scheduler", "Receiver")
    }
}
