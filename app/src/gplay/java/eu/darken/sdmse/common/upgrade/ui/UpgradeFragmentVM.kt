package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class UpgradeFragmentVM @Inject constructor(
    @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    fun onGoSubscription(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlowSubscription(activity)

    }

    fun onGoIap(activity: Activity) {
        log(TAG) { "onGoIap($activity)" }
        upgradeRepo.launchBillingFlowIap(activity)
    }


    companion object {
        private val TAG = logTag("Upgrade", "Gplay", "Fragment", "VM")
    }
}