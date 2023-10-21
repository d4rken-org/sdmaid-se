package eu.darken.sdmse.common.upgrade.core.billing.client

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager.Companion.tryMapUserFriendly
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class BillingConnection(
    private val client: BillingClient,
    val purchaseEvents: Flow<Pair<BillingResult, Collection<Purchase>?>?>,
) {

    private val queryCacheIaps = MutableStateFlow<Collection<Purchase>?>(null)
    private val queryCacheSubs = MutableStateFlow<Collection<Purchase>?>(null)

    val purchases: Flow<Collection<Purchase>> = combine(
        purchaseEvents,
        queryCacheIaps.filterNotNull(),
        queryCacheSubs.filterNotNull(),
    ) { purchaseEvent, iapCache, subCache ->
        val combined = mutableSetOf<Purchase>()

        combined.addAll(iapCache)
        combined.addAll(subCache)

        purchaseEvent
            ?.takeIf { (result, _) -> result.isSuccess }
            ?.let { (_, purchases) -> purchases?.filter { it.purchaseState == PurchaseState.PURCHASED } }
            ?.let { combined.addAll(it) }

        combined.sortedByDescending { it.purchaseTime }
    }.setupCommonEventHandlers(TAG) { "purchases" }

    private suspend fun queryPurchases(@BillingClient.ProductType type: String): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder().apply {
            setProductType(type)
        }.build()
        val (billingResult, purchaseData) = client.queryPurchasesAsync(params)

        log(TAG) {
            "queryPurchases($type): code=${billingResult.isSuccess}, message=${billingResult.debugMessage}, purchaseData=${purchaseData}"
        }

        if (!billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw BillingClientException(billingResult)
        }

        return purchaseData
    }

    suspend fun refreshPurchases() = coroutineScope {
        log(TAG) { "refreshPurchases()" }
        val iapJob = async {
            try {
                val iaps = queryPurchases(BillingClient.ProductType.INAPP)
                log(TAG) { "Refreshed IAPs: $iaps" }
                queryCacheIaps.value = iaps.filter { it.purchaseState == PurchaseState.PURCHASED }
            } catch (e: Exception) {
                throw e.tryMapUserFriendly()
            }
        }
        val subJob = async {
            try {
                val subs = queryPurchases(BillingClient.ProductType.SUBS)
                log(TAG) { "Refreshed SUBs: $subs" }
                queryCacheSubs.value = subs.filter { it.purchaseState == PurchaseState.PURCHASED }
            } catch (e: Exception) {
                throw e.tryMapUserFriendly()
            }
        }
        awaitAll(iapJob, subJob)
    }

    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val ackResult = suspendCoroutine<BillingResult> { continuation ->
            client.acknowledgePurchase(ack) { continuation.resume(it) }
        }
        log(TAG) {
            "acknowledgePurchase(purchase=$purchase): code=${ackResult.responseCode}, message=${ackResult.debugMessage})"
        }

        if (!ackResult.isSuccess) {
            throw BillingClientException(ackResult)
        }
        return ackResult
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> {
        val productList = skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder().apply {
                setProductId(sku.id)
                setProductType(
                    when (sku.type) {
                        Sku.Type.IAP -> BillingClient.ProductType.INAPP
                        Sku.Type.SUBSCRIPTION -> BillingClient.ProductType.SUBS
                    }
                )
            }.build()
        }

        val params = QueryProductDetailsParams.newBuilder().apply {
            setProductList(productList)
        }.build()

        val (result, details) = suspendCoroutine<Pair<BillingResult, Collection<ProductDetails>?>> { continuation ->
            client.queryProductDetailsAsync(params) { result: BillingResult, details: Collection<ProductDetails> ->
                continuation.resume(result to details)
            }
        }

        log(TAG) {
            "querySkus(skus=$skus): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }

        if (!result.isSuccess) throw BillingClientException(result)

        if (details.isNullOrEmpty()) throw IllegalStateException("Unknown SKU, no details available.")

        return details
            .groupBy { it.productId }
            .mapNotNull { (key, details) ->
                val sku = skus
                    .single { it.id == key }
                val detail = details.single { it.productId == sku.id }

                SkuDetails(sku, detail)
            }
    }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku, targetOffer: Sku.Subscription.Offer?): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku)" }
        if (sku.type == Sku.Type.SUBSCRIPTION) {
            requireNotNull(targetOffer) { "SUB skus require a target offer" }
        }

        val data = querySkus(sku).single { it.sku == sku }

        val params = BillingFlowParams.newBuilder().apply {
            val productDetail = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                setProductDetails(data.details)
                if (sku is Sku.Subscription && targetOffer != null) {
                    val offer = data.details.subscriptionOfferDetails!!.single {
                        targetOffer.matches(it)
                    }
                    setOfferToken(offer.offerToken)
                }
            }.build()
            setProductDetailsParamsList(listOf(productDetail))
        }.build()

        return client.launchBillingFlow(activity, params)
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")
    }
}