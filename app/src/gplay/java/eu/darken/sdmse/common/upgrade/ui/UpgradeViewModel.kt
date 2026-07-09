package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
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

    // Route is bound from the Host via bindRoute(); SavedStateHandle.toRoute<>() crashes under Nav3.
    private val routeFlow = MutableStateFlow<UpgradeRoute?>(null)
    private var hasShownRepoError: Boolean = false
    private var hasShownServiceUnavailableError: Boolean = false
    val events = SingleEventFlow<UpgradeEvents>()

    fun bindRoute(route: UpgradeRoute) {
        if (routeFlow.value != null) return
        routeFlow.value = route
    }

    init {
        routeFlow
            .filterNotNull()
            .take(1)
            .onEach { route ->
                if (!route.forced) {
                    upgradeRepo.upgradeInfo
                        .filter { it.isPro }
                        .take(1)
                        .onEach { navUp() }
                        .launchInViewModel()
                }
            }
            .launchInViewModel()
    }

    internal val state: StateFlow<GplayUpgradeUiState> = combine(
        querySkuDetails(OurSku.Iap.PRO_UPGRADE),
        querySkuDetails(OurSku.Sub.PRO_UPGRADE),
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
    ) { iap, sub, current, wasEverPro ->
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
            wasPreviouslyPro = wasEverPro && !current.isPro,
        )
    }.safeStateIn(
        initialValue = GplayUpgradeUiState.Loading,
        // Lazily (not WhileSubscribed): keep the billing SKU queries cached for the VM lifetime so
        // backgrounding >5s and returning doesn't drop the offer cards back to Loading and re-query.
        started = SharingStarted.Lazily,
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
        upgradeRepo.launchBillingFlow(activity, OurSku.Iap.PRO_UPGRADE, null, onError = errorEvents::tryEmit)
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        upgradeRepo.launchBillingFlow(
            activity,
            OurSku.Sub.PRO_UPGRADE,
            OurSku.Sub.PRO_UPGRADE.BASE_OFFER,
            onError = errorEvents::tryEmit,
        )
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG) { "onGoSubscriptionTrial($activity)" }
        upgradeRepo.launchBillingFlow(
            activity,
            OurSku.Sub.PRO_UPGRADE,
            OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER,
            onError = errorEvents::tryEmit,
        )
    }

    fun restorePurchase() = launch {
        log(TAG) { "restorePurchase()" }

        val restored = try {
            withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog instead
            // of the generic "restore failed" message, so the user can tell the two cases apart.
            log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.tryEmit(e)
            return@launch
        }

        when {
            restored == null -> {
                // Play never answered in time; the restore-failed dialog already suggests waiting /
                // clearing the Play cache, which fits a timeout too.
                log(TAG, WARN) { "Restore purchase timed out" }
                events.tryEmit(UpgradeEvents.RestoreFailed)
            }

            restored.isPro -> log(TAG, INFO) { "Restored purchase :))" }

            else -> {
                log(TAG, WARN) { "Restore purchase failed" }
                events.tryEmit(UpgradeEvents.RestoreFailed)
            }
        }
    }

    companion object {
        private const val RESTORE_TIMEOUT_MS = 15_000L
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
