package eu.darken.sdmse.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepo
) : ViewModel3(dispatcherProvider) {

    val isPro = upgradeRepo.upgradeInfo.map { it.isPro }.asLiveData2()

    companion object {
        private val TAG = logTag("Settings", "General", "ViewModel")
    }
}