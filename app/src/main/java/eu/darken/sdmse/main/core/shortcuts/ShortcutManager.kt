package eu.darken.sdmse.main.core.shortcuts

import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.combine
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
) {

    private val shortcutManager: ShortcutManager by lazy {
        context.getSystemService(ShortcutManager::class.java)
    }

    fun initialize() {
        log(TAG, INFO) { "Initializing" }
        combine(
            generalSettings.shortcutOneClickEnabled.flow,
            generalSettings.shortcutCleanCorpseFinderEnabled.flow,
            generalSettings.shortcutCleanSystemCleanerEnabled.flow,
            generalSettings.shortcutCleanAppCleanerEnabled.flow,
            generalSettings.shortcutCleanDeduplicatorEnabled.flow,
            configurationGenerations(),
        ) { oneTap, corpseFinder, systemCleaner, appCleaner, deduplicator, generation ->
            ShortcutState(
                oneTapShortCutEnabled = oneTap,
                cleanTools = OneTapCleaner.ONECLICK_TYPES.filter {
                    when (it) {
                        SDMTool.Type.CORPSEFINDER -> corpseFinder
                        SDMTool.Type.SYSTEMCLEANER -> systemCleaner
                        SDMTool.Type.APPCLEANER -> appCleaner
                        SDMTool.Type.DEDUPLICATOR -> deduplicator
                        // A tool without its own opt-in must not inherit another tool's setting.
                        else -> false
                    }
                },
                configGeneration = generation,
            )
        }
            .distinctUntilChanged()
            .map { state ->
                Publication(
                    shortcuts = computeShortcuts(state),
                    // Part of the distinct key on purpose: a locale change leaves the list equal but
                    // must still republish so the labels re-resolve in the new language.
                    configGeneration = state.configGeneration,
                )
            }
            .distinctUntilChanged()
            .onEach { updateShortcuts(it) }
            .catch { log(TAG, ERROR) { "Failed to update shortcuts: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    /**
     * Emits once on collection, then on every LOCALE change. We listen for configuration changes
     * rather than ACTION_LOCALE_CHANGED because the app's own per-app language picker never
     * broadcasts that, but it does deliver a configuration change.
     *
     * Configuration changes that leave the locales alone (rotation, dark mode, font scale) are
     * dropped: only the locales can alter what we publish, since the labels are resolved through
     * getString and the icons are resource ids the launcher resolves itself. Republishing on those
     * would rebuild every ShortcutInfo for a binder call that changes nothing.
     */
    private fun configurationGenerations(): Flow<Int> = callbackFlow {
        var generation = 0
        var lastLocales = context.resources.configuration.locales
        send(generation)
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                if (newConfig.locales == lastLocales) return
                lastLocales = newConfig.locales
                generation++
                log(TAG) { "onConfigurationChanged(): locales=$lastLocales, generation=$generation" }
                trySend(generation)
            }

            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)
        awaitClose { context.unregisterComponentCallbacks(callbacks) }
    }.conflate()

    private data class ShortcutState(
        val oneTapShortCutEnabled: Boolean,
        val cleanTools: List<SDMTool.Type>,
        val configGeneration: Int,
    )

    private data class Publication(
        val shortcuts: List<AppShortcut>,
        val configGeneration: Int,
    )

    private fun computeShortcuts(state: ShortcutState): List<AppShortcut> {
        // Defensive only: the platform default is 15, but the limit is device-configurable and
        // setDynamicShortcuts() throws when it is exceeded.
        val maxCount = shortcutManager.maxShortcutCountPerActivity
        val requested = buildShortcuts(
            oneTapEnabled = state.oneTapShortCutEnabled,
            cleanTools = state.cleanTools,
            maxCount = Int.MAX_VALUE,
        )
        val shortcuts = buildShortcuts(
            oneTapEnabled = state.oneTapShortCutEnabled,
            cleanTools = state.cleanTools,
            maxCount = maxCount,
        )
        if (shortcuts.size < requested.size) {
            log(TAG, INFO) { "Device limit is $maxCount, dropped ${requested.size - shortcuts.size} shortcut(s)" }
        }
        return shortcuts
    }

    private fun updateShortcuts(publication: Publication) {
        log(TAG, INFO) { "updateShortcuts(): $publication" }

        try {
            if (publication.shortcuts.isEmpty()) {
                shortcutManager.removeAllDynamicShortcuts()
                log(TAG, INFO) { "Removed all dynamic shortcuts." }
                return
            }

            val infos = publication.shortcuts.mapIndexed { index, shortcut ->
                shortcut.toShortcutInfo(context, rank = index)
            }
            // Returns false (it does not throw) when we are rate limited while backgrounded. No
            // retry: initialize() runs from App.onCreate, so the next launch republishes anyway.
            if (shortcutManager.setDynamicShortcuts(infos)) {
                log(TAG, INFO) { "Updated shortcuts." }
            } else {
                log(TAG, WARN) { "setDynamicShortcuts() was rejected, rate limited?" }
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to update shortcuts: $e" }
        }
    }

    companion object {
        private val TAG = logTag("Shortcut", "Manager")
    }
}
