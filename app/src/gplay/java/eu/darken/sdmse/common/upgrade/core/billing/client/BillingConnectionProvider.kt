package eu.darken.sdmse.common.upgrade.core.billing.client

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.billing.BillingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val provider: Flow<BillingConnection> = callbackFlow {
        val purchaseEvents = MutableStateFlow<Pair<BillingResult, Collection<Purchase>?>?>(null)

        val client = BillingClient.newBuilder(context).apply {
            enablePendingPurchases()
            setListener { result, purchases ->
                if (result.isSuccess) {
                    log(TAG) {
                        "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    purchaseEvents.value = result to purchases
                } else {
                    log(TAG, WARN) {
                        "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                }
            }
        }.build()

        log(TAG, VERBOSE) { "startConnection(...)" }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                log(TAG, VERBOSE) {
                    "onBillingSetupFinished(code=${result.responseCode}, message=${result.debugMessage})"
                }

                when (result.responseCode) {
                    BillingResponseCode.OK -> {
                        val connection = BillingConnection(client, purchaseEvents)
                        trySendBlocking(connection)
                    }
                    else -> {
                        close(BillingClientException(result))
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                log(TAG) { "onBillingServiceDisconnected() " }
                close(BillingException("Billing service disconnected"))
            }
        })

        log(TAG) { "Awaiting close." }
        awaitClose {
            try {
                log(TAG) { "Stopping billing client connection" }
                client.endConnection()
            } catch (e: Exception) {
                log(TAG, WARN) { "Couldn't end billing client connection: ${e.asLog()}" }
            }
        }
    }

    val connection: Flow<BillingConnection> = provider
        .setupCommonEventHandlers(TAG) { "provider" }
        .retryWhen { cause, attempt ->
            log(TAG) { "Billing client connection error: ${cause.asLog()}" }

            if (cause is CancellationException) {
                log(TAG) { "BillingClient connection cancelled." }
                return@retryWhen false
            }

            if (cause !is BillingException) {
                log(TAG, WARN) { "Unknown exception type: $cause" }
                return@retryWhen false
            }

            if (cause is BillingClientException && cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                log(TAG) { "Got BILLING_UNAVAILABLE while trying to connect client." }
                return@retryWhen false
            }

            if (attempt > 5) {
                log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                return@retryWhen false
            }

            log(TAG) { "Will retry BillingClient connection... *sigh*" }
            delay(3000 * attempt)
            true
        }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Client", "ConnectionProvider")
    }
}