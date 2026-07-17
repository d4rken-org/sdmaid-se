package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import eu.darken.sdmse.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import eu.darken.sdmse.common.upgrade.core.billing.UserCanceledBillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import eu.darken.sdmse.main.core.CurriculumVitae
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
    private val curriculumVitae: CurriculumVitae,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    // Coalescing single-flight for the invisible already-owned recoveries: overlapping triggers
    // (async Play event racing a buy tap's launch result) join the SAME restore instead of
    // stacking concurrent Play queries. Busy state is exact because at most one job runs.
    private val autoRestoreLock = Mutex()
    private var autoRestoreJob: Deferred<Info?>? = null
    private val autoRestoreState = MutableStateFlow(false)

    // The already-owned auto-restores run invisibly on AppScope; expose their busy state so the
    // UI can pause entitlement actions instead of racing them with a manual restore or a buy.
    val autoRestoreBusy: Flow<Boolean> = autoRestoreState

    // Serializes the pro-state recorders: the fresh-data collector and the failure paths in
    // refresh()/restorePurchaseNow() can run concurrently, and a stale unconfirmed-stamp read must
    // not undo a newer confirmation's episode clear. Declared before the init block — its
    // collector can run during construction.
    private val proStateLock = Mutex()

    init {
        // Grace bookkeeping is driven by *fresh* Play data only: freshBillingData emissions each
        // represent an actual Play round-trip (per-connection/manual query results, completed
        // purchase events) — never the replayed billingData/upgradeInfo flows, whose old data
        // must not keep re-stamping the grace window (e.g. after a refund).
        billingManager.freshBillingData
            .onEach { fresh ->
                try {
                    recordProState(fresh)
                    trackProState(fresh)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A failed DataStore write must not kill this process-lifetime collector.
                    log(TAG, WARN) { "Failed to record pro state: ${e.asLog()}" }
                }
            }
            .setupCommonEventHandlers(TAG) { "proStateRecorder" }
            .launchIn(scope)

        // Async variant of the launch-result ITEM_ALREADY_OWNED case: Play told us mid-flow that
        // the user already owns it. Reconcile silently — Play shows its own UI for purchase-sheet
        // failures, so no app-side dialog here.
        billingManager.purchaseFailures
            .filter { it.responseCode == BillingResponseCode.ITEM_ALREADY_OWNED }
            .onEach {
                log(TAG, INFO) { "Async already-owned event -> restoring purchase" }
                autoRestore()
            }
            .setupCommonEventHandlers(TAG) { "asyncAlreadyOwned" }
            .launchIn(scope)
    }

    // False until the first real billing outcome after process start — the window where
    // upgradeInfo below reports non-Pro even for paying users (its flow is seeded with null).
    // A failed connection attempt counts as settled: gating callers must not wait on a broken
    // connection (the connect loop keeps retrying, but that can take arbitrarily long).
    override val isSettled: Flow<Boolean> = billingManager.isSettled
        .distinctUntilChanged()

    override val upgradeInfo: Flow<Info> = billingManager.billingData
        .map<BillingData, BillingData?> { it }
        .onStart { emit(null) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { data: BillingData? -> data.toUpgradeInfo() }
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            // Billing connection errors can no longer reach this flow (the connect loop retries
            // them internally) — what CAN fail here are the LOCAL DataStore reads in the Pro
            // mapping, plausible exactly when storage is full. Keep the flow alive and keep a
            // recently-Pro user in their grace window.
            log(TAG, WARN) { "upgradeInfo mapping failed (attempt=$attempt): ${error.asLog()}" }
            val fallback = try {
                if ((System.currentTimeMillis() - billingCache.lastProStateAt.value()) < graceWindowMs()) {
                    Info(gracePeriod = true, billingData = null)
                } else {
                    Info(billingData = null, error = error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The grace probe reads the same storage that just failed — a second failure must
                // not kill the retry loop that exists for exactly this situation.
                Info(billingData = null, error = error)
            }
            emit(fallback)
            delay(retryDelayMs(attempt))
            true
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a (known) Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .distinctUntilChanged()

    // Epoch millis of the first fresh reconciliation that couldn't confirm Pro in the current grace
    // episode (0 = none). The upgrade screen delays its grace diagnostics until this has aged, so
    // self-healing Play blips never surface it.
    val proUnconfirmedSince: Flow<Long> = billingCache.proUnconfirmedSince.flow
        .distinctUntilChanged()

    fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer?,
        onError: (Throwable) -> Unit,
    ) {
        scope.launch { launchBillingFlowInternal(activity, sku, offer, onError) }
    }

    // Suspends until the Play launch resolved (sheet up, or failed) — callers holding an
    // in-progress guard (e.g. the IAP verification single-flight) stay guarded through the
    // launch. Still runs ON AppScope: the purchase flow and the already-owned recovery must
    // survive the upgrade screen being closed, so caller cancellation only abandons the await.
    suspend fun launchBillingFlowNow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer?,
        onError: (Throwable) -> Unit,
    ) {
        scope.async { launchBillingFlowInternal(activity, sku, offer, onError) }.await()
    }

    private suspend fun launchBillingFlowInternal(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer?,
        onError: (Throwable) -> Unit,
    ) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        try {
            billingManager.startIapFlow(activity, sku, offer)
        } catch (e: CancellationException) {
            // Not an error: must not reach onError (spurious dialog) — rethrow for structured
            // cancellation.
            throw e
        } catch (e: Exception) {
            when {
                e is UserCanceledBillingException -> log(TAG) { "User canceled billing flow" }

                e is ItemAlreadyOwnedBillingException -> {
                    // Stale local state: Play says they already own it, so tapping "buy" really
                    // means "unlock what I own" — restore instead of showing an error.
                    log(TAG, INFO) { "Launch says already owned -> restoring purchase" }
                    val restored = autoRestore()
                    // Reconciled only if the restore actually returned the SKU Play claims is
                    // owned — a grace-only isPro doesn't count, the entitlement is still missing.
                    if (restored?.upgrades?.any { it.sku == sku } != true) {
                        // Couldn't reconcile the entitlement (pending purchase, account mismatch,
                        // Play quirk) — fall back to the already-owned dialog with restore tips.
                        onError(e)
                    }
                }

                else -> {
                    log(TAG) { "startIapFlow failed:${e.asLog()}" }
                    onError(e)
                }
            }
        }
    }

    // Bounded, silent restore for the already-owned recovery paths. Coalescing: a trigger that
    // arrives while one is running awaits the running one. Returns null when the restore failed
    // or timed out — never throws (except cancellation of the AWAITING caller; the job itself
    // finishes on AppScope either way).
    private suspend fun autoRestore(): Info? {
        val job = autoRestoreLock.withLock {
            autoRestoreJob?.takeIf { it.isActive } ?: scope.async {
                autoRestoreState.value = true
                try {
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Already-owned restore failed: ${e.asLog()}" }
                    null
                } finally {
                    autoRestoreState.value = false
                }
            }.also { autoRestoreJob = it }
        }
        return job.await()
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = billingManager.querySkus(*skus)

    // Strict subscription lookup for the pre-purchase gate: fresh SUBS-only query with explicit
    // failure. No grace substitution and no cross-product-type tolerance (unlike refresh() and
    // restorePurchaseNow()) — callers must treat any error as "couldn't verify" and fail closed.
    suspend fun queryCurrentSubscriptions(): Collection<Purchase> {
        log(TAG) { "queryCurrentSubscriptions()" }
        return billingManager.querySubscriptions()
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            // Bounded: with unbounded connection retry, an unavailable Play would otherwise keep
            // background callers (MainViewModel, isProSettled gates) suspended indefinitely.
            // Grace stamping happens via the freshBillingData collector, not here.
            val fresh = withTimeoutOrNull(REFRESH_TIMEOUT_MS) { billingManager.refresh() }
            if (fresh == null) {
                // A hanging connection is also a fresh attempt that couldn't confirm Pro.
                recordProUnconfirmed()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background refresh: keep the old swallow-and-log behaviour so callers like MainViewModel
            // aren't affected. The explicit restore path uses restorePurchaseNow(), which surfaces errors.
            log(TAG, ERROR) { "Background refresh failed: ${e.asLog()}" }
            // A fresh attempt that FAILED also can't confirm Pro — without this, a sustained Play
            // outage (queries erroring, never empty-succeeding) would never start the episode clock.
            recordProUnconfirmed()
        }
    }

    // Explicit "Restore purchase": query Play now and evaluate Pro from the returned data in the same
    // coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing errors
    // propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): Info {
        log(TAG) { "restorePurchaseNow()" }
        return try {
            billingManager.refresh().toUpgradeInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mirror the reactive flow's retryWhen: a transient Play error while we were Pro recently
            // keeps us Pro via the grace period; otherwise surface the error so the caller can show
            // the proper "Play unavailable" message instead of a generic restore failure.
            val lastProStateAt = billingCache.lastProStateAt.value()
            if ((System.currentTimeMillis() - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "restore hit a Play error but we were Pro recently -> grace" }
                recordProUnconfirmed()
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Reports the Pro-state transition history to CurriculumVitae (grace engaged, Pro lost) —
    // lifetime counters that end up in every debug log recording. Fed from FRESH data only:
    // upgradeInfo's null-seeded replay would fake a PURCHASED->GRACE->PURCHASED round trip on
    // every app launch. A partial snapshot without a known upgrade proves nothing about absence,
    // so it records nothing — grace engagements during a TOTAL Play outage are only counted once
    // Play answers again (accepted trade-off: no false positives).
    private suspend fun trackProState(fresh: BillingManager.FreshData) {
        val info = Info(billingData = fresh.data)
        val state = when {
            info.upgrades.isNotEmpty() -> CurriculumVitae.ProState.PURCHASED
            !fresh.isFullSnapshot -> return
            (System.currentTimeMillis() - billingCache.lastProStateAt.value()) < graceWindowMs() ->
                CurriculumVitae.ProState.GRACE

            else -> CurriculumVitae.ProState.FREE
        }
        curriculumVitae.updateProState(state)
    }

    // Persists "we saw a known Pro purchase" for the grace machinery, or feeds the unconfirmed-
    // episode clock when fresh data can't confirm Pro. Only ever fed by the freshBillingData
    // collector — fresh Play round-trips, never replayed flow data, so a refunded purchase can't
    // keep re-stamping its grace window.
    private suspend fun recordProState(fresh: BillingManager.FreshData) = proStateLock.withLock {
        val sku = preferredProSku(Info(billingData = fresh.data).upgrades)
        if (sku == null) {
            // A full snapshot proves absence; a partial one (purchase event, single-type query)
            // only proves presence of what it contains and must not start an unconfirmed episode.
            if (fresh.isFullSnapshot) recordProUnconfirmedLocked()
            return@withLock
        }
        val storedSkuId = billingCache.lastProStateSku.value()
        val storedType = OurSku.PRO_SKUS.singleOrNull { it.id == storedSkuId }?.type
        // A non-full snapshot (purchase event, partial refresh) proves ownership of what it
        // contains, but not the ABSENCE of anything else: it must not downgrade the grace class of
        // a previously confirmed permanent IAP (30d) to the subscription window (7d). Only a full
        // snapshot, where Play confirmed the IAP is really gone, may do that.
        val effectiveSkuId = if (
            !fresh.isFullSnapshot && storedType == Sku.Type.IAP && sku.type != Sku.Type.IAP
        ) {
            storedSkuId
        } else {
            sku.id
        }
        log(TAG, VERBOSE) { "Fresh Pro state confirmed by $sku, stamping $effectiveSkuId" }
        // One transaction: also closes any unconfirmed episode atomically.
        billingCache.stampLastProState(effectiveSkuId, System.currentTimeMillis())
    }

    private suspend fun recordProUnconfirmed() = proStateLock.withLock { recordProUnconfirmedLocked() }

    // Fresh reconciliation failed to confirm a known Pro purchase (full-snapshot empty result or
    // query error). Starts the unconfirmed-episode clock that delays the grace hint on the upgrade
    // screen. Set-if-unset so follow-up failures never refresh it; stamps from an earlier episode
    // (older than the last confirmation) or from the future (clock changes) are replaced.
    // Fail-quiet: purely informational, must never affect entitlement handling.
    private suspend fun recordProUnconfirmedLocked() {
        try {
            val lastProStateAt = billingCache.lastProStateAt.value()
            val now = System.currentTimeMillis()
            val sinceConfirm = now - lastProStateAt
            // sinceConfirm <= 0 also rejects future confirmations (clock moved backwards) — they
            // would otherwise pass the window check and re-stamp the episode on every attempt.
            if (lastProStateAt <= 0L || sinceConfirm <= 0L || sinceConfirm >= graceWindowMs()) return
            billingCache.proUnconfirmedSince.update { current ->
                val stale = current <= 0L || current < lastProStateAt || current > now
                if (stale) now else current
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to record unconfirmed pro state: ${e.asLog()}" }
        }
    }

    // Shared Pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes Pro if we haven't had it for a while (grace period). READ-ONLY: this runs on
    // replayed shared-flow data too, so it must never stamp the grace cache — see recordProState().
    private suspend fun BillingData?.toUpgradeInfo(): Info {
        // Branch on MAPPED upgrades, not raw purchases: a purchase list containing only products
        // this app doesn't know maps to zero upgrades and must fall through to the grace check —
        // otherwise a recently-Pro user is denied grace they're entitled to. A known purchase is
        // decided before any grace-cache read, so failing local storage can't turn a confirmed
        // purchase into an error episode.
        val mapped = Info(billingData = this)
        if (mapped.upgrades.isNotEmpty()) return mapped

        val now = System.currentTimeMillis()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> mapped
        }
    }

    // Grace window depends on what was last owned: a permanent one-time purchase gets a long window,
    // a subscription (or an unknown/legacy last SKU) gets the short default.
    private suspend fun graceWindowMs(): Long {
        val lastSku = billingCache.lastProStateSku.value()
        val type = OurSku.PRO_SKUS.singleOrNull { it.id == lastSku }?.type
        val window = if (type == Sku.Type.IAP) GRACE_PERIOD_IAP_MS else GRACE_PERIOD_MS
        log(TAG) { "graceWindowMs(): lastSku=$lastSku, type=$type -> ${window}ms" }
        return window
    }

    data class Info(
        private val gracePeriod: Boolean = false,
        private val billingData: BillingData?,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY

        val upgrades: Collection<PurchasedSku> = billingData?.purchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, ERROR) { "Unknown product: $productId ($purchase)" }
                        return@mapNotNull null
                    } else {
                        log(TAG) { "Mapped $productId to $sku ($purchase)" }
                    }
                    PurchasedSku(sku, purchase)
                }
            }
            ?.flatten()
            ?: emptySet()

        override val isPro: Boolean = upgrades.isNotEmpty() || gracePeriod

        override val upgradedAt: Instant? = upgrades
            .maxByOrNull { it.purchase.purchaseTime }
            ?.let { Instant.ofEpochMilli(it.purchase.purchaseTime) }
    }


    companion object {
        private const val STORE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.sdmse"
        private const val UPGRADE_SITE = "https://play.google.com/store/apps/details?id=eu.darken.sdmse"
        private const val BETA_SITE = "https://play.google.com/apps/testing/eu.darken.sdmse"
        // Keep paying users Pro through transient empty/failed Play Billing responses. A permanent
        // one-time purchase should almost never be dropped on a hiccup, so it gets a long window; a
        // subscription legitimately lapses, so it keeps the short one. GRACE_PERIOD_MS is the
        // subscription/default window (also used when the last-owned SKU is unknown/legacy).
        val GRACE_PERIOD_MS = Duration.ofDays(7).toMillis()
        val GRACE_PERIOD_IAP_MS = Duration.ofDays(30).toMillis()
        private const val RESTORE_ON_OWNED_TIMEOUT_MS = 15_000L
        private const val REFRESH_TIMEOUT_MS = 30_000L
        val TAG: String = logTag("Upgrade", "Gplay", "Repo")

        // The SKU whose grace window applies when several are owned: the permanent one-time purchase
        // wins over a subscription (purchases are time-sorted, so firstOrNull alone isn't enough).
        // null when no known Pro SKU is owned.
        internal fun preferredProSku(upgrades: Collection<PurchasedSku>): Sku? =
            upgrades.firstOrNull { it.sku.type == Sku.Type.IAP }?.sku ?: upgrades.firstOrNull()?.sku

        // Backoff for the local-failure retry in upgradeInfo: 30s/60s/120s/240s, capped at 5min.
        // Integer math on purpose — the old Double-pow formula slept for hours and could overflow
        // into a hot loop at extreme attempt counts. Pure and unit-tested.
        internal fun retryDelayMs(attempt: Long): Long =
            if (attempt >= 4) 300_000L else 30_000L shl attempt.toInt()
    }
}
