package eu.darken.sdmse.stats.ui.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.StatsRepo
import javax.inject.Inject

@HiltViewModel
class StatsSettingsViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    statsRepo: StatsRepo,
    upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    companion object {
        private val TAG = logTag("Settings", "Stats", "ViewModel")
    }
}