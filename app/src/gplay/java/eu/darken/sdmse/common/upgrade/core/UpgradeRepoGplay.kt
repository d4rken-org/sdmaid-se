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
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PendingPurchaseBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import eu.darken.sdmse.common.upgrade.core.billing.UserCanceledBillingException
import eu.darken.sdmse.common.upgrade.core.billing.client.redacted
import eu.darken.sdmse.common.upgrade.core.billing.work.PurchaseAckScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.shareIn
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
    private val ackScheduler: PurchaseAckScheduler,
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

    // Process-wide single-flight for Play launches: the launch runs on AppScope and outlives the
    // ViewModel that started it, so a VM-level guard alone lets a rotation (or a second screen)
    // start a competing purchase flow. Holds the SKU being launched, null while idle.
    private val launchBusySku = MutableStateFlow<Sku?>(null)

    // Which purchase launch (if any) is currently in flight, so the UI can present the busy state
    // even for a launch a previous ViewModel instance started.
    val purchaseLaunchSku: StateFlow<Sku?> = launchBusySku

    // Test seam: the launch body runs on AppScope (a real dispatcher), so a virtual-time test
    // cannot advance the production bound. Same pattern as UpgradeViewModel's `clock`.
    internal var launchTimeoutMs: Long = LAUNCH_TIMEOUT_MS

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

        // Connect-loop failures never reach an explicit refresh() caller (the loop retries
        // internally and downstream flows just go quiet), so without this a sustained Play outage
        // between ON_RESUME refreshes wouldn't advance the grace episode clock. The emitted value is
        // the failure's occurrence time: recordProUnconfirmed uses it (not processing-time) so a
        // buffered failure that a later success already superseded is dropped rather than reopening a
        // closed episode. Set-if-unset and grace-guarded, so repeated outages collapse to a single
        // episode start and installs that were never Pro (lastProStateAt == 0) are ignored.
        billingManager.connectionFailures
            .onEach { failedAt -> recordProUnconfirmed(failedAt) }
            .setupCommonEventHandlers(TAG) { "connectionFailureRecorder" }
            .launchIn(scope)

    }

    // Settledness travels WITH the ownership data (Info.isSettled), never on a parallel flow —
    // a parallel signal could be observed out of step and pair "settled" with a stale non-Pro
    // seed for one emission (the old cosmetic flash at owners).
    //
    // Per emission: data != null means a COMMITTED Play round-trip happened by construction
    // (BillingConnection.purchases only emits after refreshPurchases committed under the reducer
    // lock, and the manager only publishes the connection after that refresh succeeded). Note:
    // committed, not necessarily complete — combinePurchaseResults tolerates one failed product
    // type when the other returned a purchase, so a partial snapshot also settles (same as the
    // old signal; grace covers a recently-confirmed Pro whose type failed). A null seed settles
    // only via isFailureSettled: Play is unreachable, so seed + grace mapping IS the best
    // knowledge. Accepted residual: isFailureSettled is sticky, so after a failure-then-recovery
    // a fresh resubscribe can briefly pair the seed with settled=true before the data replay
    // lands — identical to the old signal's stickiness, covered by grace + the UI Loading gates;
    // the pure-success-path race (settled leading the FIRST reconciliation) is now impossible.
    override val upgradeInfo: Flow<Info> = combine(
        billingManager.billingData
            .map<BillingData, BillingData?> { it }
            .onStart { emit(null) },
        billingManager.isFailureSettled,
    ) { data, failureSettled -> data to (data != null || failureSettled) }
        .setupCommonEventHandlers(TAG) { "upgradeInfo1" }
        .map { (data, settled) -> data.toUpgradeInfo(settled = settled) }
        .distinctUntilChanged()
        .retryWhen { error, attempt ->
            if (error is CancellationException) return@retryWhen false
            // Billing connection errors can no longer reach this flow (the connect loop retries
            // them internally) — what CAN fail here are the LOCAL DataStore reads in the Pro
            // mapping, plausible exactly when storage is full. Keep the flow alive and keep a
            // recently-Pro user in their grace window.
            log(TAG, WARN) { "upgradeInfo mapping failed (attempt=$attempt): ${error.asLog()}" }
            // Fallbacks are settled: a local storage failure is a definitive best-knowledge
            // outcome — gates must resolve now, not stall out a 30s+ retry backoff.
            val fallback = try {
                if ((System.currentTimeMillis() - billingCache.lastProStateAt.value()) < graceWindowMs()) {
                    Info(gracePeriod = true, billingData = null, isSettled = true)
                } else {
                    Info(billingData = null, error = error, isSettled = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The grace probe reads the same storage that just failed — a second failure must
                // not kill the retry loop that exists for exactly this situation.
                Info(billingData = null, error = error, isSettled = true)
            }
            emit(fallback)
            delay(retryDelayMs(attempt))
            true
        }
        // Settledness is monotonic within a subscription span: the retry above resubscribes the
        // upstream, whose onStart re-emits the null seed — without this latch, that seed would
        // regress an already-settled stream back to unsettled until the billing replay lands.
        .runningReduce { acc, next ->
            if (acc.isSettled && !next.isSettled) next.copy(isSettled = true) else next
        }
        .setupCommonEventHandlers(TAG) { "upgradeInfo2" }
        .shareIn(scope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // True once we've ever confirmed a (known) Pro purchase on this install; drives the proactive
    // restore banner. Local signal only — a fresh install or switched Google account starts false.
    // Fail-soft, and that matters more here than the value itself: this is combined into the whole
    // upgrade-screen state, whose safeStateIn fallback is TERMINAL (it catches, so the retry button
    // can't revive the stream). A full-disk DataStore would otherwise take the entire screen down
    // until it is reopened, over a decoration. Degrade to "not previously pro" instead — entitlement
    // does not depend on this, upgradeInfo reads the cache on its own retrying path.
    val wasEverPro: Flow<Boolean> = billingCache.lastProStateAt.flow
        .map { it > 0 }
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, WARN) { "wasEverPro read failed: ${e.asLog()}" }
            emit(false)
        }
        .distinctUntilChanged()

    // Epoch millis of the first fresh reconciliation that couldn't confirm Pro in the current grace
    // episode (0 = none). The upgrade screen delays its grace diagnostics until this has aged, so
    // self-healing Play blips never surface it.
    val proUnconfirmedSince: Flow<Long> = billingCache.proUnconfirmedSince.flow
        // Same fail-soft reasoning as wasEverPro — 0 reads as "no episode", i.e. the quiet grace stage.
        .catch { e ->
            if (e is CancellationException) throw e
            log(TAG, WARN) { "proUnconfirmedSince read failed: ${e.asLog()}" }
            emit(0L)
        }
        .distinctUntilChanged()

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
        // Silent coalesce, no error event: a second tap (or a second ViewModel instance after a
        // rotation) must not open a competing Play sheet. Cleared in the finally below, so the next
        // deliberate tap works. The already-owned recovery inside only restores, it never re-enters
        // this function.
        if (!launchBusySku.compareAndSet(null, sku)) {
            log(TAG, WARN) { "Billing launch already in flight, ignoring" }
            return
        }
        try {
            // Persistent ack safety net, launch trigger: armed and AWAITED before the Play sheet
            // can open, so the WorkManager DB transaction lands even if the process dies around
            // the sheet — the exact window behind Play's unacknowledged-purchase auto-refunds.
            // Failure to arm never blocks the purchase; the foreground ack path still exists.
            try {
                ackScheduler.armForBillingFlowLaunch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to arm ack safety net for launch: ${e.asLog()}" }
            }

            // Bounded, like every other Play path (refresh, restore, SKU query, ack). useConnection
            // waits for a healthy connection indefinitely, so a Play outage between rendering the
            // offers and this tap would park the launch forever — with launchBusySku still held,
            // which leaves every purchase button busy and never surfaces an error. Generous on
            // purpose: a cold Play can take >8s just for the SKU query this launch does first.
            // Residual: a timeout landing in the millisecond window between launchBillingFlow's
            // binder call and its result shows an error while the sheet opens. The purchase itself
            // is unaffected — it arrives via onPurchasesUpdated, independent of this coroutine.
            withTimeoutOrNull(launchTimeoutMs) {
                billingManager.startIapFlow(activity, sku, offer)
            } ?: throw GplayServiceUnavailableException(
                RuntimeException("Billing flow launch timed out for ${sku.id}")
            )
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
                        if (restored?.pendingSkus?.isNotEmpty() == true) {
                            // Play blocks re-purchasing a product whose payment it is still
                            // processing. "Already owned" is technically what Play said, but the
                            // dialog's restore tips are the wrong advice: nothing to restore, and
                            // the entitlement lands by itself once the payment clears.
                            log(TAG, INFO) { "Already-owned recovery found a pending payment" }
                            onError(PendingPurchaseBillingException(e))
                        } else {
                            // Couldn't reconcile the entitlement (account mismatch, Play quirk) —
                            // fall back to the already-owned dialog with restore tips.
                            onError(e)
                        }
                    }
                }

                else -> {
                    log(TAG) { "startIapFlow failed:${e.asLog()}" }
                    onError(e)
                }
            }
        } finally {
            // Released on ANY termination, including the already-owned recovery path and caller
            // cancellation: a launch that is over must not block the next one.
            launchBusySku.value = null
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
                    // Provenance is irrelevant here: the caller only checks whether the claimed SKU
                    // came back, and a grace-only result doesn't reconcile it either way.
                    withTimeoutOrNull(RESTORE_ON_OWNED_TIMEOUT_MS) { restorePurchaseNow().info }
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

    // Strict purchase-state lookup for the pre-purchase gates: a fresh COMPLETE round-trip with
    // explicit failure. No grace substitution and no partial tolerance (unlike refresh() and
    // restorePurchaseNow()) — callers must treat any error as "couldn't verify" and fail closed.
    // Covers both product types and pending payments, because both can make a purchase wrong: a
    // renewing subscription (double billing) and a payment Play is still processing (Play rejects
    // the re-purchase).
    suspend fun verifyPurchaseStateNow(): Info {
        log(TAG) { "verifyPurchaseStateNow()" }
        return Info(billingData = billingManager.refreshStrict(), isSettled = true)
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

    override suspend fun onAppStart() {
        log(TAG) { "onAppStart()" }
        try {
            ackScheduler.armPeriodicSweep()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to arm periodic ack sweep: ${e.asLog()}" }
        }
    }

    // Explicit "Restore purchase": query Play now and evaluate Pro from the returned data in the same
    // coroutine (real happens-before), so we never read a stale upgradeInfo replay. Billing errors
    // propagate so the caller can distinguish "not owned" from "Play unavailable".
    suspend fun restorePurchaseNow(): RestoreOutcome {
        // INFO: pairs with the refreshPurchases() outcome line so a support log shows which Play
        // round-trip belongs to an explicit restore tap.
        log(TAG, INFO) { "restorePurchaseNow()" }
        return try {
            // A restore result IS a real Play round-trip outcome -> settled.
            RestoreOutcome.Checked(billingManager.refresh().toUpgradeInfo(settled = true))
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
                // Grace keeps Pro, but the lookup itself never landed. Reported as Inconclusive so
                // the UI can't claim a completed check: an owner in grace is exactly who must not
                // be told "we checked Play and found nothing".
                RestoreOutcome.Inconclusive(Info(gracePeriod = true, billingData = null, isSettled = true), e)
            } else {
                throw e
            }
        }
    }

    /**
     * Provenance of an explicit restore, kept apart from [Info] so entitlement stays untouched.
     *
     * [Info] alone can't carry this: a grace-substituted `Info(gracePeriod = true, billingData =
     * null)` is produced both by a successful empty query (a real answer) and by a swallowed Play
     * error (no answer at all). Those need opposite UI treatment.
     */
    sealed interface RestoreOutcome {
        val info: Info

        /** Play answered. [info] reflects a real entitlement lookup. */
        data class Checked(override val info: Info) : RestoreOutcome

        /** Play couldn't be reached; [info] is grace-substituted and ownership stays unknown. */
        data class Inconclusive(override val info: Info, val cause: Throwable) : RestoreOutcome
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
            // occurredAt is the snapshot's commit time — when Play confirmed the absence.
            if (fresh.isFullSnapshot) recordProUnconfirmedLocked(fresh.occurredAt)
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
        // Stamp with the confirmation's OWN commit time, not processing-now: the same value gates
        // which unconfirmed episode this closes (BillingCache only clears an episode that began at or
        // before it) and lets a later connection failure be correctly ordered against this success.
        billingCache.stampLastProState(effectiveSkuId, fresh.occurredAt)
    }

    private suspend fun recordProUnconfirmed(occurredAt: Long = System.currentTimeMillis()) =
        proStateLock.withLock { recordProUnconfirmedLocked(occurredAt) }

    // Fresh reconciliation failed to confirm a known Pro purchase (full-snapshot empty result or
    // query error). Starts the unconfirmed-episode clock that delays the grace hint on the upgrade
    // screen. Set-if-unset so follow-up failures never refresh it; stamps from an earlier episode
    // (older than the last confirmation) or from the future (clock changes) are replaced.
    // Fail-quiet: purely informational, must never affect entitlement handling.
    //
    // occurredAt is WHEN the failure happened. Imperative callers (refresh/restore/empty snapshot)
    // pass the default (now), which is also their event time. The buffered connectionFailures feed
    // passes the failure's own timestamp so that a failure which happened BEFORE the latest
    // confirmation — e.g. one enqueued during an outage but consumed only after a later retry
    // succeeded and stamped Pro — is rejected by the sinceConfirm <= 0 guard instead of reopening
    // an episode Play already closed.
    private suspend fun recordProUnconfirmedLocked(occurredAt: Long = System.currentTimeMillis()) {
        try {
            val lastProStateAt = billingCache.lastProStateAt.value()
            val sinceConfirm = occurredAt - lastProStateAt
            // sinceConfirm <= 0 rejects failures superseded by a later confirmation AND future
            // confirmations (clock moved backwards) — both would otherwise pass the window check and
            // (re)stamp the episode.
            if (lastProStateAt <= 0L || sinceConfirm <= 0L || sinceConfirm >= graceWindowMs()) return
            billingCache.proUnconfirmedSince.update { current ->
                val stale = current <= 0L || current < lastProStateAt || current > occurredAt
                if (stale) occurredAt else current
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
    // settled comes from the caller, never from billingData nullness: a null-data Info can be
    // perfectly settled (a real empty snapshot), and the grace branch carries data through anyway.
    private suspend fun BillingData?.toUpgradeInfo(settled: Boolean): Info {
        // Branch on MAPPED upgrades, not raw purchases: a purchase list containing only products
        // this app doesn't know maps to zero upgrades and must fall through to the grace check —
        // otherwise a recently-Pro user is denied grace they're entitled to. A known purchase is
        // decided before any grace-cache read, so failing local storage can't turn a confirmed
        // purchase into an error episode.
        val mapped = Info(billingData = this, isSettled = settled)
        if (mapped.upgrades.isNotEmpty()) return mapped

        val now = System.currentTimeMillis()
        val lastProStateAt = billingCache.lastProStateAt.value()
        log(TAG) { "toUpgradeInfo(): now=$now, lastProStateAt=$lastProStateAt, data=$this" }
        return when {
            (now - lastProStateAt) < graceWindowMs() -> {
                log(TAG, VERBOSE) { "We are not pro, but were recently, did GPlay try annoy us again?" }
                // billingData is carried through, not dropped: this branch is only reached when the
                // mapped upgrades are empty, so entitlement stays empty either way — but a pending
                // payment must stay visible, and a grace user waiting on one is exactly who needs
                // the explanation.
                Info(gracePeriod = true, billingData = this, isSettled = settled)
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
        // Default false is the fail-safe direction: a forgotten stamp shows up as "never
        // settles" (loud), never as a settled pre-reconciliation flash. Mapping-only
        // constructions (trackProState, recordProState) never read this.
        override val isSettled: Boolean = false,
    ) : UpgradeRepo.Info {

        override val type: UpgradeRepo.Type = UpgradeRepo.Type.GPLAY

        val upgrades: Collection<PurchasedSku> = billingData?.purchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, ERROR) { "Unknown product: $productId (${purchase.redacted()})" }
                        return@mapNotNull null
                    } else {
                        log(TAG) { "Mapped $productId to $sku (${purchase.redacted()})" }
                    }
                    PurchasedSku(sku, purchase)
                }
            }
            ?.flatten()
            ?: emptySet()

        // Products with a payment Play is still processing. Deliberately NOT part of [isPro] or
        // [upgrades]: a pending payment grants nothing. It exists so the UI can explain the wait
        // and lock the purchase buttons — buying the alternative product now would double-charge.
        val pendingSkus: Collection<Sku> = billingData?.pendingPurchases
            ?.map { purchase ->
                purchase.products.mapNotNull { productId ->
                    val sku = OurSku.PRO_SKUS.singleOrNull { it.id == productId }
                    if (sku == null) {
                        log(TAG, WARN) { "Unknown pending product: $productId (${purchase.redacted()})" }
                        return@mapNotNull null
                    }
                    sku
                }
            }
            ?.flatten()
            ?: emptySet()

        // Any owned purchase Play still bills on a schedule. Deliberately computed from the RAW
        // PURCHASED purchases instead of [upgrades]: the mapping drops products this app doesn't
        // know, and the pre-purchase gate must keep blocking on a renewing subscription with an
        // unknown or legacy product ID — being wrong there means billing the user twice for Pro.
        // Both product types are scanned; a one-time purchase reports isAutoRenewing = false, so
        // the broader input cannot produce a false positive.
        // Computed on access, not in the initializer: an Info is built for every mapping pass, and
        // only the purchase gate needs this.
        val hasAutoRenewingSubscription: Boolean
            get() = billingData?.purchases?.any { it.isAutoRenewing } == true

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

        // Covers connecting, the SKU query the launch does first, and the sheet launch itself.
        // Matches REFRESH_TIMEOUT_MS: both bound "connection wait + Play round-trip".
        internal const val LAUNCH_TIMEOUT_MS = 30_000L
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
