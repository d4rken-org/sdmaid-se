package eu.darken.sdmse.common.upgrade.core.client

import android.content.Context
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.newBuilder
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class BillingClientConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val connectionProvider: Flow<BillingClientConnection> = callbackFlow {
        val purchasePublisher = MutableStateFlow<Collection<Purchase>>(emptySet())

        val client = newBuilder(context).apply {
            enablePendingPurchases()
            setListener { result, purchases ->
                if (result.isSuccess) {
                    log(TAG) {
                        "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                    purchasePublisher.value = purchases.orEmpty()
                } else {
                    log(TAG, WARN) {
                        "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
                    }
                }
            }
        }.build()

        val connectionResult = suspendCoroutine<BillingResult> { continuation ->
            log(TAG, VERBOSE) { "startConnection(...)" }
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    log(TAG, VERBOSE) {
                        "onBillingSetupFinished(code=${result.responseCode}, message=${result.debugMessage})"
                    }
                    continuation.resume(result)
                }

                override fun onBillingServiceDisconnected() {
                    log(TAG, VERBOSE) { "onBillingServiceDisconnected() " }
                    close(CancellationException("Billing service disconnected"))
                }
            })
        }

        val billingClientConnection = when (connectionResult.responseCode) {
            BillingResponseCode.OK -> BillingClientConnection(client, purchasePublisher)
            else -> throw BillingClientException(connectionResult)
        }

        try {
            purchasePublisher.value = billingClientConnection.queryPurchases()
            log(TAG) { "Initial IAP query successful." }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Initial IAP query failed:\n${e.asLog()}" }
        }

        send(billingClientConnection)

        log(TAG) { "Awaiting close." }
        awaitClose {
            log(TAG) { "Stopping billing client connection" }
            client.endConnection()
        }
    }

    val connection: Flow<BillingClientConnection> = connectionProvider
        .setupCommonEventHandlers(TAG) { "connection" }
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) {
                log(TAG) { "BillingClient connection cancelled." }
                return@retryWhen false
            }
            if (attempt > 5) {
                log(TAG, WARN) { "Reached attempt limit: $attempt due to $cause" }
                return@retryWhen false
            }
            if (cause !is BillingClientException) {
                log(TAG, WARN) { "Unknown BillingClient exception type: $cause" }
                return@retryWhen false
            } else {
                log(TAG) { "BillingClient exception: $cause; ${cause.result}" }
            }

            if (cause.result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) {
                log(TAG) { "Got BILLING_UNAVAILABLE while trying to connect client." }
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