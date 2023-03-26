package eu.darken.sdmse.systemcleaner.ui.settings

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SystemCleanerSettingsFragmentVM @Inject constructor(
    private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val rootManager: RootManager,
    private val upgradeRepo: UpgradeRepo,
) : ViewModel3(dispatcherProvider) {

    val state = combine(
        flow { emit(rootManager.useRoot()) },
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
        private val TAG = logTag("Settings", "SystemCleaner", "VM")
    }
}