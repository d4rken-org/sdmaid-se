package eu.darken.sdmse.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
) {

    // Fresh Play data plus its provenance: a query result covers owned products of the queried
    // types, while a purchase event only carries the products of that transaction — consumers
    // deciding between per-SKU behaviors (like grace windows) need to know the difference.
    data class FreshData(
        val data: BillingData,
        val isFullSnapshot: Boolean,
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

    // First real billing outcome after process start, success OR failure. The connect loop
    // swallows connection errors (it retries forever), so downstream flows just stay quiet during
    // an outage — gates that used to observe a propagated error need this explicit signal.
    private val settledOnce = MutableStateFlow(false)
    val isSettled: Flow<Boolean> = settledOnce

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
                                // isSettled forever with no retry. withTimeoutOrNull, NOT
                                // withTimeout: TimeoutCancellationException is a
                                // CancellationException and would kill this loop.
                                withTimeoutOrNull(INITIAL_REFRESH_TIMEOUT_MS) {
                                    connection.refreshPurchases()
                                } ?: throw BillingException("Initial purchase refresh timed out")

                                failStreak = 0
                                settledOnce.value = true
                                connectionHolder.value = connection
                                log(TAG, INFO) { "Billing connection established" }
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
                }
                connectionHolder.value = null
                settledOnce.value = true
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

    private val purchases = connectionHolder
        // NOT filterNotNull(): the null emission is what detaches a dead connection's inner flows.
        .flatMapLatest { it?.purchases ?: emptyFlow() }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "purchases" }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val billingData: Flow<BillingData> = purchases
        .map { BillingData(purchases = it) }
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
        .flatMapLatest { it?.freshUpdates ?: emptyFlow() }
        .map { FreshData(data = BillingData(purchases = it.purchases), isFullSnapshot = it.isFullSnapshot) }
        .setupCommonEventHandlers(TAG) { "freshBillingData" }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    init {
        purchases
            .onEach { purchases ->
                purchases
                    .filter {
                        val needsAck = !it.isAcknowledged

                        if (needsAck) log(TAG, INFO) { "Needs ACK: $it" }
                        else log(TAG) { "Already ACK'ed: $it" }

                        needsAck
                    }
                    .forEach {
                        log(TAG, INFO) { "Acknowledging purchase: $it" }

                        try {
                            useConnection {
                                acknowledgePurchase(it)
                            }
                        } catch (e: CancellationException) {
                            // AppScope death is not an acknowledgement failure.
                            throw e
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Failed to acknowledge purchase: $it\n${e.asLog()}" }
                        }
                    }
            }
            .setupCommonEventHandlers(TAG) { "connection-acks" }
            .retryWhen { cause, attempt ->
                if (cause is CancellationException) {
                    log(TAG) { "Ack was cancelled (appScope?) cancelled." }
                    return@retryWhen false
                }
                if (attempt > 5) {
                    log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                    return@retryWhen false
                }
                if (cause !is BillingException) {
                    log(TAG, WARN) { "Unknown BillingClient exception type: $cause" }
                    return@retryWhen false
                } else {
                    log(TAG) { "BillingClient exception: $cause" }
                }

                if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                    log(TAG) { "Got BILLING_UNAVAILABLE while trying to ACK purchase." }
                    return@retryWhen false
                }

                log(TAG) { "Will retry ACK" }
                delay(3000 * attempt)
                true
            }
            .launchIn(scope)
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
        return BillingData(purchases = fresh.purchases)
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

        private const val INITIAL_REFRESH_TIMEOUT_MS = 30_000L
        private const val MAX_BACKOFF_MS = 300_000L

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Manager")
    }
}
