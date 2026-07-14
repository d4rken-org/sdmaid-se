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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
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
    private var hasShownPartialQueryError: Boolean = false
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

    private val restoring = MutableStateFlow(false)
    private val retryTrigger = MutableStateFlow(0)

    // One aggregate query per retry generation: both SKU lookups run concurrently and land in a
    // single Done, so the UI can never combine results from two different retry attempts.
    private sealed interface SkuQueries {
        data object Pending : SkuQueries
        data class Done(
            val iap: Result<Collection<SkuDetails>>,
            val sub: Result<Collection<SkuDetails>>,
        ) : SkuQueries
    }

    private val skuQueries: Flow<SkuQueries> = retryTrigger.flatMapLatest {
        flow {
            emit(SkuQueries.Pending)
            val done = coroutineScope {
                val iap = async { querySkuDetails(OurSku.Iap.PRO_UPGRADE) }
                val sub = async { querySkuDetails(OurSku.Sub.PRO_UPGRADE) }
                SkuQueries.Done(iap = iap.await(), sub = sub.await())
            }
            emit(done)
        }
    }

    private suspend fun querySkuDetails(sku: Sku): Result<Collection<SkuDetails>> = try {
        val details = withTimeoutOrNull(SKU_QUERY_TIMEOUT_MS) { upgradeRepo.querySkus(sku) }
            ?: throw GplayServiceUnavailableException(RuntimeException("SKU query timed out for ${sku.id}"))
        Result.success(details)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(TAG, WARN) { "querySkuDetails($sku) failed: ${e.asLog()}" }
        Result.failure(e)
    }

    internal val state: StateFlow<GplayUpgradeUiState> = combine(
        skuQueries,
        upgradeRepo.upgradeInfo,
        upgradeRepo.wasEverPro,
        restoring,
        upgradeRepo.autoRestoreBusy,
    ) { queries, current, wasEverPro, isRestoring, isAutoRestoring ->
        if (queries is SkuQueries.Pending) {
            // A new attempt starts a new error episode.
            hasShownServiceUnavailableError = false
            hasShownPartialQueryError = false
            return@combine GplayUpgradeUiState.Loading
        }
        queries as SkuQueries.Done

        val iap = queries.iap.getOrNull()
        val sub = queries.sub.getOrNull()

        if (iap == null && sub == null) {
            val serviceUnavailableError = GplayServiceUnavailableException(
                queries.iap.exceptionOrNull() ?: RuntimeException("IAP and SUB data request failed.")
            )
            // This combine re-runs on every upstream change (e.g. restore progress toggling) —
            // emit the unavailable error once per failure episode, not once per recombination.
            if (!hasShownServiceUnavailableError) {
                hasShownServiceUnavailableError = true
                errorEvents.tryEmit(serviceUnavailableError)
            }
            return@combine GplayUpgradeUiState.Unavailable(serviceUnavailableError)
        }
        hasShownServiceUnavailableError = false

        // Exactly one product type failed: keep today's behavior — show what's available, surface
        // the failure once. No retry affordance; re-entering the screen (or the working offer)
        // covers this, only the full Unavailable state is a dead end.
        val partialError = queries.iap.exceptionOrNull() ?: queries.sub.exceptionOrNull()
        if (partialError != null) {
            if (!hasShownPartialQueryError) {
                hasShownPartialQueryError = true
                errorEvents.tryEmit(partialError)
            }
        } else {
            hasShownPartialQueryError = false
        }

        if (!current.isPro && current.error != null) {
            if (!hasShownRepoError) {
                hasShownRepoError = true
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                errorEvents.tryEmit(current.error!!)
            }
        } else {
            hasShownRepoError = false
        }

        // Diagnosability: distinguishes "Play withheld the trial offer" from "offer matching failed"
        // when users report a missing trial (see upgrade_screen_how_body_no_trial fallback).
        sub?.firstOrNull()?.details?.subscriptionOfferDetails?.let { offers ->
            log(TAG) { "Subscription offers from Play: ${offers.map { "${it.basePlanId}/${it.offerId}" }}" }
        }

        toLoadedState(
            iap = iap?.firstOrNull(),
            sub = sub?.firstOrNull(),
            hasIap = current.upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
            hasSub = current.upgrades.any { it.sku == OurSku.Sub.PRO_UPGRADE },
            wasPreviouslyPro = wasEverPro && !current.isPro,
            // Manual restore or the repo's invisible already-owned auto-restore: either pauses the
            // entitlement actions, so the two can't be raced against each other from the UI.
            restoreInProgress = isRestoring || isAutoRestoring,
        )
    }.safeStateIn(
        initialValue = GplayUpgradeUiState.Loading,
        // Lazily (not WhileSubscribed): keep the billing SKU queries cached for the VM lifetime so
        // backgrounding >5s and returning doesn't drop the offer cards back to Loading and re-query.
        started = SharingStarted.Lazily,
        onError = { error -> GplayUpgradeUiState.Unavailable(error) },
    )

    // Re-runs the SKU queries after a full "Play unavailable" episode — without this, the Lazily
    // cached failure bricked the screen for the whole ViewModel lifetime.
    fun retrySkuQuery() {
        log(TAG) { "retrySkuQuery()" }
        retryTrigger.update { it + 1 }
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
        // Single-flight: repeated taps while a restore is running (worst case bounded by
        // RESTORE_TIMEOUT_MS) must not stack concurrent restores and duplicate result dialogs.
        if (!restoring.compareAndSet(expect = false, update = true)) {
            log(TAG) { "restorePurchase() ignored, already in progress" }
            return@launch
        }
        log(TAG) { "restorePurchase()" }

        try {
            val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play/billing error (e.g. service unavailable): surface the proper error dialog instead
            // of the generic "restore failed" message, so the user can tell the two cases apart.
            log(TAG, WARN) { "Restore purchase errored: ${e.asLog()}" }
            errorEvents.tryEmit(e)
        } finally {
            // Reset only after result handling, so the single-flight guard covers the whole action.
            restoring.value = false
        }
    }

    companion object {
        private const val RESTORE_TIMEOUT_MS = 15_000L

        // The very first billing query after Play sign-in can take >8s (measured 8.5s) while Play
        // warms up — 5s produced false "Play unavailable" dialogs on slow-but-healthy stores.
        private const val SKU_QUERY_TIMEOUT_MS = 15_000L
        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
