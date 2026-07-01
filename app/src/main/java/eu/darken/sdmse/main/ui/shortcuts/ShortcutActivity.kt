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
import eu.darken.sdmse.common.upgrade.isPro
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import kotlinx.coroutines.Dispatchers
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
        if (!upgradeRepo.isPro()) {
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

        // Show "started" up front: submit() suspends until each task finishes, so showing it after
        // the submits would land only once everything is already done.
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@ShortcutActivity,
                getString(R.string.shortcut_onetap_started),
                Toast.LENGTH_SHORT,
            ).show()
        }

        if (corpseEnabled) {
            try {
                taskManager.submit(CorpseFinderOneClickTask())
            } catch (e: Exception) {
                log(TAG) { "Failed to submit CorpseFinderOneClickTask: $e" }
            }
        }
        if (systemEnabled) {
            try {
                taskManager.submit(SystemCleanerOneClickTask())
            } catch (e: Exception) {
                log(TAG) { "Failed to submit SystemCleanerOneClickTask: $e" }
            }
        }
        if (appCleanerEnabled) {
            try {
                taskManager.submit(AppCleanerOneClickTask(shortcutMode = true))
            } catch (e: Exception) {
                log(TAG) { "Failed to submit AppCleanerOneClickTask: $e" }
            }
        }
        if (deduplicatorEnabled) {
            try {
                taskManager.submit(DeduplicatorOneClickTask())
            } catch (e: Exception) {
                log(TAG) { "Failed to submit DeduplicatorOneClickTask: $e" }
            }
        }
    }

    companion object {
        private val TAG = logTag("Shortcut", "Activity")

        const val ACTION_OPEN_APPCONTROL = "eu.darken.sdmse.ACTION_OPEN_APPCONTROL"
        const val ACTION_OPEN_ANALYZER = "eu.darken.sdmse.ACTION_OPEN_ANALYZER"
        const val ACTION_SCAN_DELETE = "eu.darken.sdmse.ACTION_SCAN_DELETE"
        const val ACTION_UPGRADE = "eu.darken.sdmse.ACTION_UPGRADE"

        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    }
}