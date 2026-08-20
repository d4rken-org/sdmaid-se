package eu.darken.sdmse.main.core.shortcuts

import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime routing for the per-tool "clean" launcher shortcuts. Extracted from `ShortcutActivity`
 * (like [OneTapCleaner] before it) so the exported surface's decisions — which tool the extra names,
 * whether that tool's shortcut is still opted in, and where the outcome sends the user — are plain
 * logic instead of activity code.
 *
 * Deliberately free of `Context`/`Activity`: the caller applies the returned [Route] and supplies
 * the "started" hint via [route]'s callback.
 */
@Singleton
class CleanShortcutRouter @Inject constructor(
    private val generalSettings: GeneralSettings,
    private val oneTapCleaner: OneTapCleaner,
) {

    sealed interface Route {
        /** Open the app's main screen — nothing was cleaned. */
        data object OpenApp : Route

        /** Open the app on the upgrade screen — the clean requires Pro. */
        data object OpenUpgrade : Route

        /** The clean ran in the background, no UI needed. */
        data object Nowhere : Route
    }

    /**
     * Resolves [toolName], re-checks that tool's opt-in and runs its scan + delete. The opt-in is
     * re-read here rather than trusted, because the shortcut trampoline is exported and a stale
     * pinned shortcut or an external caller can ask for a tool the user has since switched off.
     * Unknown, absent or disabled tool → [Route.OpenApp], without starting any clean.
     *
     * [onStarted] fires once the run is actually underway (never for a rejected outcome), so the
     * caller can surface a "started" hint while the work is still going.
     */
    suspend fun route(toolName: String?, onStarted: suspend () -> Unit = {}): Route {
        val type = resolveCleanShortcutTool(toolName)
        if (type == null) {
            log(TAG, WARN) { "Unknown clean shortcut tool '$toolName', opening app instead" }
            return Route.OpenApp
        }

        if (!isEnabled(type)) {
            log(TAG, INFO) { "Clean shortcut for $type is disabled, opening app instead" }
            return Route.OpenApp
        }

        return when (val outcome = oneTapCleaner.runSingleTool(type, shortcutMode = true, onStarted = onStarted)) {
            OneTapCleaner.Outcome.NotPro -> {
                log(TAG, INFO) { "Clean shortcut requires Pro, opening upgrade screen" }
                Route.OpenUpgrade
            }

            // A run is already in progress — open the app so the user can watch progress.
            OneTapCleaner.Outcome.AlreadyRunning -> Route.OpenApp

            OneTapCleaner.Outcome.NothingEnabled, OneTapCleaner.Outcome.Ran -> {
                log(TAG, INFO) { "Clean shortcut for $type finished: $outcome" }
                Route.Nowhere
            }
        }
    }

    private suspend fun isEnabled(type: SDMTool.Type): Boolean = when (type) {
        SDMTool.Type.CORPSEFINDER -> generalSettings.shortcutCleanCorpseFinderEnabled
        SDMTool.Type.SYSTEMCLEANER -> generalSettings.shortcutCleanSystemCleanerEnabled
        SDMTool.Type.APPCLEANER -> generalSettings.shortcutCleanAppCleanerEnabled
        SDMTool.Type.DEDUPLICATOR -> generalSettings.shortcutCleanDeduplicatorEnabled
        // Unreachable: resolveCleanShortcutTool only returns ONECLICK_TYPES.
        else -> throw IllegalArgumentException("$type has no clean shortcut")
    }.value()

    companion object {
        private val TAG = logTag("Shortcut", "CleanRouter")
    }
}
