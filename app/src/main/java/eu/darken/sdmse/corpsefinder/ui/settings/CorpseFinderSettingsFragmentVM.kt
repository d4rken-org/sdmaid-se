package eu.darken.sdmse.corpsefinder.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.toggleSelfComponent
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinderSettings
import eu.darken.sdmse.corpsefinder.core.watcher.UninstallWatcherReceiver
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class CorpseFinderSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
    private val settings: CorpseFinderSettings,
    private val rootManager: RootManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    init {
        settings.isUninstallWatcherEnabled.flow
            .drop(1)
            .onEach { enabled ->
                packageManager.toggleSelfComponent(
                    ComponentName(context, UninstallWatcherReceiver::class.java),
                    enabled
                )
                log(TAG, INFO) { "New uninstall watcher state: enabled=$enabled" }
            }
            .launchInViewModel()
    }

    val state = combine(
        rootManager.useRoot,
        upgradeRepo.upgradeInfo.map { it.isPro }
    ) { isRooted, isPro ->
        State(
            isRooted = isRooted,
            isPro = isPro,
        )
    }.asLiveData2()

    data class State(
        val isRooted: Boolean,
        val isPro: Boolean
    )

    companion object {
        private val TAG = logTag("Settings", "CorpseFinder", "VM")
    }
}