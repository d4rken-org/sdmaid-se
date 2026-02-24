package eu.darken.sdmse.appcontrol.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.appcontrol.core.AppControlSettings
import eu.darken.sdmse.appcontrol.core.FilterSettings
import eu.darken.sdmse.appcontrol.core.SortSettings
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


@HiltViewModel
class AppControlSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    appControl: AppControl,
    upgradeRepo: UpgradeRepo,
    private val settings: AppControlSettings,
) : ViewModel3(dispatcherProvider) {

    init {
        settings.moduleSizingEnabled.flow
            .onEach { enabled ->
                if (!enabled && settings.listSort.value().mode == SortSettings.Mode.SIZE) {
                    settings.listSort.value(SortSettings())
                }
            }
            .launchInViewModel()
        settings.moduleActivityEnabled.flow
            .onEach { enabled ->
                val curFilter = settings.listFilter.value()
                if (!enabled && curFilter.tags.contains(FilterSettings.Tag.ACTIVE)) {
                    settings.listFilter.value(
                        curFilter.copy(tags = curFilter.tags - FilterSettings.Tag.ACTIVE)
                    )
                }
            }
            .launchInViewModel()
    }

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