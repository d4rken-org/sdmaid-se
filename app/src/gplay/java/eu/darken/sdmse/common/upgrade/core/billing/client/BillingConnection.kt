package eu.darken.sdmse.common.upgrade.core.billing.client

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.flow.setupCommonEventHandlers
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager.Companion.tryMapUserFriendly
import eu.darken.sdmse.common.upgrade.core.billing.OfferUnavailableBillingException
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

    // A purchase (owned or with a pending payment) proven by an onPurchasesUpdated success event.
    // Additive only: events prove existence, never absence. `gen` orders it against queries (a
    // query that STARTED before this event must not clear it); `type` is resolved at ingestion so a
    // later per-type query that confirms absence can supersede it (null = product unknown to this
    // app, only a complete refresh may clear it).
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
            relevant: Collection<Purchase>,
            typeOf: (String) -> Sku.Type?,
        ): ReducerState {
            val gen = eventGen + 1
            val entries = relevant.map { purchase ->
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
        // data (ack state etc.) is fresher. Dedup by purchaseToken (the stable purchase identity):
        // snapshot and overlay instances of the same purchase can differ in ack state, so Purchase
        // equality or object identity would retain both instead of letting the newer one win.
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
        // Wall-clock time this update was COMMITTED under reducerLock — i.e. when Play actually
        // confirmed this data. Defaults to construction time, which at every production call site is
        // the commit instant (this type is only ever built inside the reducer commit below). The Pro
        // entitlement layer stamps its grace anchor with this so a confirmation and a later
        // connection failure are ordered by when they HAPPENED, not by when each separate flow got
        // around to processing them — see UpgradeRepoGplay.recordProState / BillingCache
        // .stampLastProState.
        val occurredAt: Long = System.currentTimeMillis(),
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
                "onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, " +
                    "purchases=${purchases?.redacted()})"
            }
            // The reducer carries PENDING purchases too (the UI must be able to show a payment in
            // progress), but the fresh stream stays PURCHASED-only: it feeds the entitlement and
            // grace bookkeeping, which must never see a payment Play hasn't completed.
            val relevant = purchases.orEmpty().filter { it.isRelevant }
            val purchased = relevant.filter { it.isPurchased }
            synchronized(reducerLock) {
                state.value = state.value.withEvent(relevant, skuTypeOf)
                if (purchased.isNotEmpty()) {
                    freshUpdatesChannel.trySend(FreshUpdate(purchased, isFullSnapshot = false))
                }
            }
        } else {
            log(TAG, WARN) {
                "error: onPurchasesUpdated(code=${result.responseCode}, message=${result.debugMessage}, " +
                    "purchases=${purchases?.redacted()})"
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

    // The full outcome of a refresh: what it committed, what it actually CONFIRMED, and how far it
    // got. A partial result (one query failed) is still authoritative for what it found, but must
    // not be treated as proof of absence for the type that couldn't be checked — so callers that
    // need to fail closed, or to feed the grace episode clock, get the provenance instead of having
    // to infer it from the merged view.
    data class PurchaseRefresh(
        // The committed reducer state (queries merged with retained snapshots and surviving
        // events) — the same view the reactive purchases flow emits.
        val purchases: Collection<Purchase>,
        // ONLY what these queries returned: never retained state of a failed type, so a consumer
        // can tell "Play said so just now" from "we still remember this".
        val confirmed: Collection<Purchase> = emptyList(),
        // A confirmed PURCHASED purchase of a product this app knows. Fail-safe default: "we
        // couldn't confirm Pro" is the direction that keeps the grace bookkeeping honest.
        val hasConfirmedProPurchase: Boolean = false,
        val isComplete: Boolean,
        // Commit time under reducerLock — the same instant stamped on this refresh's FreshUpdate,
        // so a confirmation and a later failure signal are ordered by when they HAPPENED.
        val occurredAt: Long = System.currentTimeMillis(),
        // Why the refresh is incomplete (the failed type's already user-friendly-mapped
        // exception), null when complete. Carried rather than thrown: a partial refresh that found
        // something is still useful, only the caller can decide whether partial is good enough.
        val partialError: Throwable? = null,
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
            val iapJob = async { queryRelevantProducts(BillingClient.ProductType.INAPP) }
            val subJob = async { queryRelevantProducts(BillingClient.ProductType.SUBS) }
            val iap = iapJob.await()
            val sub = subJob.await()
            log(TAG) { "Refreshed IAPs=${iap.getOrNull()?.redacted()}, SUBs=${sub.getOrNull()?.redacted()}" }

            // Commit BEFORE the couldn't-verify error check: a successful per-type result is
            // authoritative even when its sibling failed — verified absence (e.g. a refunded IAP)
            // must not be discarded just because the SUB query errored.
            val isComplete = iap.isSuccess && sub.isSuccess
            // Only what the queries CONFIRMED as owned — retained stale data of a failed type, and
            // pending payments, stay out (both would keep re-stamping the grace window).
            val confirmed = (iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty())
                .filter { it.isPurchased }
                .sortedByDescending { it.purchaseTime }
            var committedAt = 0L
            val committed = synchronized(reducerLock) {
                val next = state.value.withQueryResults(
                    iap = iap.getOrNull(),
                    sub = sub.getOrNull(),
                    genAtQueryStart = genAtQueryStart,
                )
                state.value = next
                committedAt = System.currentTimeMillis()
                if (iap.isSuccess || sub.isSuccess) {
                    // A surviving OWNED overlay entry (purchase event newer than the query start,
                    // or of a failed type) means this result does NOT prove total absence: it must
                    // not count as a full snapshot, or an empty query racing a fresh purchase event
                    // would start a false unconfirmed-grace episode. A surviving PENDING entry
                    // proves nothing about ownership, so it must not suppress the bookkeeping
                    // either — a payment in progress would otherwise freeze the episode clock.
                    val provesAbsence = isComplete && next.overlay.none { it.purchase.isPurchased }
                    freshUpdatesChannel.trySend(
                        FreshUpdate(confirmed, isFullSnapshot = provesAbsence, occurredAt = committedAt)
                    )
                }
                next
            }

            // Support-log anchor, at INFO because purchase complaints arrive as debug recordings.
            // Logs what these queries CONFIRMED, kept distinct from the committed view: merged()
            // retains a failed type's previous purchases, so reporting it as "what Play returned"
            // would be the same false-certainty trap the copy elsewhere had to fix. Pending ids are
            // listed separately — "bought it, still not Pro" reports are exactly this state.
            // Product IDs only -- never the Purchase, which carries order and token data.
            log(TAG, INFO) {
                val returned = iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty()
                val confirmedIds = returned.filter { it.isPurchased }.flatMap { it.products }
                val pendingIds = returned.filterNot { it.isPurchased }.flatMap { it.products }
                "refreshPurchases(): confirmed=$confirmedIds, pending=$pendingIds, isComplete=$isComplete, " +
                    "iapOk=${iap.isSuccess}, subOk=${sub.isSuccess}, merged=${committed.merged().size}"
            }

            // Throws when nothing was found and a query failed, so the caller can tell "not
            // owned" apart from "couldn't verify".
            combinePurchaseResults(iap, sub, skuTypeOf)

            PurchaseRefresh(
                purchases = committed.merged(),
                confirmed = confirmed,
                hasConfirmedProPurchase = confirmed.any { purchase ->
                    purchase.products.any { skuTypeOf(it) != null }
                },
                isComplete = isComplete,
                occurredAt = committedAt,
                partialError = iap.exceptionOrNull() ?: sub.exceptionOrNull(),
            )
        }
    }

    // Never throws except on cancellation, so a single failing product-type query doesn't cancel
    // the sibling query (or the coroutineScope). The exception is already user-friendly-mapped.
    // Returns owned AND pending purchases; the split by state happens where it matters (fresh
    // stream, entitlement mapping), so a pending payment stays visible instead of vanishing here.
    private suspend fun queryRelevantProducts(
        @BillingClient.ProductType type: String,
    ): Result<Collection<Purchase>> = try {
        Result.success(queryPurchases(type).filter { it.isRelevant })
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
            "queryPurchases($type): code=${billingResult.isSuccess}, message=${billingResult.debugMessage}, " +
                "purchaseData=${purchaseData.redacted()}"
        }

        if (!billingResult.isSuccess) {
            log(TAG, WARN) { "queryPurchases() failed" }
            throw BillingClientException(billingResult)
        }

        return purchaseData
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
            "acknowledgePurchase(purchase=${purchase.redacted()}): code=${ackResult.responseCode}, " +
                "message=${ackResult.debugMessage})"
        }

        if (!ackResult.isSuccess) {
            throw BillingClientException(ackResult)
        }
        return ackResult
    }

    suspend fun querySkus(vararg skus: Sku): Collection<SkuDetails> {
        log(TAG) { "querySkus(skus=${skus.joinToString { it.print() }})..." }
        // Play answers per product, not per request entry: a duplicated request entry would make the
        // exactly-one-match rule below ambiguous for no reason.
        val requested = skus.distinctBy { it.id to it.type }
        if (requested.size != skus.size) {
            log(TAG, WARN) { "querySkus(): deduped duplicate request entries: ${skus.joinToString { it.print() }}" }
        }

        val productList = requested.map { sku ->
            QueryProductDetailsParams.Product.newBuilder().apply {
                setProductId(sku.id)
                setProductType(sku.playProductType)
            }.build()
        }

        val params = QueryProductDetailsParams.newBuilder().apply {
            setProductList(productList)
        }.build()

        // Cancellable so the ViewModel's query timeout and flatMapLatest-based retry actually work:
        // with suspendCoroutine a missing Play callback kept the timeout suspended indefinitely.
        val (result, queryResult) = suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult>> { continuation ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (continuation.isActive) {
                    continuation.resume(result to queryResult) { _, _, _ -> }
                }
            }
        }
        val details = queryResult.productDetailsList.orEmpty()
        val unfetched = queryResult.unfetchedProductList.orEmpty()

        log(TAG) {
            "querySkus(skus=${skus.joinToString { it.print() }}): code=${result.responseCode}, " +
                "debug=${result.debugMessage}, skuDetails=$details, unfetched=${unfetched.printed()}"
        }

        if (!result.isSuccess) {
            log(TAG, WARN) { "querySkus() failed: code=${result.responseCode}, message=${result.debugMessage}" }
            val firstSku = requested.firstOrNull()
            if (result.responseCode == BillingResponseCode.ITEM_UNAVAILABLE && firstSku != null) {
                // Merchandising state (region, account eligibility, pulled product), not a defect:
                // typed so the user gets copy instead of a bug report.
                throw OfferUnavailableBillingException(firstSku, null)
            }
            throw BillingClientException(result)
        }

        if (unfetched.isNotEmpty()) {
            log(TAG, WARN) { "querySkus(): Play did not fetch ${unfetched.printed()}" }
        }
        // A malformed product ID is OUR configuration defect and stays on the reportable path. The
        // merchandising statuses (product pulled, no eligible offer) are normal Play conditions and
        // fall out of the per-sku matching below as OfferUnavailableBillingException.
        unfetched
            .firstOrNull { it.statusCode == UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT }
            ?.let { invalid ->
                throw BillingClientException(
                    BillingResult.newBuilder().apply {
                        setResponseCode(BillingResponseCode.DEVELOPER_ERROR)
                        setDebugMessage("Invalid product id format: ${invalid.productId} (${invalid.productType})")
                    }.build()
                )
            }

        val returned = details.groupBy { it.productId to it.productType }
        val requestedKeys = requested.map { it.id to it.playProductType }.toSet()
        returned.keys
            .filterNot { it in requestedKeys }
            .forEach { (id, type) -> log(TAG, WARN) { "querySkus(): skipping unrequested product $id ($type)" } }

        // Iterating the REQUEST (not the response) is what makes an omitted sku visible at all:
        // walking the returned groups only ever visits what Play chose to send.
        return requested.map { sku ->
            val matches = returned[sku.id to sku.playProductType].orEmpty()
            // Exactly one, never "the first of several": a duplicate row means we can't tell which
            // one Play meant, and guessing would sell the user a different product.
            val detail = matches.singleOrNull()
            if (detail == null) {
                log(TAG, WARN) {
                    "querySkus(): expected exactly 1 detail for ${sku.print()}, got ${matches.size} of $details"
                }
                throw OfferUnavailableBillingException(sku, null)
            }
            SkuDetails(sku, detail)
        }
    }

    suspend fun launchBillingFlow(activity: Activity, sku: Sku, targetOffer: Sku.Subscription.Offer?): BillingResult {
        log(TAG) { "launchBillingFlow(activity=$activity, sku=$sku)" }
        if (sku.type == Sku.Type.SUBSCRIPTION) {
            requireNotNull(targetOffer) { "SUB skus require a target offer" }
        }

        val skuDetails = querySkus(sku)
        val data = skuDetails.singleOrNull { it.sku == sku }
        if (data == null) {
            log(TAG, WARN) { "launchBillingFlow(): no unique details for ${sku.print()} in $skuDetails" }
            throw OfferUnavailableBillingException(sku, targetOffer)
        }

        val params = BillingFlowParams.newBuilder().apply {
            val productDetail = BillingFlowParams.ProductDetailsParams.newBuilder().apply {
                setProductDetails(data.details)
                if (sku is Sku.Subscription && targetOffer != null) {
                    // singleOrNull, not single: an absent offer (withheld/revoked) and an ambiguous
                    // duplicate both mean we must not guess an offer token — the wrong one bills the
                    // user at the wrong price.
                    val offer = data.details.subscriptionOfferDetails?.singleOrNull {
                        targetOffer.matches(it)
                    }
                    if (offer == null) {
                        log(TAG, WARN) {
                            val available = data.details.subscriptionOfferDetails
                                ?.map { "${it.basePlanId}/${it.offerId}" }
                            "launchBillingFlow(): offer ${targetOffer.basePlanId}/${targetOffer.offerId} " +
                                "unavailable for ${sku.print()}, available=$available"
                        }
                        throw OfferUnavailableBillingException(sku, targetOffer)
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
        if (!result.isSuccess) {
            // Same merchandising state as at query time, just reported by the launch instead: the
            // product/offer isn't purchasable, which is not a defect worth reporting.
            if (result.responseCode == BillingResponseCode.ITEM_UNAVAILABLE) {
                throw OfferUnavailableBillingException(sku, targetOffer)
            }
            throw BillingClientException(result)
        }

        return result
    }

    companion object {
        val TAG: String = logTag("Upgrade", "Gplay", "Billing", "ClientConnection")

        // Play's product-type string for one of our SKUs. Part of the match key: a product ID alone
        // is not unique across product types.
        private val Sku.playProductType: String
            get() = when (type) {
                Sku.Type.IAP -> BillingClient.ProductType.INAPP
                Sku.Type.SUBSCRIPTION -> BillingClient.ProductType.SUBS
            }

        // Log-friendly rendering of the products Play refused to fetch (no purchase data involved).
        private fun Collection<UnfetchedProduct>.printed(): String =
            joinToString(prefix = "[", postfix = "]") { "${it.productId}(${it.productType})=${it.statusCode}" }

        // Classifies event purchases by product type at ingestion, so a later per-type query can
        // authoritatively supersede them. Unknown products stay untyped (cleared only by a
        // complete refresh).
        internal val DEFAULT_SKU_TYPE_RESOLVER: (String) -> Sku.Type? = { productId ->
            OurSku.PRO_SKUS.singleOrNull { it.id == productId }?.type
        }

        // Combines the two product-type query results: an error is only propagated when the refresh
        // learned nothing usable, so callers can tell "not owned" apart from "couldn't verify one
        // product type". A PURCHASED result of ANY product suppresses the error — every product
        // this app sells is a Pro SKU (see OurSku.PRO_SKUS), so it is by construction relevant. A
        // PENDING result only counts when it maps to a KNOWN Pro SKU: it grants nothing, and an
        // unknown pending product says nothing about the type whose query failed, so treating it
        // as a find would swallow a real "couldn't verify". Pure and unit-tested.
        internal fun combinePurchaseResults(
            iap: Result<Collection<Purchase>>,
            sub: Result<Collection<Purchase>>,
            typeOf: (String) -> Sku.Type? = DEFAULT_SKU_TYPE_RESOLVER,
        ): Collection<Purchase> {
            val returned = iap.getOrNull().orEmpty() + sub.getOrNull().orEmpty()
            val found = returned.filter { purchase ->
                purchase.isPurchased || purchase.products.any { typeOf(it) != null }
            }
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
