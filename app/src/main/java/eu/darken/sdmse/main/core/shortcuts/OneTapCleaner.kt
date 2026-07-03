package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.isProForUi
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderOneClickTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorOneClickTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared execution of the "one-tap" (scan + delete) and scan-only cleaning runs. Extracted from
 * [ShortcutActivity] so every entry point — launcher shortcut, home-screen widget, and the in-app
 * widget-consent prompt — runs the *exact same* guarded logic: Pro gate, [OneTapRunGuard]
 * single-flight, and per-tool cancellation handling.
 *
 * [runOneClick] must be invoked from a coroutine on a long-lived scope (e.g. `AppScope`), because
 * [OneTapRunGuard] registers that coroutine's job so a widget "Cancel" can abort not-yet-submitted
 * tools even after the caller UI is gone.
 */
@Singleton
class OneTapCleaner @Inject constructor(
    private val taskManager: TaskManager,
    private val generalSettings: GeneralSettings,
    private val upgradeRepo: UpgradeRepo,
    private val oneTapRunGuard: OneTapRunGuard,
) {

    sealed interface Outcome {
        /** Not a Pro user — the caller should route to the upgrade screen. */
        data object NotPro : Outcome

        /** No one-click tools are enabled — nothing to run. */
        data object NothingEnabled : Outcome

        /** A one-tap run is already in progress (single-flight) — the caller may open the app. */
        data object AlreadyRunning : Outcome

        /** The run started; since the submits suspend, it has finished by the time this returns. */
        data object Ran : Outcome
    }

    /**
     * Runs the guarded one-click scan + delete for every enabled tool. [onStarted] fires once the
     * guard is acquired and before the (suspending) submits, so the caller can surface a "started"
     * hint at the right moment. Suspends until the whole sequence completes.
     */
    suspend fun runOneClick(shortcutMode: Boolean, onStarted: suspend () -> Unit = {}): Outcome {
        if (!upgradeRepo.isProForUi()) {
            log(TAG, INFO) { "runOneClick(): requires Pro" }
            return Outcome.NotPro
        }

        val corpseEnabled = generalSettings.oneClickCorpseFinderEnabled.value()
        val systemEnabled = generalSettings.oneClickSystemCleanerEnabled.value()
        val appCleanerEnabled = generalSettings.oneClickAppCleanerEnabled.value()
        val deduplicatorEnabled = generalSettings.oneClickDeduplicatorEnabled.value()

        if (!corpseEnabled && !systemEnabled && !appCleanerEnabled && !deduplicatorEnabled) {
            log(TAG, INFO) { "runOneClick(): no one-tap tools enabled" }
            return Outcome.NothingEnabled
        }

        // Single-flight: if a OneTap run is already in progress, don't stack another. Registering
        // this coroutine's job lets a widget "Cancel" abort not-yet-submitted tools too.
        if (!oneTapRunGuard.tryStart(currentCoroutineContext().job)) {
            log(TAG, INFO) { "runOneClick(): already running" }
            return Outcome.AlreadyRunning
        }

        try {
            onStarted()
            if (corpseEnabled) submitOneTapTask(CorpseFinderOneClickTask())
            if (systemEnabled) submitOneTapTask(SystemCleanerOneClickTask())
            if (appCleanerEnabled) submitOneTapTask(AppCleanerOneClickTask(shortcutMode = shortcutMode))
            if (deduplicatorEnabled) submitOneTapTask(DeduplicatorOneClickTask())
        } finally {
            oneTapRunGuard.finish()
        }
        return Outcome.Ran
    }

    /**
     * Submits a plain *scan* (no deletion) for every enabled one-click tool, so the dashboard can
     * present the results for a normal confirmed delete. Best-effort per tool.
     */
    suspend fun runScanOnly() {
        log(TAG, INFO) { "runScanOnly()" }
        if (generalSettings.oneClickCorpseFinderEnabled.value()) submitScanTask(CorpseFinderScanTask())
        if (generalSettings.oneClickSystemCleanerEnabled.value()) submitScanTask(SystemCleanerScanTask())
        if (generalSettings.oneClickAppCleanerEnabled.value()) submitScanTask(AppCleanerScanTask())
        if (generalSettings.oneClickDeduplicatorEnabled.value()) submitScanTask(DeduplicatorScanTask())
    }

    private suspend fun submitScanTask(task: SDMTool.Task) {
        try {
            currentCoroutineContext().ensureActive()
            taskManager.submit(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG) { "Failed to submit scan ${task::class.simpleName}: $e" }
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
                // NonCancellable block after the cancel sweep — cancel its type again so nothing
                // keeps running.
                taskManager.cancel(task.type)
                throw e
            }
            // A per-tool cancel (e.g. from the dashboard) only skips that tool.
            log(TAG) { "${task::class.simpleName} was cancelled individually, continuing run: $e" }
        } catch (e: Exception) {
            log(TAG) { "Failed to submit ${task::class.simpleName}: $e" }
        }
    }

    companion object {
        private val TAG = logTag("OneTap", "Cleaner")

        /** The tools the one-click run submits and the cancel action targets (dashboard parity). */
        val ONECLICK_TYPES = setOf(
            SDMTool.Type.CORPSEFINDER,
            SDMTool.Type.SYSTEMCLEANER,
            SDMTool.Type.APPCLEANER,
            SDMTool.Type.DEDUPLICATOR,
        )
    }
}
