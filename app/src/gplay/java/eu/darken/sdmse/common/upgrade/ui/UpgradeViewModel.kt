package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.uix.ViewModel4
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    private val route = UpgradeRoute.from(handle)
    private var hasShownRepoError: Boolean = false
    private var hasShownServiceUnavailableError: Boolean = false
    val events = SingleEventFlow<UpgradeEvents>()

    init {
        if (!route.forced) {
            upgradeRepo.upgradeInfo
                .filter { it.isPro }
                .take(1)
                .onEach { navUp() }
                .launchInViewModel()
        }
    }

    internal val state: StateFlow<GplayUpgradeUiState> = combine(
        querySkuDetails(OurSku.Iap.PRO_UPGRADE),
        querySkuDetails(OurSku.Sub.PRO_UPGRADE),
        upgradeRepo.upgradeInfo,
    ) { iap, sub, current ->
        val serviceUnavailableError = if (iap == null && sub == null) {
            GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out."))
        } else {
            null
        }

        if (serviceUnavailableError != null) {
            if (!hasShownServiceUnavailableError) {
                hasShownServiceUnavailableError = true
                errorEvents.tryEmit(serviceUnavailableError)
            }
        } else {
            hasShownServiceUnavailableError = false
        }

        if (serviceUnavailableError == null && !current.isPro && current.error != null) {
            if (!hasShownRepoError) {
                hasShownRepoError = true
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.tryEmit(current.error!!)
            }
        } else {
            hasShownRepoError = false
        }

        if (serviceUnavailableError != null) {
            return@combine GplayUpgradeUiState.Unavailable(serviceUnavailableError)
        }

        toLoadedState(
            iap = iap?.firstOrNull(),
            sub = sub?.firstOrNull(),
            hasIap = current.upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
            hasSub = current.upgrades.any { it.sku == OurSku.Sub.PRO_UPGRADE },
        )
    }.safeStateIn(
        initialValue = GplayUpgradeUiState.Loading,
        onError = { error -> GplayUpgradeUiState.Unavailable(error) },
    )

    private fun querySkuDetails(sku: Sku): Flow<Collection<SkuDetails>?> = flow {
        val data = withTimeoutOrNull(5000) {
            try {
                upgradeRepo.querySkus(sku)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorEvents.tryEmit(e)
                null
            }
        }
        emit(data)
    }

    fun onGoIap(activity: Activity) {
        log(TAG) { "onGoIap($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(activity, OurSku.Sub.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE.BASE_OFFER)
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG) { "onGoSubscriptionTrial($activity)" }
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
            events.tryEmit(UpgradeEvents.RestoreFailed)
        }
    }

    companion object {
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
