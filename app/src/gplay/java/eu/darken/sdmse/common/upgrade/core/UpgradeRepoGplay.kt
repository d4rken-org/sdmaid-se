package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
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
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class UpgradeRepoGplay @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    private val billingManager: BillingManager,
    private val billingCache: BillingCache,
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

    init {
        // Grace bookkeeping is driven by *fresh* Play data only: freshBillingData emissions each
        // represent an actual Play round-trip (per-connection/manual query results, completed
        // purchase events) — never the replayed billingData/upgradeInfo flows, whose old data
        // must not keep re-stamping the grace window (e.g. after a refund).
        billingManager.freshBillingData
            .onEach { fresh ->
                try {
                    stampLastProState(fresh)
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
            // Ignore Google Play errors if the last pro state was recent
            val now = System.currentTimeMillis()
            val lastProStateAt = billingCache.lastProStateAt.value()
            log(TAG) { "Catch: now=$now, lastProStateAt=$lastProStateAt, attempt=$attempt, error=$error" }
            if ((now - lastProStateAt) < graceWindowMs()) {
                log(TAG, VERBOSE) { "We are not pro, but were recently, and just got an error, what is GPlay doing???" }
                emit(Info(gracePeriod = true, billingData = null))
            } else {
                emit(Info(billingData = null, error = error))
            }
            delay(30_000L * 2.0.pow(attempt.toDouble()).toLong())
            true
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a (known) Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .distinctUntilChanged()

    fun launchBillingFlow(
        activity: Activity,
        sku: Sku,
        offer: Sku.Subscription.Offer?,
        onError: (Throwable) -> Unit,
    ) {
        log(TAG) { "launchBillingFlow($activity,$sku)" }
        // AppScope on purpose: the purchase flow and the already-owned recovery below must survive
        // the upgrade screen being closed; the reactive isPro emission unlocks the app either way.
        scope.launch {
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
                        if (restored?.isPro != true) {
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

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        try {
            // Bounded: with unbounded connection retry, an unavailable Play would otherwise keep
            // background callers (MainViewModel, isProSettled gates) suspended indefinitely.
            // Grace stamping happens via the freshBillingData collector, not here.
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) { billingManager.refresh() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background refresh: keep the old swallow-and-log behaviour so callers like MainViewModel
            // aren't affected. The explicit restore path uses restorePurchaseNow(), which surfaces errors.
            log(TAG, ERROR) { "Background refresh failed: ${e.asLog()}" }
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
                Info(gracePeriod = true, billingData = null)
            } else {
                throw e
            }
        }
    }

    // Persists "we saw a known Pro purchase" for the grace machinery. Only ever fed by the
    // freshBillingData collector — fresh Play round-trips, never replayed flow data, so a refunded
    // purchase can't keep re-stamping its grace window.
    private suspend fun stampLastProState(fresh: BillingManager.FreshData) {
        val sku = preferredProSku(Info(billingData = fresh.data).upgrades) ?: return
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
        billingCache.stampLastProState(effectiveSkuId, System.currentTimeMillis())
    }

    // Shared Pro/grace mapping used by both the reactive upgradeInfo flow and restorePurchaseNow().
    // Only relinquishes Pro if we haven't had it for a while (grace period). READ-ONLY: this runs on
    // replayed shared-flow data too, so it must never stamp the grace cache — see stampLastProState().
    private suspend fun BillingData?.toUpgradeInfo(): Info {
        val now = System.currentTimeMillis()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            this?.purchases?.isNotEmpty() == true -> Info(billingData = this)

            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                Info(gracePeriod = true, billingData = null)
            }

            else -> Info(billingData = this)
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
    }
}
