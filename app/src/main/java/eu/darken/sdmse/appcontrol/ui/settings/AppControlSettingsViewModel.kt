package eu.darken.sdmse.appcontrol.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject


@HiltViewModel
class AppControlSettingsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    appControl: AppControl,
    settings: AppControlSettings,
    upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        appControl.state,
        upgradeRepo.upgradeInfo.map { it.isPro },
    ) { state, isPro ->
        State(
            state = state,
            isPro = isPro,
        )
    }.asLiveData2()

    data class State(
        val state: AppControl.State,
        val isPro: Boolean,
    )

    companion object {
        private val TAG = logTag("Settings", "AppControl", "ViewModel")
    }
}