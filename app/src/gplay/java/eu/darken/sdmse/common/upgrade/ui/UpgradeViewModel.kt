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
import eu.darken.sdmse.common.upgrade.core.billing.OfferUnavailableBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PendingPurchaseBillingException
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
) : ViewModel4(dispatcherProvider = dispatcherProvider, tag = TAG) {

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

    // ONE arbiter for every entitlement action (both purchase paths and restore). They all talk to
    // the same Play account state, so two independent guards let a subscription tap and a restore
    // tap run concurrent Play operations against each other.
    private val activeOp = MutableStateFlow<BusyOp?>(null)
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
        upgradeRepo.wasEverPro,
        upgradeRepo.proUnconfirmedSince,
        graceTick,
        activeOp,
        upgradeRepo.autoRestoreBusy,
        upgradeRepo.purchaseLaunchSku,
    ) { queries, current, wasEverPro, proUnconfirmedSince, _, vmOp, isAutoRestoring, launchSku ->
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
        // actions render immediately and price problems are not their problem. A user waiting on a
        // pending payment is in the same position — the card is their answer, and both offers are
        // locked anyway, so a price failure must not replace it with an error screen.
        val priceIndependent = ownership.ownsAnything || grace != null || current.pendingSkus.isNotEmpty()

        val done = queries as? SkuQueries.Done
        if (done == null) {
            // A new attempt starts a new error episode.
            hasShownServiceUnavailableError = false
            hasShownPartialQueryError = false
        }
        // Structural close: entitlement-dependent UI never renders from a pre-reconciliation
        // Info — even if fast SKU queries finish before the reconciled Info propagates, an
        // unsettled owner must not be flashed acquisition offers. Carve-out: a Done where BOTH
        // fresh SKU queries failed is itself a definitive can't-reach-Play outcome and may
        // resolve to Unavailable/grace without waiting for the connect loop's failure signal
        // (preserves the ~15s bound from the query timeouts during a total Play hang).
        val bothQueriesFailed = done != null && done.iap.isFailure && done.sub.isFailure
        if (!current.isSettled && !bothQueriesFailed) return@combine GplayUpgradeUiState.Loading
        // Acquisition renders with prices like it always has; owners and grace users render
        // their status immediately without waiting for prices.
        if (done == null && !priceIndependent) return@combine GplayUpgradeUiState.Loading

        val iap = done?.iap?.getOrNull()
        val sub = done?.sub?.getOrNull()

        if (done != null) {
            if (iap == null && sub == null) {
                val iapCause = done.iap.exceptionOrNull()
                val subCause = done.sub.exceptionOrNull()
                // Play answered fine and simply has nothing to sell here (region, account
                // eligibility, pulled product): reporting that as a connectivity failure sends the
                // user chasing futile advice (clear Play's cache, reboot). Only when BOTH causes
                // are merchandising — a single connectivity failure can't rule out a real Play
                // problem, so the conservative "can't reach Play" copy stays correct.
                val queryError = if (
                    iapCause is OfferUnavailableBillingException && subCause is OfferUnavailableBillingException
                ) {
                    iapCause
                } else {
                    GplayServiceUnavailableException(
                        iapCause ?: RuntimeException("IAP and SUB data request failed.")
                    )
                }
                // Grace users and owners are excluded: during an outage (exactly when grace
                // matters) they must keep the Loaded presentation with their status/grace card,
                // not an acquisition-style error state or dialog.
                if (!priceIndependent) {
                    // This combine re-runs on every upstream change (e.g. restore progress
                    // toggling) — emit once per failure episode, not once per recombination.
                    if (!hasShownServiceUnavailableError) {
                        hasShownServiceUnavailableError = true
                        errorEvents.tryEmit(queryError)
                    }
                    return@combine GplayUpgradeUiState.Unavailable(queryError)
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
            hasPendingPurchase = current.toPendingFlag(),
            // This ViewModel's own action wins; otherwise a launch started elsewhere (previous VM
            // instance, e.g. across a rotation — the launch lives on AppScope) or the repo's
            // invisible already-owned auto-restore still pauses the entitlement actions here.
            busy = vmOp
                ?: launchSku?.let { if (it is Sku.Subscription) BusyOp.SUBSCRIPTION else BusyOp.IAP }
                ?: BusyOp.RESTORE.takeIf { isAutoRestoring },
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

    // Returning to the screen is the user's own "try again": a transient Play outage would
    // otherwise leave the retry card up until it's tapped by hand. Only re-queries from the
    // unavailable state — a loaded or still-loading screen has nothing to retry.
    fun onResume() {
        log(TAG) { "onResume()" }
        if (state.value is GplayUpgradeUiState.Unavailable) retrySkuQuery()
    }

    // Acquires the single action slot. Rejects while ANY other entitlement action of this ViewModel
    // runs, and while the repo reports a Play launch in flight (which may belong to another VM
    // instance — the repo CAS remains the authoritative gate, this only avoids the pointless tap).
    private fun acquireOp(op: BusyOp): Boolean {
        upgradeRepo.purchaseLaunchSku.value?.let {
            log(TAG) { "$op ignored, a billing launch for $it is already in flight" }
            return false
        }
        if (!activeOp.compareAndSet(expect = null, update = op)) {
            log(TAG) { "$op ignored, ${activeOp.value} is already in progress" }
            return false
        }
        return true
    }

    // Outcome of the pre-purchase check with Play. Both purchase paths share it: they buy
    // alternatives of the same entitlement from the same account, so a check only one of them runs
    // is exactly how a double charge slips through. [Blocked] means the user was already told why.
    private sealed interface PurchaseGate {
        data class Clear(val info: UpgradeRepoGplay.Info) : PurchaseGate
        data object Blocked : PurchaseGate
    }

    // Fails closed: no fresh, complete answer from Play (error, timeout) means no purchase. Bounded,
    // because the repo waits for a healthy connection indefinitely and a tap must not park the
    // single-flight guard through an outage.
    private suspend fun runPurchaseGate(): PurchaseGate {
        val info = try {
            withTimeoutOrNull(VERIFY_TIMEOUT_MS) { upgradeRepo.verifyPurchaseStateNow() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Purchase verification errored: ${e.asLog()}" }
            errorEvents.tryEmit(e)
            return PurchaseGate.Blocked
        }
        if (info == null) {
            log(TAG, WARN) { "Purchase verification timed out" }
            events.tryEmit(UpgradeEvents.PurchaseCheckFailed)
            return PurchaseGate.Blocked
        }
        if (info.pendingSkus.isNotEmpty()) {
            // Play rejects a purchase while it is still processing a payment for this account, and
            // the alternative product would charge twice for the same features.
            log(TAG, INFO) { "Purchase blocked: a payment is still pending" }
            events.tryEmit(UpgradeEvents.PurchasePending)
            return PurchaseGate.Blocked
        }
        return PurchaseGate.Clear(info)
    }

    // A launch that failed on a pending payment is not an error the user can act on: it gets the
    // informational dialog instead of the already-owned copy and its restore tips.
    private fun onLaunchError(error: Throwable) {
        if (error is PendingPurchaseBillingException) {
            events.tryEmit(UpgradeEvents.PurchasePending)
        } else {
            errorEvents.tryEmit(error)
        }
    }

    fun onGoIap(activity: Activity) {
        log(TAG) { "onGoIap($activity)" }
        launch {
            // Single-flight: repeated taps must not stack verifications or billing launches.
            if (!acquireOp(BusyOp.IAP)) return@launch
            try {
                val gate = runPurchaseGate()
                if (gate !is PurchaseGate.Clear) return@launch
                // Hard gate against double-billing, against the FRESH result — the replayed
                // upgradeInfo can be stale or built from partial results. Asked of the RAW
                // purchases (see Info.hasAutoRenewingSubscription), never of the mapped upgrades:
                // a renewing subscription with an unknown or legacy product ID must block the
                // one-time purchase too, or the user pays for Pro twice.
                if (gate.info.hasAutoRenewingSubscription) {
                    log(TAG, INFO) { "IAP purchase blocked: subscription is still set to renew" }
                    events.tryEmit(UpgradeEvents.SubscriptionStillRenewing)
                    return@launch
                }
                // Suspends until the Play sheet launch resolved, so the single-flight guard covers
                // the whole tap-to-sheet window, not just the verification.
                upgradeRepo.launchBillingFlowNow(
                    activity,
                    OurSku.Iap.PRO_UPGRADE,
                    null,
                    onError = ::onLaunchError,
                )
            } finally {
                activeOp.value = null
            }
        }
    }

    fun onGoSubscription(activity: Activity) {
        log(TAG) { "onGoSubscription($activity)" }
        startSubPurchase(activity, OurSku.Sub.PRO_UPGRADE.BASE_OFFER)
    }

    fun onGoSubscriptionTrial(activity: Activity) {
        log(TAG) { "onGoSubscriptionTrial($activity)" }
        startSubPurchase(activity, OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER)
    }

    private fun startSubPurchase(activity: Activity, offer: Sku.Subscription.Offer) {
        launch {
            if (!acquireOp(BusyOp.SUBSCRIPTION)) return@launch
            try {
                // Same fresh check as the one-time path: a pending payment (for either product)
                // must block this launch too — Play would reject it, or bill it on top.
                val gate = runPurchaseGate()
                if (gate !is PurchaseGate.Clear) return@launch
                // The reactive UI can be stale (the purchase was made on another device), and Play
                // happily sells the subscription alongside an owned one-time purchase — the user
                // would pay for Pro twice. The strict refresh already committed into the shared
                // billing data, so the screen re-renders to the ownership state, and the
                // restore-success toast explains why nothing launched. Deliberately the MAPPED
                // upgrades, not isPro: grace users (mapped upgrades empty) may legitimately
                // re-purchase.
                if (gate.info.upgrades.isNotEmpty()) {
                    log(TAG, INFO) { "Subscription purchase blocked: fresh check found an owned upgrade" }
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                    return@launch
                }
                // Same breadth as the one-time path's renewal guard: an auto-renewing subscription
                // with an unknown or legacy product ID (which maps to zero upgrades, so the block
                // above passed) must still block a new subscription — two renewing subscriptions
                // for the same features is the same double-billing. Reuses the existing
                // manage-subscription dialog.
                if (gate.info.hasAutoRenewingSubscription) {
                    log(TAG, INFO) { "Subscription purchase blocked: another subscription is still set to renew" }
                    events.tryEmit(UpgradeEvents.SubscriptionStillRenewing)
                    return@launch
                }
                // launchBillingFlowNow suspends until the launch resolved, so the guard covers the
                // whole tap-to-sheet window. The flow itself still runs on AppScope, so closing the
                // screen mid-launch doesn't abort the purchase.
                upgradeRepo.launchBillingFlowNow(
                    activity,
                    OurSku.Sub.PRO_UPGRADE,
                    offer,
                    onError = ::onLaunchError,
                )
            } finally {
                activeOp.value = null
            }
        }
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
        // RESTORE_TIMEOUT_MS) must not stack concurrent restores and duplicate result dialogs —
        // and a restore must not run alongside a purchase either.
        if (!acquireOp(BusyOp.RESTORE)) return@launch
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
                    // Budget covers connecting, the refresh mutex AND both queries, so a query may
                    // well have started. All we know is the check didn't finish -- not that Play
                    // said no. Reporting this as a completed check would send an owner chasing the
                    // multi-account explanation for what is really a slow or unreachable Play.
                    log(TAG, WARN) { "Restore purchase timed out" }
                    events.tryEmit(UpgradeEvents.RestoreInconclusive)
                }

                restored is UpgradeRepoGplay.RestoreOutcome.Inconclusive -> {
                    // Play errored and grace kept Pro alive. Same non-answer as a timeout, and the
                    // user is by definition a recent owner -- the last person to tell that we
                    // checked and found nothing.
                    log(TAG, WARN) { "Restore purchase inconclusive: ${restored.cause.asLog()}" }
                    events.tryEmit(UpgradeEvents.RestoreInconclusive)
                }

                restored.info.upgrades.isNotEmpty() -> {
                    log(TAG, INFO) { "Restored purchase :))" }
                    // Explicit feedback: on the ownership screen a successful restore changes
                    // nothing visible (the user already is Pro), so silence reads as "broken".
                    events.tryEmit(UpgradeEvents.RestoreSucceeded)
                }

                restored.info.pendingSkus.isNotEmpty() -> {
                    // Play answered and found the purchase — it just hasn't been paid for yet.
                    // RestoreFailed would send this user chasing account and support advice for
                    // something that resolves itself.
                    log(TAG, INFO) { "Restore found a purchase with a pending payment" }
                    events.tryEmit(UpgradeEvents.PurchasePending)
                }

                else -> {
                    // Play answered and had nothing. Includes a grace-only result from a successful
                    // EMPTY query: Pro may still be active, but the check really did complete, so
                    // troubleshooting and escalation are warranted.
                    log(TAG, WARN) { "Restore purchase found no purchases (isPro=${restored.info.isPro})" }
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
            activeOp.value = null
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
