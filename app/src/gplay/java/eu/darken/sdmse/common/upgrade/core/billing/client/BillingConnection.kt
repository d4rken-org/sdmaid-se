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
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager.Companion.tryMapUserFriendly
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BillingConnection(
    private val client: BillingClient,
    private val skuTypeOf: (String) -> Sku.Type? = DEFAULT_SKU_TYPE_RESOLVER,
) {

    // A purchase proven by an onPurchasesUpdated success event. Additive only: events prove
    // ownership, never absence. `gen` orders it against queries (a query that STARTED before this
    // event must not clear it); `type` is resolved at ingestion so a later per-type query that
    // confirms absence can supersede it (null = product unknown to this app, only a complete
    // refresh may clear it).
    data class OverlayEntry(
        val purchase: Purchase,
        val gen: Long,
        val type: Sku.Type?,
    )

    // The single, atomically-updated ownership state of this connection. Split state (per-type
    // caches, separate event flows) exposed intermediate combinations and starved on partial
    // failures — every mutation here is a pure copy applied under `reducerLock`, so observers only
    // ever see committed states and refreshPurchases() can return the exact state it committed.
    data class ReducerState(
        val iapSnapshot: Collection<Purchase>? = null,
        val subSnapshot: Collection<Purchase>? = null,
        val overlay: List<OverlayEntry> = emptyList(),
        val eventGen: Long = 0L,
    ) {

        internal fun withEvent(
            purchased: Collection<Purchase>,
            typeOf: (String) -> Sku.Type?,
        ): ReducerState {
            val gen = eventGen + 1
            val entries = purchased.map { purchase ->
                OverlayEntry(
                    purchase = purchase,
                    gen = gen,
                    type = purchase.products.firstNotNullOfOrNull(typeOf),
                )
            }
            return copy(eventGen = gen, overlay = overlay + entries)
        }

        internal fun withQueryResults(
            iap: Collection<Purchase>?,
            sub: Collection<Purchase>?,
            genAtQueryStart: Long,
        ): ReducerState {
            val clearedTypes = setOfNotNull(
                Sku.Type.IAP.takeIf { iap != null },
                Sku.Type.SUBSCRIPTION.takeIf { sub != null },
            )
            val isComplete = clearedTypes.size == 2
            return copy(
                iapSnapshot = iap ?: iapSnapshot,
                subSnapshot = sub ?: subSnapshot,
                // A successful per-type query is authoritative for that type: overlay entries it
                // could have seen (gen <= start) are superseded by its result. Entries of a FAILED
                // type survive, as do events that arrived after the query started. Untyped entries
                // (unknown product) only fall to a complete refresh.
                overlay = overlay.filterNot { entry ->
                    entry.gen <= genAtQueryStart &&
                        (entry.type in clearedTypes || (isComplete && entry.type == null))
                },
            )
        }

        // Never verified anything this connection: downstream must not mistake "don't know yet"
        // for "owns nothing".
        internal val isSettled: Boolean
            get() = iapSnapshot != null || subSnapshot != null

        // Snapshots first, overlay overwrites: a surviving overlay entry is by construction newer
        // than the last successful query of its type (older ones were cleared), so its purchase
        // data (ack state etc.) is fresher. purchaseToken is the real purchase identity — Purchase
        // has no equals(), object-identity dedup would keep duplicates.
        internal fun merged(): Collection<Purchase> {
            val byToken = LinkedHashMap<String, Purchase>()
            iapSnapshot.orEmpty().forEach { byToken[it.purchaseToken] = it }
            subSnapshot.orEmpty().forEach { byToken[it.purchaseToken] = it }
            overlay.forEach { byToken[it.purchase.purchaseToken] = it.purchase }
            return byToken.values.sortedByDescending { it.purchaseTime }
        }
    }

    // Fresh data straight from a Play round-trip, in COMMIT ORDER: emitted under the same lock
    // that mutates the reducer state, so a consumer can never observe a purchase event AFTER the
    // query commit that superseded it (or a stale snapshot after a newer event). Query emissions
    // carry only what the queries confirmed — never retained stale data — because consumers use
    // this for time-based bookkeeping like the Pro grace period.
    data class FreshUpdate(
        val purchases: Collection<Purchase>,
        val isFullSnapshot: Boolean,
    )

    // Guards state mutation + fresh emission as one atomic step. Kept a plain monitor (not a
    // Mutex): the listener path is synchronous on Play's callback thread.
    private val reducerLock = Any()
    private val state = MutableStateFlow(ReducerState())
    // UNLIMITED: event volume is tiny and a silently dropped item would lose a grace stamp or an
    // already-owned recovery. Closed by the provider when the connection dies.
    private val freshUpdatesChannel = Channel<FreshUpdate>(Channel.UNLIMITED)
    private val failureChannel = Channel<BillingResult>(Channel.UNLIMITED)

    val freshUpdates: Flow<FreshUpdate> = freshUpdatesChannel.receiveAsFlow()

    // Non-OK results from onPurchasesUpdated (e.g. async ITEM_ALREADY_OWNED after the Play sheet
    // opened). A channel, not state: events must not conflate, and a late subscriber must not be
    // served a stale failure. Consumed by a single persistent collector chain.
    val purchaseFailures: Flow<BillingResult> = failureChannel.receiveAsFlow()

    val purchases: Flow<Collection<Purchase>> = state
        .mapNotNull { current -> current.takeIf { it.isSettled }?.merged() }
        .setupCommonEventHandlers(TAG) { "purchases" }

    // Called synchronously from the PurchasesUpdatedListener on Play's callback thread:
    // exactly-once per callback, ordered, and atomic with the fresh emission. Success and failure
    // results stay strictly apart — a failure (reopened sheet -> USER_CANCELED) must not evict a
    // fresh purchase event.
    internal fun onPurchasesUpdated(result: BillingResult, purchases: Collection<Purchase>?) {
        if (result.isSuccess) {
            log(TAG) {
                "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
            }
            // PENDING purchases must never surface as owned (or stamp the Pro grace cache).
            val purchased = purchases.orEmpty().filter { it.purchaseState == PurchaseState.PURCHASED }
            synchronized(reducerLock) {
                state.value = state.value.withEvent(purchased, skuTypeOf)
                if (purchased.isNotEmpty()) {
                    freshUpdatesChannel.trySend(FreshUpdate(purchased, isFullSnapshot = false))
                }
            }
        } else {
            log(TAG, WARN) {
                "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, purchases=$purchases)"
            }
            failureChannel.trySend(result)
        }
    }

    // Called by the provider when this connection ends: completes the event flows so consumers
    // don't wait on a dead connection's channels.
    internal fun close() {
        freshUpdatesChannel.close()
        failureChannel.close()
    }

    // The purchases of a refresh plus whether it covered both product types: a partial result (one
    // query failed) is still authoritative for what it FOUND, but must not be treated as proof of
    // absence for the type that couldn't be checked.
    data class PurchaseRefresh(
        val purchases: Collection<Purchase>,
        val isComplete: Boolean,
    )

    // Serializes concurrent refreshes (manual, background, auto-restore): an older query that got
    // descheduled after Play answered must not commit over a newer one's result.
    private val refreshMutex = Mutex()

    // Queries both product types and commits the result into the reducer state in ONE atomic
    // update, then returns the merged view of exactly that committed state — so the reactive
    // purchases flow and this return value can never disagree. Tolerant of a single product-type
    // failure: found purchases are authoritative, and an error only propagates when nothing was
    // found AND a query failed, so the caller can tell "not owned" apart from "couldn't verify".
    suspend fun refreshPurchases(): PurchaseRefresh = refreshMutex.withLock {
        coroutineScope {
            log(TAG) { "refreshPurchases()" }
            val genAtQueryStart = state.value.eventGen
            val iapJob = async { queryPurchasedProducts(BillingClient.ProductType.INAPP) }
            val subJob = async { queryPurchasedProducts(BillingClient.ProductType.SUBS) }
            val iap = iapJob.await()
            val sub = subJob.await()
            log(TAG) { "Refreshed IAPs=${iap.getOrNull()}, SUBs=${sub.getOrNull()}" }

            // Commit BEFORE the couldn't-verify error check: a successful per-type result is
            // authoritative even when its sibling failed — verified absence (e.g. a refunded IAP)
            // must not be discarded just because the SUB query errored.
            val isComplete = iap.isSuccess && sub.isSuccess
            val committed = synchronized(reducerLock) {
                val next = state.value.withQueryResults(
                    iap = iap.getOrNull(),
                    sub = sub.getOrNull(),
                    genAtQueryStart = genAtQueryStart,
                )
                state.value = next
                if (iap.isSuccess || sub.isSuccess) {
                    // Only what the queries CONFIRMED — retained stale data of a failed type stays
                    // out of the fresh stream (it would keep re-stamping the grace window).
                    val confirmed = (iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty())
                        .sortedByDescending { it.purchaseTime }
                    // A surviving overlay entry (purchase event newer than the query start, or of
                    // a failed type) means this result does NOT prove total absence: it must not
                    // count as a full snapshot, or an empty query racing a fresh purchase event
                    // would start a false unconfirmed-grace episode.
                    val provesAbsence = isComplete && next.overlay.isEmpty()
                    freshUpdatesChannel.trySend(FreshUpdate(confirmed, isFullSnapshot = provesAbsence))
                }
                next
            }

            // Throws when nothing was found and a query failed, so the caller can tell "not
            // owned" apart from "couldn't verify".
            combinePurchaseResults(iap, sub)

            PurchaseRefresh(
                purchases = committed.merged(),
                isComplete = isComplete,
            )
        }
    }

    // Never throws except on cancellation, so a single failing product-type query doesn't cancel
    // the sibling query (or the coroutineScope). The exception is already user-friendly-mapped.
    private suspend fun queryPurchasedProducts(
        @BillingClient.ProductType type: String,
    ): Result<Collection<Purchase>> = try {
        Result.success(queryPurchases(type).filter { it.purchaseState == PurchaseState.PURCHASED })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.tryMapUserFriendly())
    }

    private suspend fun queryPurchases(@BillingClient.ProductType type: String): Collection<Purchase> {
        val params = QueryPurchasesParams.newBuilder().apply {
            setProductType(type)
        }.build()
        // Own cancellable wrapper instead of the billing-ktx extension: a non-cancellable
        // suspension would make the timeouts around refreshes hang until Play's callback fires.
        // The onCancellation overload makes a callback racing the cancellation a no-op instead of
        // an IllegalStateException on Play's thread.
        val (billingResult, purchaseData) = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (continuation.isActive) continuation.resume(result to purchases) { _, _, _ -> }
            }
        }

        log(TAG) {
            "queryPurchases($type): code=${billingResult.isSuccess}, message=${billingResult.debugMessage}, purchaseData=${purchaseData}"
        }

        if (!billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw BillingClientException(billingResult)
        }

        return purchaseData
    }

    // Strict SUBS-only query for the pre-purchase subscription gate: unlike refreshPurchases(),
    // a failure propagates (no cross-type tolerance) — callers must be able to fail closed on
    // "couldn't verify". Commits through the reducer like any query, so the reactive purchases
    // flow picks up the fresh renewal state, and emits a partial fresh update: it proves what the
    // SUBS query found, never the absence of anything it didn't cover.
    suspend fun querySubscriptions(): Collection<Purchase> = refreshMutex.withLock {
        log(TAG) { "querySubscriptions()" }
        val genAtQueryStart = state.value.eventGen
        val subs = queryPurchases(BillingClient.ProductType.SUBS)
            .filter { it.purchaseState == PurchaseState.PURCHASED }
        val committed = synchronized(reducerLock) {
            val next = state.value.withQueryResults(
                iap = null,
                sub = subs,
                genAtQueryStart = genAtQueryStart,
            )
            state.value = next
            freshUpdatesChannel.trySend(FreshUpdate(subs, isFullSnapshot = false))
            next
        }
        // The COMMITTED view, not the raw response: a purchase event that arrived after the query
        // started survives the commit as a newer overlay and must reach the gate too — otherwise a
        // just-purchased renewing sub could slip past the fail-closed double-billing check.
        // Non-IAP overlays only; untyped (unknown product) entries stay in on the safe side.
        val byToken = LinkedHashMap<String, Purchase>()
        subs.forEach { byToken[it.purchaseToken] = it }
        committed.overlay
            .filter { it.type != Sku.Type.IAP }
            .forEach { byToken[it.purchase.purchaseToken] = it.purchase }
        byToken.values.sortedByDescending { it.purchaseTime }
    }
    suspend fun acknowledgePurchase(purchase: Purchase): BillingResult {
        val ack = AcknowledgePurchaseParams.newBuilder().apply {
            setPurchaseToken(purchase.purchaseToken)
        }.build()

        val ackResult = suspendCancellableCoroutine<BillingResult> { continuation ->
            client.acknowledgePurchase(ack) {
                if (continuation.isActive) continuation.resume(it) { _, _, _ -> }
            }
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
        log(TAG) { "querySkus(skus=${skus.joinToString { it.print() }})..." }
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

        // Cancellable so the ViewModel's query timeout and flatMapLatest-based retry actually work:
        // with suspendCoroutine a missing Play callback kept the timeout suspended indefinitely.
        val (result, details) = suspendCancellableCoroutine<Pair<BillingResult, Collection<ProductDetails>?>> { continuation ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (continuation.isActive) {
                    continuation.resume(result to queryResult.productDetailsList) { _, _, _ -> }
                }
            }
        }

        log(TAG) {
            "querySkus(skus=${skus.joinToString { it.print() }}): code=${result.responseCode}, debug=${result.debugMessage}), skuDetails=$details"
        }

        if (!result.isSuccess) throw BillingClientException(result)

        if (details.isNullOrEmpty()) {
            throw IllegalStateException("No details available for ${skus.joinToString { "${it.type}-${it.id}" }}")
        }

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

        // launchBillingFlow must run on the main thread (documented BillingClient contract), and its
        // RETURNED result reports whether the flow could be launched at all (DEVELOPER_ERROR,
        // ITEM_ALREADY_OWNED, BILLING_UNAVAILABLE, ...) — failures arrive here, not as exceptions.
        // Throw like the other client calls do, so callers can surface them instead of silence.
        val result = withContext(Dispatchers.Main) {
            client.launchBillingFlow(activity, params)
        }
        log(TAG) {
            "launchBillingFlow(sku=$sku): code=${result.responseCode}, message=${result.debugMessage}"
        }
        if (!result.isSuccess) throw BillingClientException(result)

        return result
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")

        // Classifies event purchases by product type at ingestion, so a later per-type query can
        // authoritatively supersede them. Unknown products stay untyped (cleared only by a
        // complete refresh).
        internal val DEFAULT_SKU_TYPE_RESOLVER: (String) -> Sku.Type? = { productId ->
            OurSku.PRO_SKUS.singleOrNull { it.id == productId }?.type
        }

        // Combines the two product-type query results: a purchase found by either type is
        // authoritative; an error is only propagated when nothing was found, so callers can tell
        // "not owned" apart from "couldn't verify one product type". Treating any found purchase
        // as authoritative is safe because every product this app sells is a Pro SKU (see
        // OurSku.PRO_SKUS). Pure and unit-tested.
        internal fun combinePurchaseResults(
            iap: Result<Collection<Purchase>>,
            sub: Result<Collection<Purchase>>,
        ): Collection<Purchase> {
            val found = iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty()
            return when {
                found.isNotEmpty() -> found.sortedByDescending { it.purchaseTime }
                else -> {
                    (iap.exceptionOrNull() ?: sub.exceptionOrNull())?.let { throw it }
                    emptyList()
                }
            }
        }
    }
}
