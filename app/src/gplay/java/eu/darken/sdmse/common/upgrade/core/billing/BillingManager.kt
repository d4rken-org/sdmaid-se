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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    connectionProvider: BillingConnectionProvider,
) {

    // Wakes a pending connection-retry backoff early. Zero replay: kicks fired while no retry is
    // waiting are dropped, so a healthy connection can't accumulate stale wake-ups.
    private val connectionKick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val connection = connectionProvider.connection
        .onEach {
            try {
                it.refreshPurchases()
            } catch (e: Exception) {
                log(TAG, ERROR) { "Initial purchase data refresh failed: ${e.asLog()}" }
            }
        }
        .retryWhen { cause, attempt ->
            // Never give up terminally: the ack collector pins this shareIn forever, so WhileSubscribed
            // can't restart a completed upstream — a swallowed terminal failure (e.g. one transient
            // BILLING_UNAVAILABLE while Play updates itself at boot) would leave billing dead until
            // process restart. Retry with capped backoff instead; Play recovering makes this heal.
            if (cause is CancellationException) {
                false
            } else {
                log(TAG, WARN) { "Billing connection failed (attempt=$attempt), will retry: ${cause.asLog()}" }
                val backoff = (30_000L * (attempt + 1)).coerceAtMost(300_000L)
                // Interruptible backoff: an explicit billing action (restore tap, app-open refresh)
                // wakes the retry immediately — the user may have just fixed Play, don't make them
                // wait out up to 5 minutes for us to notice.
                withTimeoutOrNull(backoff) { connectionKick.first() }
                true
            }
        }
        .setupCommonEventHandlers(TAG) { "connection" }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    private val purchases = connection
        .flatMapLatest { it.purchases }
        .distinctUntilChanged()
        .setupCommonEventHandlers(TAG) { "purchases" }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val billingData: Flow<BillingData> = purchases
        .map { BillingData(purchases = it) }
        .shareIn(scope, WhileSubscribed(3000L, 0L), replay = 1)

    val purchaseFailures: Flow<BillingResult> = connection
        .flatMapLatest { it.purchaseFailures }
        .setupCommonEventHandlers(TAG) { "purchaseFailures" }

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
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "Failed to ancknowledge purchase: $it\n${e.asLog()}" }
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
        // Every explicit billing operation counts as a user-driven "try again NOW".
        connectionKick.tryEmit(Unit)
        return connection
            .map { action(it) }
            .take(1)
            .single()
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
        // purchases (and any billing error) with a real happens-before instead of racing the shared
        // upgradeInfo replay cache.
        return BillingData(purchases = useConnection { refreshPurchases() })
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

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Manager")
    }
}
