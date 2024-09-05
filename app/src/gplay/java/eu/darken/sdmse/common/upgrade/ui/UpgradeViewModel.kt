package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.SingleLiveEvent
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.navigation.navArgs
import eu.darken.sdmse.common.uix.ViewModel3
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeRepo: UpgradeRepoGplay,
) : ViewModel3(dispatcherProvider = dispatcherProvider) {

    private val navArgs by handle.navArgs<UpgradeFragmentArgs>()

    val events = SingleLiveEvent<UpgradeEvents>()

    init {
        if (!navArgs.forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { popNavStack() }
                .launchInViewModel()
        }
    }

    val state = combine(
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(OurSku.Iap.PRO_UPGRADE)
                } catch (e: Exception) {
                    errorEvents.postValue(e)
                    null
                }
            }
            emit(data)
        },
        flow {
            val data = withTimeoutOrNull(5000) {
                try {
                    upgradeRepo.querySkus(OurSku.Sub.PRO_UPGRADE)
                } catch (e: Exception) {
                    errorEvents.postValue(e)
                    null
                }
            }
            emit(data)
        },
        upgradeRepo.upgradeInfo,
    ) { iap, sub, current ->
        if (iap == null && sub == null) {
            throw GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out."))
        }
        Pricing(
            iap = iap?.first(),
            sub = sub?.first(),
            hasIap = current.upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
            hasSub = current.upgrades.any { it.sku == OurSku.Sub.PRO_UPGRADE },
        )
    }.asLiveData2()

    data class Pricing(
        val iap: SkuDetails?,
        val sub: SkuDetails?,
        val hasSub: Boolean,
        val hasIap: Boolean,
    )

    fun onGoIap(activity: Activity) {
        log(TAG) { "onGoIap($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.BASE_OFFER)
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER)
    }

    fun restorePurchase() = launch {
        log(TAG) { "restorePurchase()" }

        log(TAG, VERBOSE) { "Refreshing" }
        upgradeRepo.refresh()

        val refreshedState = upgradeRepo.upgradeInfo.first()
        log(TAG) { "Refreshed purchase state: $refreshedState" }

        if (refreshedState.isPro) {
            log(TAG, INFO) { "Restored purchase :))" }
        } else {
            log(TAG, WARN) { "Restore purchase failed" }
            events.postValue(UpgradeEvents.RestoreFailed)
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}