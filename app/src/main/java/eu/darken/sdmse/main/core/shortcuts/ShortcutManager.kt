package eu.darken.sdmse.main.core.shortcuts

import android.content.Context
import android.content.pm.ShortcutManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.main.core.GeneralSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val appScope: CoroutineScope,
    private val generalSettings: GeneralSettings,
) {

    private val shortcutManager: ShortcutManager by lazy {
        context.getSystemService(ShortcutManager::class.java)
    }

    fun initialize() {
        log(TAG, INFO) { "Initializing" }
        generalSettings.shortcutOneClickEnabled.flow
            .map { oneTap ->
                ShortcutState(
                    oneTapShortCutEnabled = oneTap
                )
            }
            .distinctUntilChanged()
            .onEach { updateShortcuts(it) }
            .catch { log(TAG, ERROR) { "Failed to update shortcuts: ${it.asLog()}" } }
            .launchIn(appScope)
    }

    private data class ShortcutState(
        val oneTapShortCutEnabled: Boolean,
    )

    private suspend fun updateShortcuts(state: ShortcutState) {
        log(TAG, INFO) { "updateShortcuts(): $state" }

        val shortcuts = buildList {
            add(AppShortcut.AppControl.toShortcutInfo(context))
            if (state.oneTapShortCutEnabled) {
                add(AppShortcut.MainAction.OneTap.toShortcutInfo(context))
            }
        }

        try {
            shortcutManager.dynamicShortcuts = shortcuts
            log(TAG, INFO) { "Updated shortcuts." }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to update shortcuts: $e" }
        }
    }

    companion object {
        private val TAG = logTag("Shortcut", "Manager")
    }
}