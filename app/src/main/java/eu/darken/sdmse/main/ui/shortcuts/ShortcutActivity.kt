package eu.darken.sdmse.main.ui.shortcuts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coroutine.AppCoroutineScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.shortcuts.OneTapCleaner
import eu.darken.sdmse.main.core.shortcuts.OneTapRunGuard
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutActivity : ComponentActivity() {

    @Inject lateinit var taskManager: TaskManager
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var appScope: AppCoroutineScope
    @Inject lateinit var oneTapRunGuard: OneTapRunGuard
    @Inject lateinit var oneTapCleaner: OneTapCleaner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent?.action
        log(TAG, INFO) { "Shortcut action received: $action" }

        when (action) {
            ACTION_OPEN_APPCONTROL -> {
                startActivity(mainActivityIntent(ACTION_OPEN_APPCONTROL))
            }

            ACTION_SCAN_DELETE -> {
                handleScanDeleteShortcut()
            }

            ACTION_WIDGET_SCAN_DELETE -> {
                handleWidgetClean()
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

    /**
     * Launcher "Scan + Delete" shortcut. Gated on [GeneralSettings.shortcutOneClickEnabled]: this
     * activity is exported and the intent-filtered [ACTION_SCAN_DELETE] can be reached by a stale
     * pinned shortcut or an external caller, so we re-check the opt-in at runtime rather than trust
     * that the dynamic shortcut is only registered while enabled. Disabled → just open the app.
     */
    private fun handleScanDeleteShortcut() = appScope.launch {
        if (!generalSettings.shortcutOneClickEnabled.value()) {
            log(TAG, INFO) { "One-tap shortcut disabled, opening app instead of scan+delete" }
            openMain(null)
            return@launch
        }
        runOneTap()
    }

    /**
     * Home-screen widget "Clean" button. Never scan+deletes without opt-in:
     * - one-tap enabled → run it in the background (no app open), like the shortcut;
     * - not yet consented → open the app and show the one-time consent prompt;
     * - consent declined earlier → open the app and run a scan (results shown for a confirmed delete).
     */
    private fun handleWidgetClean() = appScope.launch {
        when {
            generalSettings.widgetOneClickEnabled.value() -> runOneTap()

            !generalSettings.widgetOneClickConsentAsked.value() -> {
                log(TAG, INFO) { "Widget one-tap not consented yet, opening consent prompt" }
                openMain(ACTION_WIDGET_CONSENT)
            }

            else -> {
                log(TAG, INFO) { "Widget one-tap declined earlier, running scan fallback" }
                // Open the dashboard first — submit() suspends until the scan finishes, and the
                // dashboard reflects TaskManager state reactively, so it shows progress + results.
                openMain(ACTION_WIDGET_SCAN)
                oneTapCleaner.runScanOnly()
            }
        }
    }

    /** Runs the shared guarded one-tap and maps its outcome to the trampoline's UI affordances. */
    private suspend fun runOneTap() {
        log(TAG, INFO) { "Executing scan and delete tasks" }
        val outcome = oneTapCleaner.runOneClick(shortcutMode = true) {
            // Show "started" up front: submit() suspends until each task finishes, so a toast after
            // the submits would land only once everything is already done.
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ShortcutActivity,
                    getString(R.string.shortcut_onetap_started),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        when (outcome) {
            OneTapCleaner.Outcome.NotPro -> {
                log(TAG, INFO) { "One-tap requires Pro, opening upgrade screen" }
                openMain(ACTION_UPGRADE)
            }

            OneTapCleaner.Outcome.NothingEnabled -> withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ShortcutActivity,
                    getString(R.string.shortcut_onetap_nothing_enabled),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            // A run is already in progress — open the app so the user can watch progress.
            OneTapCleaner.Outcome.AlreadyRunning -> openMain(null)

            OneTapCleaner.Outcome.Ran -> {}
        }
    }

    /**
     * Widget "Cancel" while working: stop the OneTap sequence (pending submits never start) and
     * cancel the one-click cleaning tools' active/queued tasks — the same scope as the dashboard's
     * cancel action. Other tools (Analyzer, AppControl, …) keep running. Not Pro-gated.
     */
    private fun handleCancelOneClick() {
        log(TAG, INFO) { "Cancelling OneTap run + one-click cleaning tasks" }
        oneTapRunGuard.cancelRun()
        OneTapCleaner.ONECLICK_TYPES.forEach { taskManager.cancel(it) }
    }

    /** Launch [MainActivity] on the main thread — these run from the app-scope (Default) coroutines. */
    private suspend fun openMain(shortcutAction: String?) = withContext(Dispatchers.Main) {
        startActivity(mainActivityIntent(shortcutAction))
    }

    private fun mainActivityIntent(shortcutAction: String?): Intent =
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            shortcutAction?.let { putExtra(EXTRA_SHORTCUT_ACTION, it) }
        }

    companion object {
        private val TAG = logTag("Shortcut", "Activity")

        const val ACTION_OPEN_APPCONTROL = "eu.darken.sdmse.ACTION_OPEN_APPCONTROL"
        const val ACTION_OPEN_ANALYZER = "eu.darken.sdmse.ACTION_OPEN_ANALYZER"
        const val ACTION_SCAN_DELETE = "eu.darken.sdmse.ACTION_SCAN_DELETE"
        const val ACTION_CANCEL_ONECLICK = "eu.darken.sdmse.ACTION_CANCEL_ONECLICK"
        const val ACTION_UPGRADE = "eu.darken.sdmse.ACTION_UPGRADE"

        /** Widget Clean button → this activity (explicit component, not in the exported filter). */
        const val ACTION_WIDGET_SCAN_DELETE = "eu.darken.sdmse.ACTION_WIDGET_SCAN_DELETE"

        /** [EXTRA_SHORTCUT_ACTION] values routed to [MainActivity] for the widget consent flow. */
        const val ACTION_WIDGET_CONSENT = "eu.darken.sdmse.ACTION_WIDGET_CONSENT"
        const val ACTION_WIDGET_SCAN = "eu.darken.sdmse.ACTION_WIDGET_SCAN"

        const val EXTRA_SHORTCUT_ACTION = "shortcut_action"
    }
}
