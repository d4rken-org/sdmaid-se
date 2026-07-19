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
import eu.darken.sdmse.main.ui.navigation.SupportFormRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
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
    private val retryTrigger = MutableStateFlow(0)

    // Test seam: the diagnostics threshold compares wall-clock time, which coroutine test
    // dispatchers can't advance.
    internal var clock: () -> Long = { System.currentTimeMillis() }

    // Re-evaluates the diagnostics threshold when the episode crosses it: all other combined
    // flows are distinct-until-changed and can stay silent across the 24h boundary, which would
    // otherwise leave a long-lived ViewModel stuck on the quiet stage.
    private val graceTick: Flow<Unit> = upgradeRepo.proUnconfirmedSince
        .flatMapLatest { stamp ->
            flow {
                emit(Unit)
                if (stamp > 0L) {
                    val remaining = stamp + GRACE_DIAGNOSTICS_AFTER_MS - clock()
                    if (remaining > 0) {
                        delay(remaining)
                        emit(Unit)
                    }
                }
            }
        }

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
        upgradeRepo.isSettled,
        upgradeRepo.wasEverPro,
        upgradeRepo.proUnconfirmedSince,
        graceTick,
        restoring,
        upgradeRepo.autoRestoreBusy,
        verifying,
    ) { queries, current, settled, wasEverPro, proUnconfirmedSince, _, isRestoring, isAutoRestoring, isVerifying ->
        val ownership = current.toOwnership()
        // Pro without any owned purchase == grace. Stage 1 (quiet "still active" line) shows
        // immediately; the diagnostics + restore CTA only once the unconfirmed episode has aged
        // past the threshold, so self-healing Play blips never surface them.
        val grace = if (current.isPro && !ownership.ownsAnything) {
            GraceHint(
                showDiagnostics = proUnconfirmedSince > 0L &&
                    clock() - proUnconfirmedSince >= GRACE_DIAGNOSTICS_AFTER_MS,
            )
        } else {
            null
        }
        // Owners and grace users don't depend on offer prices: their status and management
        // actions render immediately and price problems are not their problem.
        val priceIndependent = ownership.ownsAnything || grace != null

        val done = queries as? SkuQueries.Done
        if (done == null) {
            // A new attempt starts a new error episode.
            hasShownServiceUnavailableError = false
            hasShownPartialQueryError = false
            // Acquisition renders with prices like it always has; billing must have settled first
            // either way (an unsettled owner must not be flashed acquisition offers).
            if (!settled || !priceIndependent) return@combine GplayUpgradeUiState.Loading
        }

        val iap = done?.iap?.getOrNull()
        val sub = done?.sub?.getOrNull()

        if (done != null) {
            if (iap == null && sub == null) {
                val serviceUnavailableError = GplayServiceUnavailableException(
                    done.iap.exceptionOrNull() ?: RuntimeException("IAP and SUB data request failed.")
                )
                // Grace users and owners are excluded: during an outage (exactly when grace
                // matters) they must keep the Loaded presentation with their status/grace card,
                // not an acquisition-style error state or dialog.
                if (!priceIndependent) {
                    // This combine re-runs on every upstream change (e.g. restore progress
                    // toggling) — emit once per failure episode, not once per recombination.
                    if (!hasShownServiceUnavailableError) {
                        hasShownServiceUnavailableError = true
                        errorEvents.tryEmit(serviceUnavailableError)
                    }
                    return@combine GplayUpgradeUiState.Unavailable(serviceUnavailableError)
                }
            } else {
                hasShownServiceUnavailableError = false

                // Exactly one product type failed: keep today's behavior — show what's available,
                // surface the failure once. Not for owners/grace: price errors aren't their problem.
                val partialError = done.iap.exceptionOrNull() ?: done.sub.exceptionOrNull()
                if (partialError != null && !priceIndependent) {
                    if (!hasShownPartialQueryError) {
                        hasShownPartialQueryError = true
                        errorEvents.tryEmit(partialError)
                    }
                } else if (partialError == null) {
                    // Only a SUCCESS resets the flag. A priceIndependent user with a failed query
                    // must leave it untouched: it may already be true from before they became an
                    // owner, and resetting would re-emit the same episode if ownership lapses
                    // again. A new query attempt (Pending above) resets it either way.
                    hasShownPartialQueryError = false
                }
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

            // Diagnosability: distinguishes "Play withheld the trial offer" from "offer matching
            // failed" when users report a missing trial (see the no-trial offer body fallback).
            sub?.firstOrNull()?.details?.subscriptionOfferDetails?.let { offers ->
                log(TAG) { "Subscription offers from Play: ${offers.map { "${it.basePlanId}/${it.offerId}" }}" }
            }
        }

        toLoadedState(
            iap = iap?.firstOrNull(),
            sub = sub?.firstOrNull(),
            ownership = ownership,
            grace = grace,
            wasPreviouslyPro = wasEverPro && !current.isPro,
            // Manual restore or the repo's invisible already-owned auto-restore: either pauses the
            // entitlement actions, so the two can't be raced against each other from the UI.
            restoreInProgress = isRestoring || isAutoRestoring,
            verificationInProgress = isVerifying,
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

    fun onContactSupport() {
        log(TAG) { "onContactSupport()" }
        // The guided support form, not a bare mailto: it attaches version and Pro context, which
        // is exactly what purchase troubleshooting needs.
        navTo(SupportFormRoute)
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
            // Minimum visible duration, not a fixed add-on: the pad runs CONCURRENTLY with the
            // real Play query, so a fast check gets stretched to a believable length while a slow
            // one gains nothing. A sub-second round-trip reads as "nothing was checked" and
            // undermines the result — the check is real, this only makes its duration perceptible.
            // Manual restores only; the repo's invisible auto-restore must stay fast.
            val restored = coroutineScope {
                val minVisible = async { delay(RESTORE_MIN_VISIBLE_MS) }
                val result = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { upgradeRepo.restorePurchaseNow() }
                minVisible.await()
                result
            }
            when {
                restored == null -> {
                    // Play never answered in time; the restore-failed dialog already suggests waiting /
                    // clearing the Play cache, which fits a timeout too.
                    log(TAG, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvents.RestoreFailed)
                }

                restored.upgrades.isNotEmpty() -> {
                    log(TAG, INFO) { "Restored purchase :))" }
                    // Explicit feedback: on the ownership screen a successful restore changes
                    // nothing visible (the user already is Pro), so silence reads as "broken".
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                }

                else -> {
                    // Includes grace-only results: Pro may still be active, but the restore found
                    // no actual purchase — troubleshooting dialog, not a success toast.
                    log(TAG, WARN) { "Restore purchase found no purchases (isPro=${restored.isPro})" }
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
        // Floor for how long a manual restore visibly runs (spinner up, result held back). Long
        // enough that the user believes a round-trip to Play happened, short enough not to drag.
        internal const val RESTORE_MIN_VISIBLE_MS = 1_500L
        private const val VERIFY_TIMEOUT_MS = 10_000L

        // The very first billing query after Play sign-in can take >8s (measured 8.5s) while Play
        // warms up — 5s produced false "Play unavailable" dialogs on slow-but-healthy stores.
        private const val SKU_QUERY_TIMEOUT_MS = 15_000L

        // How long a fresh-data-confirmed grace episode must last before the grace card shows its
        // diagnostics: long enough that self-healing Play blips stay invisible, short enough to
        // leave most of the 7-day subscription grace for the user to act in.
        internal val GRACE_DIAGNOSTICS_AFTER_MS = Duration.ofHours(24).toMillis()

        // Play's management page for our subscription specifically; harmless without a matching
        // sub on the account (Play falls back to the general subscription list).
        internal val PLAY_SUBSCRIPTION_SITE =
            "https://play.google.com/store/account/subscriptions" +
                "?sku=${OurSku.Sub.PRO_UPGRADE.id}&package=${BuildConfigWrap.APPLICATION_ID}"

        private val TAG = logTag("Upgrade", "Gplay", "ViewModel")
    }
}
