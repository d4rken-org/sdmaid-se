package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.SingleEventFlow
import eu.darken.sdmse.common.flow.combine
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
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider = dispatcherProvider) {

    // Route is bound from the Host via bindRoute(); SavedStateHandle.toRoute<>() crashes under Nav3.
    private val routeFlow = MutableStateFlow<UpgradeRoute?>(null)
    private var hasShownRepoError: Boolean = false
    private var hasShownServiceUnavailableError: Boolean = false
    private var hasShownDetailsError: Boolean = false
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
                // The manage route is the ownership screen — Pro users are its audience, they must
                // not be bounced out. Forced routes keep their existing don't-auto-close semantics.
                if (!route.forced && !route.manage) {
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
    private val verifying = MutableStateFlow(false)

    internal val state: StateFlow<GplayUpgradeUiState> = combine(
        querySkuDetails(OurSku.Iap.PRO_UPGRADE),
        querySkuDetails(OurSku.Sub.PRO_UPGRADE),
        upgradeRepo.upgradeInfo,
        upgradeRepo.isSettled,
        upgradeRepo.wasEverPro,
        restoring,
        verifying,
    ) { iap, sub, current, settled, wasEverPro, isRestoring, isVerifying ->
        val ownership = current.toOwnership()

        val bothFailed = iap is DetailsState.Failed && sub is DetailsState.Failed
        val anyPending = iap is DetailsState.Pending || sub is DetailsState.Pending
        val serviceUnavailableError = if (bothFailed && !ownership.ownsAnything) {
            GplayServiceUnavailableException(RuntimeException("IAP and SUB data request timed out."))
        } else {
            null
        }

        if (serviceUnavailableError != null) {
            if (!hasShownServiceUnavailableError) {
                hasShownServiceUnavailableError = true
                errorEvents.tryEmit(serviceUnavailableError)
            }
        } else if (!bothFailed) {
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

        // Detail-query errors only matter where prices are rendered: owners get their status and
        // management actions without prices, so don't bother them with a Play error dialog.
        val detailsError = (iap as? DetailsState.Failed)?.error ?: (sub as? DetailsState.Failed)?.error
        if (serviceUnavailableError == null && detailsError != null && !ownership.ownsAnything) {
            if (!hasShownDetailsError) {
                hasShownDetailsError = true
                errorEvents.tryEmit(detailsError)
            }
        }

        // Diagnosability: distinguishes "Play withheld the trial offer" from "offer matching failed"
        // when users report a missing trial (see upgrade_screen_how_body_no_trial fallback).
        (sub as? DetailsState.Loaded)?.details?.firstOrNull()?.details?.subscriptionOfferDetails?.let { offers ->
            log(TAG) { "Subscription offers from Play: ${offers.map { "${it.basePlanId}/${it.offerId}" }}" }
        }

        when {
            // Bounded settling window: while detail queries are still in flight (≤5s), wait for
            // billing to settle (an unsettled owner must not be flashed acquisition offers) and,
            // for non-owners, for prices (acquisition renders with prices like it always has).
            // Once the detail queries resolve, rendering proceeds regardless — a starved billing
            // layer degrades to the pre-existing acquisition presentation, never an endless spinner.
            anyPending && (!settled || !ownership.ownsAnything) -> GplayUpgradeUiState.Loading

            serviceUnavailableError != null -> GplayUpgradeUiState.Unavailable(serviceUnavailableError)

            else -> toLoadedState(
                iap = (iap as? DetailsState.Loaded)?.details?.firstOrNull(),
                sub = (sub as? DetailsState.Loaded)?.details?.firstOrNull(),
                ownership = ownership,
                wasPreviouslyPro = wasEverPro && !current.isPro,
                restoreInProgress = isRestoring,
                verificationInProgress = isVerifying,
            )
        }
    }.safeStateIn(
        initialValue = GplayUpgradeUiState.Loading,
        // Lazily (not WhileSubscribed): keep the billing SKU queries cached for the VM lifetime so
        // backgrounding >5s and returning doesn't drop the offer cards back to Loading and re-query.
        started = SharingStarted.Lazily,
        onError = { error -> GplayUpgradeUiState.Unavailable(error) },
    )

    private sealed interface DetailsState {
        data object Pending : DetailsState
        data class Failed(val error: Throwable? = null) : DetailsState
        data class Loaded(val details: Collection<SkuDetails>) : DetailsState
    }

    private fun querySkuDetails(sku: Sku): Flow<DetailsState> = flow {
        // Pending first, so ownership state can render without waiting out the detail queries.
        emit(DetailsState.Pending)
        val result = try {
            // An empty (test-fixture) success still counts as answered — only a timeout or an
            // exception is a failure, mirroring the pre-existing "loaded, no offers" semantics.
            withTimeoutOrNull(5000) { upgradeRepo.querySkus(sku) }
                ?.let { DetailsState.Loaded(it) }
                ?: DetailsState.Failed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not emitted to errorEvents here: whether the user needs to see it depends on
            // ownership, which only the combined state knows.
            DetailsState.Failed(e)
        }
        emit(result)
    }

    fun onGoIap(activity: Activity) {
        log(TAG) { "onGoIap($activity)" }
        launch {
            // Single-flight: repeated taps must not stack verifications or billing launches.
            if (!verifying.compareAndSet(expect = false, update = true)) {
                log(TAG) { "onGoIap() ignored, verification already in progress" }
                return@launch
            }
            try {
                // Hard gate against double-billing: verify against a FRESH SUBS-only query — the
                // replayed upgradeInfo can be stale or built from partial results. Fails closed:
                // no verified "not set to renew" (or no sub at all), no one-time purchase.
                val subscriptions = try {
                    withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.queryCurrentSubscriptions() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Subscription verification errored: ${e.asLog()}" }
                    errorEvents.tryEmit(e)
                    return@launch
                }
                when {
                    subscriptions == null -> {
                        log(TAG, WARN) { "Subscription verification timed out" }
                        events.tryEmit(UpgradeEvents.SubscriptionCheckFailed)
                    }

                    subscriptions.any { it.isAutoRenewing } -> {
                        log(TAG, INFO) { "IAP purchase blocked: subscription is still set to renew" }
                        events.tryEmit(UpgradeEvents.SubscriptionStillRenewing)
                    }

                    // Suspends until the Play sheet launch resolved, so the single-flight guard
                    // covers the whole tap-to-sheet window, not just the verification.
                    else -> upgradeRepo.launchBillingFlowNow(
                        activity,
                        OurSku.Iap.PRO_UPGRADE,
                        null,
                        onError = errorEvents::tryEmit,
                    )
                }
            } finally {
                verifying.value = false
            }
        }
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

    fun onManageSubscription() {
        log(TAG) { "onManageSubscription()" }
        webpageTool.open(PLAY_SUBSCRIPTION_SITE)
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

                restored.isPro -> {
                    log(TAG, INFO) { "Restored purchase :))" }
                    // Explicit feedback: on the ownership screen a successful restore changes
                    // nothing visible (the user already is Pro), so silence reads as "broken".
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                }

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
        private const val VERIFY_TIMEOUT_MS = 10_000L

        // Play's management page for our subscription specifically; harmless without a matching
        // sub on the account (Play falls back to the general subscription list).
        internal val PLAY_SUBSCRIPTION_SITE =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${OurSku.Sub.PRO_UPGRADE.id}&package=${BuildConfigWrap.APPLICATION_ID}"

        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
