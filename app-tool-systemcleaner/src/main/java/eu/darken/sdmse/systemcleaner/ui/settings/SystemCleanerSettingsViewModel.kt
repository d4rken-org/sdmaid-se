package eu.darken.sdmse.systemcleaner.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SystemCleanerSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    upgradeRepo: UpgradeRepo,
    systemCleaner: SystemCleaner,
    private val settings: SystemCleanerSettings,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        systemCleaner.state,
        upgradeRepo.upgradeInfo.map { it.isPro }
    ) { state, isPro ->
        State(
            areSystemFilterAvailable = state.areSystemFilterAvailable,
            isPro = isPro,
        )
    }.asLiveData2()

    val screenshotsAge = settings.filterScreenshotsAge.flow.asLiveData2()

    data class State(
        val areSystemFilterAvailable: Boolean,
        val isPro: Boolean
    )


    companion object {
        private val TAG = logTag("Settings", "SystemCleaner", "ViewModel")
    }
}