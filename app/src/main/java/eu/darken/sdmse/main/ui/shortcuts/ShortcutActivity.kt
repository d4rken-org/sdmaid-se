package eu.darken.sdmse.main.ui.shortcuts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.common.coroutine.AppCoroutineScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.shortcuts.OneTapRunGuard
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutActivity : ComponentActivity() {

    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var corpseFinder: CorpseFinder
    @Inject lateinit var systemCleaner: SystemCleaner
    @Inject lateinit var appCleaner: AppCleaner
    @Inject lateinit var deduplicator: Deduplicator
    @Inject lateinit var appScope: AppCoroutineScope
    @Inject lateinit var oneTapRunGuard: OneTapRunGuard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        log(TAG, INFO) { "Shortcut action received: $action" }

        when (action) {
            ACTION_OPEN_APPCONTROL -> {
                openAppControl()
            }

            ACTION_SCAN_DELETE -> {
                handleScanDeleteShortcut()
            }

            ACTION_CANCEL_ONECLICK -> {
                handleCancelOneClick()
            }

            else -> {
                log(TAG) { "Unknown shortcut action: $action" }
            }
        }
        finish()
    }

    private fun openAppControl() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SHORTCUT_ACTION, ACTION_OPEN_APPCONTROL)
        }
        startActivity(mainIntent)
    }

    private fun handleScanDeleteShortcut() = appScope.launch {
        if (!upgradeRepo.isProForUi()) {
            log(TAG, INFO) { "Scan/Delete shortcut requires Pro version, opening upgrade screen" }
            val upgradeIntent = Intent(this@ShortcutActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_SHORTCUT_ACTION, ACTION_UPGRADE)
            }
            withContext(Dispatchers.Main) {
                startActivity(upgradeIntent)
            }
            return@launch
        }

        log(TAG, INFO) { "Executing scan and delete tasks" }

        val corpseEnabled = generalSettings.oneClickCorpseFinderEnabled.value()
        val systemEnabled = generalSettings.oneClickSystemCleanerEnabled.value()
        val appCleanerEnabled = generalSettings.oneClickAppCleanerEnabled.value()
        val deduplicatorEnabled = generalSettings.oneClickDeduplicatorEnabled.value()

        if (!corpseEnabled && !systemEnabled && !appCleanerEnabled && !deduplicatorEnabled) {
            log(TAG, INFO) { "No one-tap tools are enabled, nothing to run" }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ShortcutActivity,
                    getString(R.string.shortcut_onetap_nothing_enabled),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return@launch
        }

        // Single-flight: if a OneTap run is already in progress, don't stack another — open the app so
        // the user can watch progress (the widget's Clean button is already "working" by now). Placed
        // after the Pro gate so the shared launcher shortcut keeps its non-Pro → upgrade behaviour.
        // Registering this coroutine's job lets the widget's Cancel abort not-yet-submitted tools too.
        if (!oneTapRunGuard.tryStart(coroutineContext.job)) {
            log(TAG, INFO) { "OneTap already running, opening app instead of starting again" }
            withContext(Dispatchers.Main) {
                startActivity(
                    Intent(this@ShortcutActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            return@launch
        }

        try {
            // Show "started" up front: submit() suspends until each task finishes, so showing it after
            // the submits would land only once everything is already done.
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ShortcutActivity,
                    getString(R.string.shortcut_onetap_started),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            if (corpseEnabled) submitOneTapTask(CorpseFinderOneClickTask())
            if (systemEnabled) submitOneTapTask(SystemCleanerOneClickTask())
            if (appCleanerEnabled) submitOneTapTask(AppCleanerOneClickTask(shortcutMode = true))
            if (deduplicatorEnabled) submitOneTapTask(DeduplicatorOneClickTask())
        } finally {
            oneTapRunGuard.finish()
        }
    }

    private suspend fun submitOneTapTask(task: SDMTool.Task) {
        try {
            // Don't queue a new task into an already-cancelled run: a cancel landing between two
            // submits wouldn't stop submit() from registering the next task (TaskManager queues it
            // inside a NonCancellable block before reaching a cancellable suspension).
            currentCoroutineContext().ensureActive()
            taskManager.submit(task)
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                // The RUN was cancelled (widget Cancel → OneTapRunGuard.cancelRun()): abort the
                // sequence. The submit may have slipped the task into the queue inside TaskManager's
                // NonCancellable block after handleCancelOneClick()'s sweep — cancel its type again
                // so nothing keeps running.
                taskManager.cancel(task.type)
                throw e
            }
            // A per-tool cancel (e.g. from the dashboard) only skips that tool. Swallowing this used
            // to be unconditional, which made cancelling the run impossible.
            log(TAG) { "${task::class.simpleName} was cancelled individually, continuing run: $e" }
        } catch (e: Exception) {
            log(TAG) { "Failed to submit ${task::class.simpleName}: $e" }
        }
    }

    /**
     * Widget "Cancel" while working: stop the OneTap sequence (pending submits never start) and cancel
     * the one-click cleaning tools' active/queued tasks — the same scope as the dashboard's cancel
     * action. Other tools (Analyzer, AppControl, …) keep running; they were started from in-app UI
     * that has its own cancel affordances. Not Pro-gated — cancelling is never a premium feature.
     */
    private fun handleCancelOneClick() {
        log(TAG, INFO) { "Cancelling OneTap run + one-click cleaning tasks" }
        oneTapRunGuard.cancelRun()
        ONECLICK_TYPES.forEach { taskManager.cancel(it) }
    }

    companion object {
        private val TAG = logTag("Shortcut", "Activity")

        const val ACTION_OPEN_APPCONTROL = "eu.darken.sdmse.ACTION_OPEN_APPCONTROL"
        const val ACTION_OPEN_ANALYZER = "eu.darken.sdmse.ACTION_OPEN_ANALYZER"
        const val ACTION_SCAN_DELETE = "eu.darken.sdmse.ACTION_SCAN_DELETE"
        const val ACTION_CANCEL_ONECLICK = "eu.darken.sdmse.ACTION_CANCEL_ONECLICK"
        const val ACTION_UPGRADE = "eu.darken.sdmse.ACTION_UPGRADE"

        /** The tools the one-click run submits and the cancel action targets (dashboard parity). */
        private val ONECLICK_TYPES = setOf(
            SDMTool.Type.CORPSEFINDER,
            SDMTool.Type.SYSTEMCLEANER,
            SDMTool.Type.APPCLEANER,
            SDMTool.Type.DEDUPLICATOR,
        )

        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    }
}