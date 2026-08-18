package eu.darken.sdmse.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.coroutine.AppScope
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingClientException
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnection
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnectionProvider
import eu.darken.sdmse.common.upgrade.core.billing.client.isPurchased
import eu.darken.sdmse.common.upgrade.core.billing.client.redacted
import eu.darken.sdmse.common.upgrade.core.billing.work.PurchaseAckScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
    private val ackScheduler: PurchaseAckScheduler,
) {

    // Fresh Play data plus its provenance: a query result covers owned products of the queried
    // types, while a purchase event only carries the products of that transaction — consumers
    // deciding between per-SKU behaviors (like grace windows) need to know the difference.
    data class FreshData(
        val data: BillingData,
        val isFullSnapshot: Boolean,
        // Commit time of the underlying Play round-trip — see BillingConnection.FreshUpdate.occurredAt.
        val occurredAt: Long = System.currentTimeMillis(),
    )

    // Bumped whenever someone actively wants billing NOW (see useConnection): a pending reconnect
    // backoff is cut short instead of making the user wait out the timer. A generation counter
    // (compared against the value captured at attempt start) instead of an event flow, so demand
    // arriving while a connection attempt is still in flight isn't lost, while demand that was
    // already satisfied by a healthy connection can't skip a future backoff.
    private val connectionDemand = MutableStateFlow(0)

    // Highest demand generation whose useConnection call already terminated (served, failed, or
    // cancelled): settled demand must not skip a backoff after a later disconnect.
    private val servedDemand = MutableStateFlow(0)

    // Signalled when an action fails with a response code that means the current connection is
    // dead (binder gone) — Play doesn't always deliver onBillingServiceDisconnected, and a dead
    // connection must not stay installed for later callers.
    private val invalidations = Channel<Unit>(Channel.CONFLATED)

    // The currently usable connection, null while (re)connecting. Nulled BEFORE any backoff, so a
    // dead connection is unreachable by construction — no replay cache to serve stale clients.
    private val connectionHolder = MutableStateFlow<BillingConnection?>(null)

    // At least one connect-loop iteration FAILED since process start. Success needs no explicit
    // signal: it is implied by billingData emitting (the connection is only published after its
    // initial refreshPurchases committed), so settledness travels with the data itself and can't
    // lead it. Failures are different — the connect loop swallows them (it retries forever) and
    // downstream flows just stay quiet during an outage, so consumers need this explicit signal
    // to settle their null seed instead of waiting on a broken connection indefinitely.
    private val failedOnce = MutableStateFlow(false)
    val isFailureSettled: Flow<Boolean> = failedOnce

    // Fires once per reconciliation that couldn't confirm Pro: a failed connect-loop iteration
    // (connection setup failure, the mandatory initial refreshPurchases erroring or timing out, an
    // established connection dropping, an action-level invalidation, an unexpected provider
    // completion) — and, via processReconciliation, a refresh that COMPLETED but only partially,
    // without a confirmed Pro purchase. The connect loop retries its failures internally and
    // downstream flows just go quiet, so without this explicit signal the grace episode clock
    // (UpgradeRepoGplay.proUnconfirmedSince) would only advance on an explicit ON_RESUME refresh().
    //
    // Each value is the failure's OCCURRENCE time (epoch millis). It has to be, not a bare Unit: the
    // channel buffers, and this feed and freshBillingData are separate flows with no cross-stream
    // ordering, so a failure enqueued before a later retry succeeds could be consumed AFTER that
    // success already confirmed Pro and closed the episode. Carrying the failure's own timestamp lets
    // the consumer compare it against the last confirmation and drop a superseded one instead of
    // reopening a closed episode. UNLIMITED + receiveAsFlow (same idiom as BillingConnection
    // .freshUpdates): one lifetime consumer, buffered so a failure that fires before it subscribes
    // isn't lost.
    private val connectionFailuresChannel = Channel<Long>(Channel.UNLIMITED)
    val connectionFailures: Flow<Long> = connectionFailuresChannel.receiveAsFlow()

    init {
        // The connect loop: owns ALL retry policy. Deliberately NOT wrapped in
        // setupCommonEventHandlers — its catch{} swallows cancellations, and this loop must die
        // with the scope, not retry through it.
        scope.launch {
            var failStreak = 0
            while (true) {
                val demandAtStart = connectionDemand.value
                // Drain invalidations from the previous connection's lifetime: a signal referring
                // to an already-dead connection must not kill the upcoming attempt. (A racing
                // signal between here and the watcher below costs one extra reconnect, nothing
                // more.)
                while (invalidations.tryReceive().isSuccess) {
                    // drained
                }
                try {
                    coroutineScope {
                        val invalidationWatcher = launch {
                            invalidations.receive()
                            throw BillingException("Billing connection invalidated by a failed action")
                        }
                        try {
                            connectionProvider.connection.collect { connection ->
                                // A refresh that can't verify anything (nothing found + a query
                                // failed) throws and counts as a connection failure — otherwise a
                                // cold start against a broken Play would starve billingData and
                                // isFailureSettled forever with no retry. withTimeoutOrNull, NOT
                                // withTimeout: TimeoutCancellationException is a
                                // CancellationException and would kill this loop.
                                val initialRefresh = withTimeoutOrNull(INITIAL_REFRESH_TIMEOUT_MS) {
                                    connection.refreshPurchases()
                                } ?: throw BillingException("Initial purchase refresh timed out")

                                failStreak = 0
                                connectionHolder.value = connection
                                log(TAG, INFO) { "Billing connection established" }
                                // AFTER publishing: a partial refresh is still a usable connection
                                // (a pending-only cold start must not starve billingData), but its
                                // bookkeeping — episode clock, dead-binder teardown — has to run,
                                // and an invalidation may only tear down an INSTALLED connection.
                                processReconciliation(initialRefresh)
                            }
                            // The provider flow stays open for the connection's lifetime; a normal
                            // completion means the connection is gone without an error — treat it
                            // like one so we reconnect (with backoff, no tight loop).
                            throw BillingException("Billing connection completed unexpectedly")
                        } finally {
                            invalidationWatcher.cancel()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, WARN) { "Billing connection failed: ${e.asLog()}" }
                    // A failed iteration is a fresh reconciliation that couldn't confirm Pro — signal
                    // it (with its occurrence time) so the entitlement layer can advance the grace
                    // episode clock even when no explicit refresh() caller is watching. Superseded
                    // failures are dropped downstream; duplicates are idempotent (set-if-unset).
                    connectionFailuresChannel.trySend(System.currentTimeMillis())
                }
                connectionHolder.value = null
                // Only reachable via the catch above (the collect never returns normally and
                // cancellation rethrows past this) — a genuinely failure-only signal.
                failedOnce.value = true
                // A swallowed cancellation (e.g. via a flow wrapper) must not convert scope death
                // into another connection attempt.
                ensureActive()
                failStreak++
                val backoffMs = if (failStreak >= 5) MAX_BACKOFF_MS else 2_000L shl (2 * (failStreak - 1))
                log(TAG) { "Billing reconnect backoff: streak=$failStreak, waiting ${backoffMs}ms" }
                // Interruptible backoff: demand that is newer than this attempt AND not yet served
                // skips the wait — a user who just fixed their Play situation shouldn't wait out
                // the timer. The demandAtStart comparison limits a still-waiting caller to one
                // skip per attempt (no tight retry loop).
                withTimeoutOrNull(backoffMs) {
                    connectionDemand.first { it != demandAtStart && it > servedDemand.value }
                }
            }
        }
    }

    // Serializes acknowledgement work between the reactive ack collector and explicit
    // ensureAllAcknowledged() sweeps (PurchaseAckWorker): both paths mutate the token bookkeeping
    // sets and both must never double-drive the same purchase's inline retry sequence.
    private val ackMutex = Mutex()

    // Re-drives the ack pass WITHOUT a new purchases emission: `purchases` is distinctUntilChanged,
    // so a refresh returning a byte-identical (still unacknowledged) list is deduped and could never
    // retry a failed ack -- the pipeline starved until Play sent something different. Declared ahead
    // of `purchases` because that chain's failure recovery signals it.
    private val ackRetryTrigger = MutableStateFlow(0)

    // Never-terminal resubscribe for the hot billing sources: a shared flow has no owner to restart
    // it, so an upstream failure would kill the sharing coroutine and leave every subscriber (incl.
    // the ack collector) hanging for the process lifetime.
    private fun <T> Flow<T>.resubscribeOnFailure(label: String, onRecover: () -> Unit = {}): Flow<T> =
        retryWhen { cause, _ ->
            if (cause is CancellationException) return@retryWhen false
            log(TAG, ERROR) { "$label failed, resubscribing: ${cause.asLog()}" }
            delay(SHARE_RETRY_MS)
            onRecover()
            true
        }

    private val purchases = connectionHolder
        // NOT filterNotNull(): the null emission is what detaches a dead connection's inner flows.
        .flatMapLatest { connection ->
            (connection?.purchases ?: emptyFlow())
                // The retry sits INSIDE the flatMapLatest, and that placement is load-bearing:
                // flatMapLatest hands values to its downstream across a channel, and a failure of
                // the inner flow cancels the coroutine that drains it -- a value emitted just
                // before the failure is then discarded instead of delivered. Retrying out here
                // keeps the failure off that boundary, so the last pre-failure emission (e.g. the
                // purchase that still needs acknowledging) survives the outage.
                .resubscribeOnFailure("purchases") {
                    // The resubscribed source replays a byte-identical list, which the
                    // distinctUntilChanged below rightly drops -- so recovery has to nudge the ack
                    // pass explicitly, or a purchase that arrived just before the failure would sit
                    // there until the 5-minute reschedule.
                    ackRetryTrigger.update { it + 1 }
                }
        }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "purchases" }
        // Belt for the plumbing outside the source flow itself: connectionHolder is a state holder
        // that never throws, so this should never fire -- but a dead sharing coroutine is
        // unrecoverable, and that is not a risk worth leaving open. No nudge needed here: this
        // resubscribes distinctUntilChanged too, so the replayed list gets through on its own.
        .resubscribeOnFailure("purchases-share")
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val billingData: Flow<BillingData> = purchases
        .map { BillingData.from(it) }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val purchaseFailures: Flow<BillingResult> = connectionHolder
        .flatMapLatest { it?.purchaseFailures ?: emptyFlow() }
        .setupCommonEventHandlers(TAG) { "purchaseFailures" }

    // Only data that was *freshly* obtained from Play, in the connection's COMMIT ORDER: query
    // results and completed purchase events, emitted by the reducer itself. Unlike billingData
    // (whose shareIn replay re-serves old data to late subscribers), every emission here
    // represents an actual Play round-trip, so consumers can safely use it for time-based
    // bookkeeping like the Pro grace period. Eagerly: the per-connection channel has exactly one
    // consumer — this chain — which must not depend on downstream subscribers.
    val freshBillingData: Flow<FreshData> = connectionHolder
        .flatMapLatest { connection ->
            (connection?.freshUpdates ?: emptyFlow())
                // Inside the flatMapLatest for the same reason as `purchases`: a failure escaping
                // the boundary discards the update that was already handed to the channel -- and
                // every emission here is a real Play round-trip the grace bookkeeping needs.
                .resubscribeOnFailure("freshBillingData")
        }
        // Through from() like every other exit, although the connection only ever puts PURCHASED
        // purchases on this stream: if that invariant ever broke, splitting keeps the grace
        // bookkeeping from stamping a pending payment as a confirmation.
        .map { FreshData(data = BillingData.from(it.purchases), isFullSnapshot = it.isFullSnapshot, occurredAt = it.occurredAt) }
        .setupCommonEventHandlers(TAG) { "freshBillingData" }
        // Same belt as `purchases`: an Eagerly shared flow that dies stays dead, and this one feeds
        // both the grace bookkeeping and the ack collector's re-drive signal.
        .resubscribeOnFailure("freshBillingData-share")
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    // Tokens we've already SUCCESSFULLY acknowledged this process. LOG-LEVEL HINT ONLY: it selects
    // INFO (first ack) vs DEBUG (idempotent repeat) and MUST NOT gate the acknowledgePurchase call
    // below. The immutable Purchase snapshot keeps reporting isAcknowledged=false until a fresh Play
    // query supersedes it, so the ack re-fires every emission until then; re-acking is a documented
    // no-op on Play's side, whereas skipping a needed ack gets the purchase auto-refunded after 3
    // days -- so the ack stays unconditional and this set only quiets the log spam. Confined by
    // ackMutex (the collector's pass and explicit sweeps both run under it).
    private val loggedAckTokens = mutableSetOf<String>()

    // Tokens whose PERMANENT ack failure was already reported. Play will keep rejecting these
    // (developer error, item not owned, unsupported feature), so the bug report fires once per token
    // instead of once per pass. Same ackMutex confinement as loggedAckTokens.
    private val reportedAckFailures = mutableSetOf<String>()

    // At most one reschedule timer in flight: repeated failures must not stack timers.
    private val ackRetryPending = MutableStateFlow(false)

    init {
        combine(
            // The canonical list is the ONLY data slot. The other side is signal-only, so a partial
            // (e.g. SUBS-only) fresh emission can never become the retried set.
            purchases,
            merge(freshBillingData.map { }, ackRetryTrigger.map { }),
        ) { currentPurchases, _ -> currentPurchases }
            .onEach { runAckPass(it) }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            // Never-dying belt: runAckPass only lets CancellationException escape, so this catches
            // flow-plumbing failures only -- the collector must live for the whole process.
            .retryWhen { cause, _ ->
                if (cause is CancellationException) {
                    log(TAG) { "Ack collector was cancelled (appScope died)" }
                    return@retryWhen false
                }
                log(TAG, ERROR) { "Ack collector failed, restarting: ${cause.asLog()}" }
                delay(ACK_CHAIN_RETRY_MS)
                true
            }
            .launchIn(scope)
    }

    // Per-purchase acknowledgement result. Derived ONLY from the acknowledgePurchase call (success =
    // returned without throwing; it throws on non-OK) -- NEVER from re-reading Purchase
    // .isAcknowledged, whose immutable snapshot stays false until a fresh Play query.
    private enum class AckOutcome { SUCCESS, TRANSIENT, PERMANENT }

    // Aggregate outcome of one ack pass; ensureAllAcknowledged() maps it to a sweep result.
    data class AckPassOutcome(val transient: Int, val permanent: Int)

    // One acknowledgement pass over the canonical purchase list. Never throws except cancellation:
    // transient failures schedule a re-drive, permanent ones are reported and left to organic fresh
    // -data signals.
    private suspend fun runAckPass(purchases: Collection<Purchase>): AckPassOutcome = ackMutex.withLock {
        val needAck = purchases.filter {
            // The canonical list carries pending payments too. Play rejects acknowledging one
            // PERMANENTLY, so an unfiltered pass would fire a bug report for every pending purchase,
            // every pass — and there is nothing to acknowledge until the payment completes anyway.
            if (!it.isPurchased) {
                log(TAG) { "Not acknowledgeable yet: ${it.redacted()}" }
                return@filter false
            }

            val needsAck = !it.isAcknowledged

            if (needsAck) log(TAG) { "Needs ACK: ${it.redacted()}" }
            else log(TAG) { "Already ACK'ed: ${it.redacted()}" }

            needsAck
        }

        if (needAck.isNotEmpty()) {
            // Arm the persistent safety net BEFORE attempting anything, and AWAIT the enqueue (the
            // scheduler bounds it): the inline retries below can span minutes, and a process death
            // inside them must not strand the purchase until Play's 3-day auto-refund. A deferred
            // signal (channel + collector) would reintroduce exactly that window. Fail-open: the
            // net is an extra layer, never a reason to skip the acks themselves.
            try {
                ackScheduler.armForUnackedPurchases(needAck.maxOf { it.purchaseTime } + ACK_SAFETY_NET_DEADLINE_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to arm ack safety net: ${e.asLog()}" }
            }
        }

        var transientFailures = 0
        var permanentFailures = 0

        for (purchase in needAck) {
            // First ack of a token is INFO; idempotent repeats drop to DEBUG. This never gates the
            // ack -- acknowledgePurchase runs regardless of set membership.
            val ackPriority = if (purchase.purchaseToken in loggedAckTokens) DEBUG else INFO
            log(TAG, ackPriority) { "Acknowledging purchase: ${purchase.redacted()}" }

            var outcome = AckOutcome.TRANSIENT
            var abortPass = false

            for (attempt in 1..ACK_MAX_ATTEMPTS) {
                outcome = try {
                    // Bounded: useConnection waits for a connection indefinitely, so without this an
                    // outage would park the pass (and every later retry) forever. A null result is a
                    // failed TRANSIENT attempt -- either the wait or the ack itself ran out of time.
                    val acked = withTimeoutOrNull(ACK_CONNECTION_TIMEOUT_MS) {
                        useConnection { acknowledgePurchase(purchase) }
                    }
                    if (acked != null) {
                        AckOutcome.SUCCESS
                    } else {
                        log(TAG, WARN) { "Ack attempt $attempt timed out: ${purchase.redacted()}" }
                        AckOutcome.TRANSIENT
                    }
                } catch (e: CancellationException) {
                    // AppScope death is not an acknowledgement failure: no reschedule, no retries.
                    throw e
                } catch (e: Exception) {
                    val code = (e as? BillingClientException)?.result?.responseCode
                    when {
                        code == BillingResponseCode.BILLING_UNAVAILABLE -> {
                            // Connection-level condition: every other purchase in this pass would
                            // fail the same way. The reschedule keeps re-attempting later -- unlike
                            // before, this no longer permanently kills the ack retries.
                            log(TAG, WARN) { "BILLING_UNAVAILABLE, aborting ack pass:\n${e.asLog()}" }
                            abortPass = true
                            AckOutcome.TRANSIENT
                        }

                        (code != null && code in PERMANENT_ACK_CODES) || e !is BillingException -> {
                            reportPermanentAckFailure(purchase, e)
                            AckOutcome.PERMANENT
                        }

                        else -> {
                            log(TAG, WARN) { "Ack attempt $attempt failed: ${purchase.redacted()}\n${e.asLog()}" }
                            AckOutcome.TRANSIENT
                        }
                    }
                }

                if (outcome == AckOutcome.SUCCESS) {
                    // Only after a *successful* ack: a failed one never lands here, so it stays loud
                    // and retryable.
                    loggedAckTokens.add(purchase.purchaseToken)
                    break
                }
                if (outcome == AckOutcome.PERMANENT || abortPass) break
                // 3s, 6s -- `attempt` starts at 1, deliberately no zero-delay first retry.
                if (attempt < ACK_MAX_ATTEMPTS) delay(ACK_RETRY_DELAY_MS * attempt)
            }

            if (outcome == AckOutcome.TRANSIENT) transientFailures++
            if (outcome == AckOutcome.PERMANENT) permanentFailures++
            if (abortPass) break
        }

        // Permanent failures never arm the timer -- only organic fresh-data signals re-attempt them.
        if (transientFailures > 0) {
            log(TAG, ERROR) {
                "$transientFailures purchase(s) left unacknowledged, re-driving in ${ACK_RESCHEDULE_MS}ms"
            }
            scheduleAckRetry()
        }

        AckPassOutcome(transient = transientFailures, permanent = permanentFailures)
    }

    // Outcome of an explicit safety-net sweep, see ensureAllAcknowledged().
    enum class AckSweepResult { COMPLETE, RETRY, PERMANENT_FAILURE }

    /**
     * One self-contained acknowledgement sweep for the persistent safety net (PurchaseAckWorker):
     * refresh from Play, then acknowledge everything unacknowledged IN THIS COROUTINE. The reactive
     * ack collector consumes purchase state asynchronously, so a caller that needs proof the acks
     * actually happened before it reports success (a worker deciding success vs retry) cannot rely
     * on it. Never throws except cancellation.
     */
    suspend fun ensureAllAcknowledged(): AckSweepResult {
        log(TAG) { "ensureAllAcknowledged()" }
        val fresh = try {
            useConnection { refreshPurchases() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "ensureAllAcknowledged(): refresh failed: ${e.asLog()}" }
            return AckSweepResult.RETRY
        }
        // Same bookkeeping every other refresh exit owes: grace episode clock + dead-binder teardown.
        processReconciliation(fresh)
        val outcome = runAckPass(fresh.purchases)
        return when {
            // An incomplete refresh may be hiding an unacknowledged purchase of the failed type,
            // and a transient ack failure is retriable by definition.
            outcome.transient > 0 || !fresh.isComplete -> AckSweepResult.RETRY
            // Play will keep rejecting these no matter how often the worker comes back.
            outcome.permanent > 0 -> AckSweepResult.PERMANENT_FAILURE
            else -> AckSweepResult.COMPLETE
        }
    }

    // A purchase Play will keep rejecting: report it once per token, then stay quiet. The pass still
    // re-attempts it whenever fresh Play data arrives -- the ack is never skipped, only the noise is.
    private fun reportPermanentAckFailure(purchase: Purchase, error: Exception) {
        if (reportedAckFailures.add(purchase.purchaseToken)) {
            log(TAG, ERROR) { "Permanent ack failure for ${purchase.redacted()}:\n${error.asLog()}" }
            Bugs.report(RuntimeException("Failed to acknowledge purchase", error))
        } else {
            log(TAG, WARN) { "Permanent ack failure (already reported) for ${purchase.redacted()}" }
        }
    }

    // Arms the re-drive timer at most once: further failures while it is pending join the same
    // scheduled pass instead of stacking timers.
    private fun scheduleAckRetry() {
        if (!ackRetryPending.compareAndSet(expect = false, update = true)) {
            log(TAG) { "Ack retry already scheduled" }
            return
        }
        scope.launch {
            delay(ACK_RESCHEDULE_MS)
            ackRetryPending.value = false
            ackRetryTrigger.update { it + 1 }
        }
    }

    // A partial refresh no longer reaches useConnection's dead-binder detection (it returns instead
    // of throwing), so the teardown that used to ride the throw path happens here. Cause chain, not
    // the exception itself: the failure arrives user-friendly-mapped. Deliberately no holder CAS:
    // the failing connection may already have been replaced, and the accepted cost of that rare
    // race is one extra failed action while the loop reconnects.
    private fun invalidateOnDeadConnection(refresh: BillingConnection.PurchaseRefresh) {
        val clientError = refresh.partialError?.let {
            (it as? BillingClientException) ?: (it.cause as? BillingClientException)
        }
        if (clientError != null && clientError.result.responseCode in INVALIDATING_CODES) {
            log(TAG, WARN) { "Refresh reported the connection dead (${clientError.result.responseCode}), invalidating." }
            invalidations.trySend(Unit)
        }
    }

    // Everything a COMPLETED refresh owes the rest of the app, in one place: both the connect loop's
    // initial refresh and manual refresh() calls run through it, so a Restore tap during an outage
    // feeds the same bookkeeping the connect loop does. Only reached when refreshPurchases returned
    // (it still throws when it found nothing AND a query failed — that path is the connect loop's /
    // useConnection's).
    private fun processReconciliation(refresh: BillingConnection.PurchaseRefresh) {
        invalidateOnDeadConnection(refresh)

        if (!refresh.isComplete && !refresh.hasConfirmedProPurchase) {
            // A reconciliation that couldn't confirm Pro. Stamped with the refresh's COMMIT time,
            // never now-at-send: a confirmation that committed between this refresh and the send
            // (e.g. a pending payment completing) must stay NEWER than this failure, or the grace
            // episode it closed would be reopened.
            log(TAG, WARN) { "Partial refresh without a confirmed Pro purchase at ${refresh.occurredAt}" }
            connectionFailuresChannel.trySend(refresh.occurredAt)
        }
    }

    private suspend fun <T> useConnection(action: suspend BillingConnection.() -> T): T {
        // Every caller here is active demand (opening the upgrade screen, restore/buy taps,
        // purchase acks) — cut a pending reconnect backoff short. A no-op while healthy.
        val demandGen = connectionDemand.updateAndGet { it + 1 }
        var used: BillingConnection? = null
        try {
            val connection = connectionHolder.filterNotNull().first().also { used = it }
            return connection.action()
        } catch (e: Exception) {
            // These codes mean the binder is gone. Play doesn't reliably deliver
            // onBillingServiceDisconnected for them, so uninstall the dead connection RIGHT HERE
            // (the loop's teardown takes several dispatches — later callers must not grab the
            // stale holder in that window) and tell the connect loop to clean up and reconnect.
            // The failure may arrive user-friendly-mapped (e.g. GplayServiceUnavailableException
            // from a refresh), so inspect the cause chain, not just the exception itself.
            // CAS + identity check: a fresh replacement must not be killed for its predecessor's
            // failure.
            val clientError = (e as? BillingClientException) ?: (e.cause as? BillingClientException)
            if (clientError != null && clientError.result.responseCode in INVALIDATING_CODES && used != null) {
                if (connectionHolder.compareAndSet(used, null)) {
                    log(TAG, WARN) { "Connection reported dead by action (${clientError.result.responseCode}), invalidating." }
                    invalidations.trySend(Unit)
                }
            }
            throw e
        } finally {
            // Settled on ANY termination — success, error, or the caller's own timeout/cancel: a
            // call that is over is no longer pending demand and must not skip a later backoff.
            servedDemand.update { served -> maxOf(served, demandGen) }
        }
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> = useConnection {
        log(TAG) { "querySkus(): $skus..." }
        querySkus(*skus).also {
            log(TAG) { "querySkus(): $it" }
        }
    }

    suspend fun startIapFlow(activity: Activity, sku: Sku, offer: Sku.Subscription.Offer?) {
        try {
            useConnection {
                launchBillingFlow(activity, sku, offer)
            }
        } catch (e: CancellationException) {
            // Not an error: routing this into Bugs.report (or mapping it) would fake telemetry
            // and break structured cancellation.
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "Failed to start IAP flow:\n${e.asLog()}" }
            // Expected environmental/user situations — user-facing handling only, no bug report.
            // ITEM_ALREADY_OWNED is auto-handled by UpgradeRepoGplay (restore instead of error).
            val ignoredCodes = listOf(
                BillingResponseCode.USER_CANCELED,
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.ERROR,
                BillingResponseCode.ITEM_ALREADY_OWNED,
            )
            when {
                e !is BillingException -> {
                    Bugs.report(RuntimeException("State exception for $sku, U", e))
                }
                e is BillingClientException && !e.result.responseCode.let { ignoredCodes.contains(it) } -> {
                    Bugs.report(RuntimeException("Client exception for $sku", e))
                }
            }

            throw e.tryMapUserFriendly()
        }
    }

    suspend fun refresh(): BillingData {
        log(TAG) { "refresh()" }
        // Query in the caller's context and return the result directly, so callers get the fresh
        // purchases (and any billing error) with a real happens-before instead of racing the
        // shared upgradeInfo replay cache. The freshBillingData emission happens inside the
        // reducer's commit, in commit order — not here.
        val fresh = useConnection { refreshPurchases() }
        processReconciliation(fresh)
        return BillingData.from(fresh.purchases)
    }

    // Strict variant for the pre-purchase gates: unlike refresh(), anything short of a COMPLETE
    // reconciliation throws (user-friendly-mapped) instead of returning what it happened to find —
    // a gate must be able to tell "not owned" apart from "couldn't verify" and fail closed.
    suspend fun refreshStrict(): BillingData {
        log(TAG) { "refreshStrict()" }
        val fresh = useConnection { refreshPurchases() }
        // A gate that hit a dying connection must still trigger the reconnect (the throw below
        // bypasses useConnection's detection, which already returned), or every later purchase
        // check keeps reusing the corpse. No-op on a complete refresh. The episode clock stays out
        // of this path: an aborted gate is not a reconciliation outcome.
        invalidateOnDeadConnection(fresh)
        if (!fresh.isComplete) {
            // partialError is set for every incomplete refresh; the fallback only exists so a
            // future incompleteness without a captured cause still fails closed instead of passing.
            val error = fresh.partialError ?: BillingException("Purchase refresh was incomplete")
            log(TAG, WARN) { "refreshStrict() incomplete: ${error.asLog()}" }
            throw error.tryMapUserFriendly()
        }
        return BillingData.from(fresh.purchases)
    }

    companion object {
        internal fun Throwable.tryMapUserFriendly(): Throwable {
            if (this !is BillingClientException) return this

            return when (result.responseCode) {
                BillingResponseCode.USER_CANCELED -> UserCanceledBillingException(this)
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.SERVICE_TIMEOUT -> GplayServiceUnavailableException(this)
                BillingResponseCode.ERROR -> InternalBillingException(this)
                BillingResponseCode.NETWORK_ERROR -> NetworkBillingException(this)
                BillingResponseCode.ITEM_ALREADY_OWNED -> ItemAlreadyOwnedBillingException(this)
                else -> this
            }
        }

        @Suppress("DEPRECATION")
        private val INVALIDATING_CODES = setOf(
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.SERVICE_TIMEOUT,
        )

        // Ack failures Play won't stop reporting no matter how often we retry: a retry loop would
        // just burn battery and log noise. Everything else (service down, network, timeouts,
        // unmapped codes) is treated as transient.
        private val PERMANENT_ACK_CODES = setOf(
            BillingResponseCode.DEVELOPER_ERROR,
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.ITEM_NOT_OWNED,
        )

        // Play auto-refunds purchases not acknowledged within 3 days; every safety-net deadline
        // derives from this.
        const val ACK_SAFETY_NET_DEADLINE_MS = 3 * 24 * 60 * 60 * 1000L

        private const val INITIAL_REFRESH_TIMEOUT_MS = 30_000L
        private const val MAX_BACKOFF_MS = 300_000L

        // Inline attempts per purchase and their backoff (3s, 6s): covers a short Play hiccup
        // without leaving an unacknowledged purchase near its 3-day auto-refund deadline.
        private const val ACK_MAX_ATTEMPTS = 3
        private const val ACK_RETRY_DELAY_MS = 3_000L
        // Whole-pass re-drive after the inline attempts couldn't finish: the purchase list itself is
        // deduped, so this timer is what keeps a failed ack alive across a longer outage.
        private const val ACK_RESCHEDULE_MS = 300_000L
        // Bounds the (otherwise unbounded) connection wait plus ack round-trip of one attempt.
        private const val ACK_CONNECTION_TIMEOUT_MS = 30_000L
        // Restart delay for the ack collector itself and for the hot shared sources it feeds on.
        private const val ACK_CHAIN_RETRY_MS = 60_000L
        private const val SHARE_RETRY_MS = 60_000L

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Manager")
    }
}
