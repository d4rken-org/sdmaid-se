package eu.darken.sdmse.common.upgrade.core.billing.client

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.billing.BillingException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// A single connection attempt: emits one BillingConnection, stays open for its lifetime, and
// closes with the exception on setup failure or disconnect. NO retry here — BillingManager's
// connect loop owns all retry policy, so every wait stays interruptible by user demand (the old
// nested retryWhen added up to ~30s of demand-blind delays before the manager ever saw a failure).
@Singleton
class BillingConnectionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val connection: Flow<BillingConnection> = callbackFlow {
        // The listener must exist before the client, the connection needs the client: bridge via
        // this reference. onPurchasesUpdated can only fire for an ACTIVE connection, i.e. after
        // setup finished and the reference was set — a null here would be a Play contract breach,
        // and dropping such an event is the only sane response.
        var connectionRef: BillingConnection? = null

        val client = BillingClient.newBuilder(context).apply {
            enablePendingPurchases(
                PendingPurchasesParams.newBuilder().apply {
                    enableOneTimeProducts()
                }.build()
            )
            setListener { result, purchases ->
                connectionRef?.onPurchasesUpdated(result, purchases)
                    ?: log(TAG, WARN) { "onPurchasesUpdated(code=${result.responseCode}) before setup finished?!" }
            }
        }.build()

        // A never-answering Play (no setup callback at all) must fail this attempt into the
        // manager's backoff instead of hanging it outside any timeout.
        val setupTimeout = launch {
            delay(SETUP_TIMEOUT_MS)
            close(BillingException("Billing client setup timed out"))
        }

        log(TAG, VERBOSE) { "startConnection(...)" }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                setupTimeout.cancel()
                log(TAG, VERBOSE) {
                    "onBillingSetupFinished(code=${result.responseCode}, message=${result.debugMessage})"
                }

                when (result.responseCode) {
                    BillingResponseCode.OK -> {
                        val connection = BillingConnection(client)
                        connectionRef = connection
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
                // Complete the event channels first so consumers stop waiting on a dead connection.
                connectionRef?.close()
                client.endConnection()
            } catch (e: Exception) {
                log(TAG, WARN) { "Couldn't end billing client connection: ${e.asLog()}" }
            }
        }
    }.setupCommonEventHandlers(TAG) { "provider" }

    companion object {
        private const val SETUP_TIMEOUT_MS = 30_000L

        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "Client", "ConnectionProvider")
    }
}
