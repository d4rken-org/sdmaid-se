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
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
            generalSettings.shortcutToolConfig.flow,
            generalSettings.dashboardCardConfig.flow,
            configurationGenerations(),
        ) { oneTap, toolConfig, cardConfig, generation ->
            ShortcutState(
                oneTapShortCutEnabled = oneTap,
                tools = toolConfig.tools,
                cardOrder = cardConfig.cards.map { it.type },
                configGeneration = generation,
            )
        }
            // Compute first, then dedupe: a dashboard card VISIBILITY toggle changes the settings
            // but can't change the shortcuts, and would otherwise burn a publish call.
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
     * Emits once on collection, then on every configuration change. Registered against the
     * application context rather than listening for ACTION_LOCALE_CHANGED: the app's own per-app
     * language picker never broadcasts that, but it does deliver a configuration change.
     */
    private fun configurationGenerations(): Flow<Int> = callbackFlow {
        var generation = 0
        send(generation)
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                generation++
                log(TAG) { "onConfigurationChanged(): generation=$generation" }
                trySend(generation)
            }

            override fun onLowMemory() {}
        }
        context.registerComponentCallbacks(callbacks)
        awaitClose { context.unregisterComponentCallbacks(callbacks) }
    }.conflate()

    private data class ShortcutState(
        val oneTapShortCutEnabled: Boolean,
        val tools: List<DashboardCardType>,
        val cardOrder: List<DashboardCardType>,
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
            enabled = state.tools,
            cardOrder = state.cardOrder,
            maxCount = Int.MAX_VALUE,
        )
        val shortcuts = buildShortcuts(
            oneTapEnabled = state.oneTapShortCutEnabled,
            enabled = state.tools,
            cardOrder = state.cardOrder,
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

            val infos = publication.shortcuts.map { it.toShortcutInfo(context) }
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
