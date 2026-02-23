package eu.darken.sdmse.scheduler.core

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
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.isValidHiltContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch


class SchedulerRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        log(TAG) { "onReceive($context,$intent)" }
        if (!ALLOWED_INTENTS.contains(intent.action)) {
            log(TAG, ERROR) { "Unknown intent: $intent" }
            return
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

        log(TAG, INFO) { "Rechecking scheduler states (intent.data=${intent.data})" }

        val asyncPi = goAsync()

        Bugs.leaveBreadCrumb("Scheduler restore")

        entryPoint.appScope().launch {
            entryPoint.schedulerManager().state.take(1).first()
            // The manager checks the scheduling states automatically when initialised, so just give it some time
            delay(3000)

            log(TAG) { "Finished scheduler checks" }
            asyncPi.finish()
        }
    }

    companion object {
        private val ALLOWED_INTENTS = setOf(
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_BOOT_COMPLETED
        )
        internal val TAG = logTag("Scheduler", "Receiver", "Restore")

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface ReceiverEntryPoint {
            @AppScope fun appScope(): CoroutineScope
            fun schedulerManager(): SchedulerManager
        }
    }
}
