package eu.darken.sdmse.common.upgrade.core.billing.client

import android.text.TextUtils
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.UnfetchedProduct
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.OfferUnavailableBillingException
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingConnectionTest : BaseTest() {

    @BeforeEach
    fun setup() {
        // launchBillingFlow() hops to the main thread (documented BillingClient contract) --
        // unconfined so the hop resolves in place on the test thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // BillingFlowParams' builders validate through android.text.TextUtils, whose JVM stub throws
        // "not mocked". Give it its real semantics instead.
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(TextUtils::class)
    }

    private fun purchase(
        time: Long,
        token: String = "token-$time",
        products: List<String> = listOf(OurSku.Iap.PRO_UPGRADE.id),
        acknowledged: Boolean = false,
    ) = mockk<Purchase>().apply {
        every { purchaseTime } returns time
        every { purchaseToken } returns token
        every { this@apply.products } returns products
        every { purchaseState } returns PurchaseState.PURCHASED
        every { isAcknowledged } returns acknowledged
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    private val typeOf: (String) -> Sku.Type? = { id -> OurSku.PRO_SKUS.singleOrNull { it.id == id }?.type }

    // region combinePurchaseResults (pure)

    @Test fun `combines both product types, newest first`() {
        val older = purchase(1_000)
        val newer = purchase(2_000)

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(older)),
            sub = Result.success(listOf(newer)),
        ) shouldBe listOf(newer, older)
    }

    @Test fun `a single product-type failure does not mask a purchase found by the other`() {
        val owned = purchase(1_000)

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(owned)),
            sub = Result.failure(RuntimeException("SUBS query failed")),
        ) shouldBe listOf(owned)

        BillingConnection.combinePurchaseResults(
            iap = Result.failure(RuntimeException("IAP query failed")),
            sub = Result.success(listOf(owned)),
        ) shouldBe listOf(owned)
    }

    @Test fun `both product types empty returns empty`() {
        BillingConnection.combinePurchaseResults(
            iap = Result.success(emptyList()),
            sub = Result.success(emptyList()),
        ) shouldBe emptyList()
    }

    @Test fun `nothing found but a query failed rethrows the error`() {
        shouldThrow<RuntimeException> {
            BillingConnection.combinePurchaseResults(
                iap = Result.success(emptyList()),
                sub = Result.failure(RuntimeException("SUBS query failed")),
            )
        }
    }

    // endregion

    // region ReducerState (pure)

    @Test fun `events append typed overlay entries and bump the generation`() {
        val iapPurchase = purchase(1_000, products = listOf(OurSku.Iap.PRO_UPGRADE.id))
        val unknownPurchase = purchase(2_000, products = listOf("some.unknown.product"))

        val state = BillingConnection.ReducerState()
            .withEvent(listOf(iapPurchase), typeOf)
            .withEvent(listOf(unknownPurchase), typeOf)

        state.eventGen shouldBe 2L
        state.overlay.map { it.gen } shouldBe listOf(1L, 2L)
        state.overlay.map { it.type } shouldBe listOf(Sku.Type.IAP, null)
    }

    @Test fun `a per-type query replaces only its own snapshot`() {
        val oldIap = purchase(1_000, token = "iap")
        val newSub = purchase(2_000, token = "sub", products = listOf(OurSku.Sub.PRO_UPGRADE.id))

        val state = BillingConnection.ReducerState(iapSnapshot = listOf(oldIap))
            .withQueryResults(iap = null, sub = listOf(newSub), genAtQueryStart = 0L)

        // The failed IAP query keeps the last-known IAP snapshot: a partial refresh must not turn
        // "couldn't check" into "confirmed absent".
        state.iapSnapshot shouldBe listOf(oldIap)
        state.subSnapshot shouldBe listOf(newSub)
        state.merged().map { it.purchaseToken } shouldContainExactly listOf("sub", "iap")
    }

    @Test fun `a per-type query clears its own older overlay entries even on a partial refresh`() {
        val iapEvent = purchase(1_000, token = "iap-event")

        val state = BillingConnection.ReducerState()
            .withEvent(listOf(iapEvent), typeOf) // gen 1
            // IAP query (started after the event, gen 1 visible) confirms absence; SUB query failed.
            .withQueryResults(iap = emptyList(), sub = null, genAtQueryStart = 1L)

        state.overlay shouldBe emptyList()
        state.merged() shouldBe emptyList()
    }

    @Test fun `an event that raced the query survives its commit`() {
        val racedEvent = purchase(1_000, token = "raced")

        val state = BillingConnection.ReducerState()
            // Query started at gen 0, event arrived while it was in flight (gen 1).
            .withEvent(listOf(racedEvent), typeOf)
            .withQueryResults(iap = emptyList(), sub = emptyList(), genAtQueryStart = 0L)

        // The query began before the purchase existed — its empty result must not erase it.
        state.merged().map { it.purchaseToken } shouldBe listOf("raced")
    }

    @Test fun `untyped overlay entries only fall to a complete refresh`() {
        val unknown = purchase(1_000, token = "unknown", products = listOf("some.unknown.product"))
        val base = BillingConnection.ReducerState().withEvent(listOf(unknown), typeOf) // gen 1

        // Partial refresh (SUB failed): the unknown-type entry cannot be attributed, it stays.
        base.withQueryResults(iap = emptyList(), sub = null, genAtQueryStart = 1L)
            .merged().map { it.purchaseToken } shouldBe listOf("unknown")

        // Complete refresh: authoritative for everything the queries could have seen.
        base.withQueryResults(iap = emptyList(), sub = emptyList(), genAtQueryStart = 1L)
            .merged() shouldBe emptyList()
    }

    @Test fun `duplicate purchase tokens dedup with the overlay winning`() {
        // A surviving overlay entry is newer than the snapshot by construction — e.g. a purchase
        // event whose type-query hasn't re-run yet carries fresher (un)acknowledged state.
        val snapshotVersion = purchase(1_000, token = "same", acknowledged = true)
        val eventVersion = purchase(1_000, token = "same", acknowledged = false)

        val state = BillingConnection.ReducerState(iapSnapshot = listOf(snapshotVersion))
            .withEvent(listOf(eventVersion), typeOf)

        val merged = state.merged()
        merged.size shouldBe 1
        merged.single().isAcknowledged shouldBe false
    }

    @Test fun `a covering query supersedes the event representation of the same purchase`() {
        val eventVersion = purchase(1_000, token = "same", acknowledged = false)
        val queriedVersion = purchase(1_000, token = "same", acknowledged = true)

        val state = BillingConnection.ReducerState()
            .withEvent(listOf(eventVersion), typeOf) // gen 1
            // Query started after the event (genAtQueryStart = 1): its result is fresher.
            .withQueryResults(iap = listOf(queriedVersion), sub = emptyList(), genAtQueryStart = 1L)

        state.merged().single().isAcknowledged shouldBe true
    }

    // endregion

    // region refreshPurchases + reactive flow (integration)

    // A client whose purchase queries answer synchronously, in call order (INAPP first, SUBS
    // second — refreshPurchases starts them in that order on the single-threaded test dispatcher).
    private fun clientReturning(vararg responses: Pair<BillingResult, List<Purchase>>): BillingClient {
        val queue = ArrayDeque(responses.toList())
        return mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                val (result, purchases) = queue.removeFirst()
                secondArg<PurchasesResponseListener>().onQueryPurchasesResponse(result, purchases.toMutableList())
            }
        }
    }

    @Test fun `a partial-failure refresh still reaches the reactive purchases flow`() = runTest2 {
        // A purchase found by one product type while the other query fails must not leave the
        // reactive purchases/upgradeInfo chain starved — otherwise a successful restore would
        // never actually unlock the app.
        val owned = purchase(1_000)
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(owned),
                result(BillingResponseCode.ERROR) to emptyList(),
            ),
        )

        val refresh = connection.refreshPurchases()

        refresh.purchases shouldBe listOf(owned)
        refresh.isComplete shouldBe false
        connection.purchases.first() shouldBe listOf(owned)
    }

    @Test fun `a failed sibling query keeps the previous refresh's snapshot for its type`() = runTest2 {
        val iapOwned = purchase(1_000, token = "iap")
        val subOwned = purchase(2_000, token = "sub", products = listOf(OurSku.Sub.PRO_UPGRADE.id))
        val connection = BillingConnection(
            client = clientReturning(
                // Refresh 1: both succeed, IAP owned.
                result(BillingResponseCode.OK) to listOf(iapOwned),
                result(BillingResponseCode.OK) to emptyList(),
                // Refresh 2: IAP query fails, SUB finds a purchase.
                result(BillingResponseCode.ERROR) to emptyList(),
                result(BillingResponseCode.OK) to listOf(subOwned),
            ),
        )

        connection.refreshPurchases().isComplete shouldBe true
        val second = connection.refreshPurchases()

        // The IAP purchase from refresh 1 is retained — its query failing is not proof of absence.
        second.isComplete shouldBe false
        second.purchases.map { it.purchaseToken } shouldContainExactly listOf("sub", "iap")
        connection.purchases.first().map { it.purchaseToken } shouldContainExactly listOf("sub", "iap")
    }

    @Test fun `a complete refresh clears event purchases it could have seen`() = runTest2 {
        // Refund case: the purchase arrived via event, then a complete refresh confirms it's gone —
        // the stale event must not keep it alive in the reactive flow.
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(purchase(1_000)))

        val refresh = connection.refreshPurchases()

        refresh.purchases shouldBe emptyList()
        connection.purchases.first() shouldBe emptyList()
    }

    @Test fun `an event arriving while the query is in flight survives the commit`() = runTest2 {
        val pendingListeners = mutableListOf<PurchasesResponseListener>()
        val client = mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                pendingListeners.add(secondArg())
            }
        }
        val connection = BillingConnection(client = client)

        val refresh = async(start = CoroutineStart.UNDISPATCHED) { connection.refreshPurchases() }
        runCurrent()
        pendingListeners.size shouldBe 2

        // Purchase event lands while both queries are still in flight.
        val racedPurchase = purchase(1_000, token = "raced")
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(racedPurchase))

        pendingListeners.forEach { it.onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf()) }
        runCurrent()

        // The queries began before the purchase existed — empty results must not erase it.
        refresh.await().purchases.map { it.purchaseToken } shouldBe listOf("raced")
        connection.purchases.first().map { it.purchaseToken } shouldBe listOf("raced")
    }

    @Test fun `verified absence survives a failed sibling query`() = runTest2 {
        // The IAP query successfully confirms the purchase is GONE (refund) while the SUB query
        // fails: the refresh reports the couldn't-verify error, but the verified absence must
        // still have been committed — otherwise repeated SUB failures retain Pro indefinitely.
        val owned = purchase(1_000)
        val connection = BillingConnection(
            client = clientReturning(
                // Refresh 1: IAP owned, both types succeed.
                result(BillingResponseCode.OK) to listOf(owned),
                result(BillingResponseCode.OK) to emptyList(),
                // Refresh 2: IAP verified empty, SUB fails.
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.ERROR) to emptyList(),
            ),
        )
        connection.refreshPurchases().purchases shouldBe listOf(owned)

        shouldThrow<Exception> { connection.refreshPurchases() }

        connection.purchases.first() shouldBe emptyList()
    }

    @Test fun `a refresh commits exactly one reactive emission`() = runTest2 {
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(purchase(1_000)),
                result(BillingResponseCode.OK) to listOf(
                    purchase(2_000, products = listOf(OurSku.Sub.PRO_UPGRADE.id))
                ),
            ),
        )
        val emissions = mutableListOf<Collection<Purchase>>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            connection.purchases.collect { emissions.add(it) }
        }

        connection.refreshPurchases()
        runCurrent()

        // Both per-type results land in ONE committed state — observers never see an intermediate
        // combination that refreshPurchases() didn't return.
        emissions.size shouldBe 1
    }

    @Test fun `concurrent refreshes are serialized`() = runTest2 {
        val pendingListeners = mutableListOf<PurchasesResponseListener>()
        val client = mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                pendingListeners.add(secondArg())
            }
        }
        val connection = BillingConnection(client = client)

        val first = async(start = CoroutineStart.UNDISPATCHED) { connection.refreshPurchases() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { connection.refreshPurchases() }
        runCurrent()

        // Only the first refresh may query; the second waits on the mutex instead of racing its
        // commit against a possibly newer result.
        pendingListeners.size shouldBe 2

        val owned = purchase(1_000)
        pendingListeners.forEach { it.onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf(owned)) }
        runCurrent()
        first.await().purchases shouldBe listOf(owned)

        pendingListeners.size shouldBe 4
        pendingListeners.drop(2).forEach {
            it.onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf(owned))
        }
        runCurrent()
        second.await().purchases shouldBe listOf(owned)
    }

    @Test fun `purchase failures are delivered as events, repeats included`() = runTest2 {
        val connection = BillingConnection(client = mockk())
        val alreadyOwned = result(BillingResponseCode.ITEM_ALREADY_OWNED)

        connection.onPurchasesUpdated(alreadyOwned, null)
        connection.onPurchasesUpdated(alreadyOwned, null)

        // Two identical back-to-back failures both arrive — event semantics, no conflation.
        val received = connection.purchaseFailures.take(2).toList()
        received.map { it.responseCode } shouldBe
            listOf(BillingResponseCode.ITEM_ALREADY_OWNED, BillingResponseCode.ITEM_ALREADY_OWNED)
    }

    @Test fun `a failure event does not evict a fresh purchase event`() = runTest2 {
        // Failures and successes travel separately: a reopened-sheet USER_CANCELED must not
        // overwrite (or conflate away) a fresh purchase that no query snapshot contains yet.
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )
        connection.refreshPurchases() // empty snapshot, predates the purchase

        val owned = purchase(1_000)
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(owned))
        connection.onPurchasesUpdated(result(BillingResponseCode.USER_CANCELED), null)

        connection.purchases.first() shouldBe listOf(owned)
        connection.purchaseFailures.first().responseCode shouldBe BillingResponseCode.USER_CANCELED
    }

    @Test fun `a pending purchase never surfaces as owned or as fresh data`() = runTest2 {
        val pending = mockk<Purchase>().apply {
            every { purchaseState } returns PurchaseState.PENDING
            every { purchaseTime } returns 1_000L
            every { purchaseToken } returns "pending"
            every { this@apply.products } returns listOf(OurSku.Iap.PRO_UPGRADE.id)
        }
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(pending))
        connection.refreshPurchases()

        connection.purchases.first() shouldBe emptyList()
        // Only the refresh's own emission — the PENDING event never produced one.
        val update = connection.freshUpdates.first()
        update.purchases shouldBe emptyList()
        update.isFullSnapshot shouldBe true
    }

    @Test fun `fresh updates arrive in commit order`() = runTest2 {
        // The event's emission precedes the covering refresh's: a consumer stamping the Pro grace
        // period can never process a superseded event AFTER the query that cleared it.
        val owned = purchase(1_000)
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        val beforeCommit = System.currentTimeMillis()
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(owned))
        connection.refreshPurchases() // complete, clears the event

        val updates = connection.freshUpdates.take(2).toList()
        updates[0].purchases shouldBe listOf(owned)
        updates[0].isFullSnapshot shouldBe false
        // Each update is stamped with its commit time (wall-clock), not left unset.
        (updates[0].occurredAt >= beforeCommit) shouldBe true
        updates[1].purchases shouldBe emptyList()
        updates[1].isFullSnapshot shouldBe true
    }

    @Test fun `fresh updates carry only query-confirmed data, never retained stale purchases`() = runTest2 {
        val iapOwned = purchase(1_000, token = "iap")
        val connection = BillingConnection(
            client = clientReturning(
                // Refresh 1: both succeed, IAP owned.
                result(BillingResponseCode.OK) to listOf(iapOwned),
                result(BillingResponseCode.OK) to emptyList(),
                // Refresh 2: IAP fails (stale snapshot retained), SUB verified empty.
                result(BillingResponseCode.ERROR) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )
        connection.refreshPurchases()
        // Refresh 2 found nothing fresh AND a query failed: it reports the couldn't-verify error —
        // after committing what the SUB query did confirm.
        shouldThrow<Exception> { connection.refreshPurchases() }

        val updates = connection.freshUpdates.take(2).toList()
        updates[0].purchases shouldBe listOf(iapOwned)
        updates[0].isFullSnapshot shouldBe true
        // The retained IAP purchase is still in the reactive view, but it is NOT fresh Play data —
        // re-emitting it would keep re-stamping the grace window without a real round-trip.
        updates[1].purchases shouldBe emptyList()
        updates[1].isFullSnapshot shouldBe false
        connection.purchases.first().map { it.purchaseToken } shouldBe listOf("iap")
    }

    @Test fun `an empty complete refresh racing a purchase event is not a full snapshot`() = runTest2 {
        // A user buys while a refresh is in flight: both queries verify empty (they started before
        // the purchase existed), but the surviving event means this refresh does NOT prove total
        // absence — a full-snapshot claim here would start a false unconfirmed-grace episode for
        // the just-purchased user.
        val pendingListeners = mutableListOf<PurchasesResponseListener>()
        val client = mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                pendingListeners.add(secondArg())
            }
        }
        val connection = BillingConnection(client = client)

        val refresh = async(start = CoroutineStart.UNDISPATCHED) { connection.refreshPurchases() }
        runCurrent()
        pendingListeners.size shouldBe 2

        val raced = purchase(1_000, token = "raced")
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(raced))
        pendingListeners.forEach { it.onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf()) }
        runCurrent()
        refresh.await()

        val updates = connection.freshUpdates.take(2).toList()
        updates[0].purchases shouldBe listOf(raced)
        updates[0].isFullSnapshot shouldBe false
        updates[1].purchases shouldBe emptyList()
        updates[1].isFullSnapshot shouldBe false
    }

    @Test fun `a hanging sku query callback cannot defeat the caller's timeout`() = runTest2 {
        val client = mockk<BillingClient>().apply {
            every { queryProductDetailsAsync(any(), any()) } answers { /* Play never calls back */ }
        }
        val connection = BillingConnection(client = client)

        // With a non-cancellable suspension this would hang past the deadline until Play answered;
        // suspendCancellableCoroutine lets the timeout fire on time.
        val outcome = withTimeoutOrNull(1_000) {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE)
        }

        outcome shouldBe null
    }

    // endregion

    // region querySkus + offer resolution

    private fun productDetails(
        id: String,
        type: String = BillingClient.ProductType.INAPP,
        offers: List<ProductDetails.SubscriptionOfferDetails>? = null,
    ) = mockk<ProductDetails>(relaxed = true).apply {
        every { productId } returns id
        every { productType } returns type
        every { subscriptionOfferDetails } returns offers
    }

    private fun offerDetails(
        offer: Sku.Subscription.Offer,
        token: String = "offer-token",
    ) = mockk<ProductDetails.SubscriptionOfferDetails>(relaxed = true).apply {
        every { basePlanId } returns offer.basePlanId
        every { offerId } returns offer.offerId
        every { offerToken } returns token
    }

    private fun unfetchedProduct(
        id: String,
        status: Int,
        type: String = BillingClient.ProductType.SUBS,
    ) = mockk<UnfetchedProduct>(relaxed = true).apply {
        every { productId } returns id
        every { statusCode } returns status
        every { productType } returns type
    }

    private fun skuClient(
        queryResponse: BillingResult = result(BillingResponseCode.OK),
        details: List<ProductDetails> = emptyList(),
        unfetched: List<UnfetchedProduct> = emptyList(),
        launchResult: BillingResult = result(BillingResponseCode.OK),
    ): BillingClient {
        val queryResult = mockk<QueryProductDetailsResult>().apply {
            every { productDetailsList } returns details
            every { unfetchedProductList } returns unfetched
        }
        return mockk<BillingClient>().apply {
            every { queryProductDetailsAsync(any(), any()) } answers {
                secondArg<ProductDetailsResponseListener>().onProductDetailsResponse(queryResponse, queryResult)
            }
            every { launchBillingFlow(any(), any()) } returns launchResult
        }
    }

    @Test fun `an OK response with no details is an offer problem, not a raw state exception`() = runTest2 {
        val connection = BillingConnection(client = skuClient(details = emptyList()))

        // Play omitting the product entirely used to surface as IllegalStateException -> bug report
        // plus an unlocalized dialog.
        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE)
        }.sku shouldBe OurSku.Iap.PRO_UPGRADE
    }

    @Test fun `a requested sku omitted from the response throws for that sku`() = runTest2 {
        // Iterating the response instead of the request would silently return only the IAP details
        // and never notice that the subscription is missing.
        val connection = BillingConnection(
            client = skuClient(details = listOf(productDetails(OurSku.Iap.PRO_UPGRADE.id))),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE, OurSku.Sub.PRO_UPGRADE)
        }.sku shouldBe OurSku.Sub.PRO_UPGRADE
    }

    @Test fun `duplicate details for one requested sku are ambiguous, never guessed`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(
                    productDetails(OurSku.Iap.PRO_UPGRADE.id),
                    productDetails(OurSku.Iap.PRO_UPGRADE.id),
                ),
            ),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE)
        }.sku shouldBe OurSku.Iap.PRO_UPGRADE
    }

    @Test fun `an unrequested row is skipped and does not satisfy the omitted request`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(details = listOf(productDetails("some.other.product"))),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE)
        }.sku shouldBe OurSku.Iap.PRO_UPGRADE
    }

    @Test fun `duplicate request entries are deduped instead of failing`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(details = listOf(productDetails(OurSku.Iap.PRO_UPGRADE.id))),
        )

        val details = connection.querySkus(OurSku.Iap.PRO_UPGRADE, OurSku.Iap.PRO_UPGRADE)

        details.map { it.sku } shouldBe listOf(OurSku.Iap.PRO_UPGRADE)
    }

    @Test fun `ITEM_UNAVAILABLE at query time maps to the offer problem`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(queryResponse = result(BillingResponseCode.ITEM_UNAVAILABLE)),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Sub.PRO_UPGRADE)
        }.sku shouldBe OurSku.Sub.PRO_UPGRADE
    }

    @Test fun `other query failures keep the generic client exception`() = runTest2 {
        val connection = BillingConnection(client = skuClient(queryResponse = result(BillingResponseCode.ERROR)))

        shouldThrow<BillingClientException> { connection.querySkus(OurSku.Iap.PRO_UPGRADE) }
    }

    @Test fun `an unfetched merchandising status surfaces as an offer problem`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                unfetched = listOf(
                    unfetchedProduct(OurSku.Sub.PRO_UPGRADE.id, UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER),
                ),
            ),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Sub.PRO_UPGRADE)
        }.sku shouldBe OurSku.Sub.PRO_UPGRADE
    }

    @Test fun `an invalid product id format stays on the reportable path`() = runTest2 {
        // Our own configuration defect: this one SHOULD reach the bug report, unlike the
        // merchandising states.
        val connection = BillingConnection(
            client = skuClient(
                unfetched = listOf(
                    unfetchedProduct(
                        OurSku.Sub.PRO_UPGRADE.id,
                        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT,
                    ),
                ),
            ),
        )

        shouldThrow<BillingClientException> {
            connection.querySkus(OurSku.Sub.PRO_UPGRADE)
        }.result.responseCode shouldBe BillingResponseCode.DEVELOPER_ERROR
    }

    @Test fun `a withheld offer at launch time is an offer problem, not a NoSuchElement`() = runTest2 {
        // Play returns the subscription but not the offer we want (revoked/withheld): the strict
        // single() used to blow up with an unmapped NoSuchElementException.
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(
                    productDetails(
                        id = OurSku.Sub.PRO_UPGRADE.id,
                        type = BillingClient.ProductType.SUBS,
                        offers = listOf(offerDetails(OurSku.Sub.PRO_UPGRADE.BASE_OFFER)),
                    ),
                ),
            ),
        )

        val error = shouldThrow<OfferUnavailableBillingException> {
            connection.launchBillingFlow(
                activity = mockk(),
                sku = OurSku.Sub.PRO_UPGRADE,
                targetOffer = OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER,
            )
        }
        error.sku shouldBe OurSku.Sub.PRO_UPGRADE
        error.offer shouldBe OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER
    }

    @Test fun `duplicate offer rows at launch time are ambiguous, never guessed`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(
                    productDetails(
                        id = OurSku.Sub.PRO_UPGRADE.id,
                        type = BillingClient.ProductType.SUBS,
                        offers = listOf(
                            offerDetails(OurSku.Sub.PRO_UPGRADE.BASE_OFFER, token = "token-a"),
                            offerDetails(OurSku.Sub.PRO_UPGRADE.BASE_OFFER, token = "token-b"),
                        ),
                    ),
                ),
            ),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.launchBillingFlow(
                activity = mockk(),
                sku = OurSku.Sub.PRO_UPGRADE,
                targetOffer = OurSku.Sub.PRO_UPGRADE.BASE_OFFER,
            )
        }.offer shouldBe OurSku.Sub.PRO_UPGRADE.BASE_OFFER
    }

    @Test fun `a missing subscriptionOfferDetails list at launch time is an offer problem`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(
                    productDetails(
                        id = OurSku.Sub.PRO_UPGRADE.id,
                        type = BillingClient.ProductType.SUBS,
                        offers = null,
                    ),
                ),
            ),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.launchBillingFlow(
                activity = mockk(),
                sku = OurSku.Sub.PRO_UPGRADE,
                targetOffer = OurSku.Sub.PRO_UPGRADE.BASE_OFFER,
            )
        }
    }

    @Test fun `ITEM_UNAVAILABLE from the launch result maps to the offer problem`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(productDetails(OurSku.Iap.PRO_UPGRADE.id)),
                launchResult = result(BillingResponseCode.ITEM_UNAVAILABLE),
            ),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.launchBillingFlow(mockk(), OurSku.Iap.PRO_UPGRADE, null)
        }.sku shouldBe OurSku.Iap.PRO_UPGRADE
    }

    @Test fun `other launch failures keep the generic client exception`() = runTest2 {
        val connection = BillingConnection(
            client = skuClient(
                details = listOf(productDetails(OurSku.Iap.PRO_UPGRADE.id)),
                launchResult = result(BillingResponseCode.DEVELOPER_ERROR),
            ),
        )

        shouldThrow<BillingClientException> {
            connection.launchBillingFlow(mockk(), OurSku.Iap.PRO_UPGRADE, null)
        }
    }

    // endregion

    // region querySubscriptions (pre-purchase gate)

    @Test fun `querySubscriptions returns fresh subs and keeps the iap snapshot intact`() = runTest2 {
        val iapOwned = purchase(1_000, token = "iap")
        val subOwned = purchase(2_000, token = "sub", products = listOf(OurSku.Sub.PRO_UPGRADE.id))
        val client = clientReturning(
            // Refresh: IAP owned, no subs yet.
            result(BillingResponseCode.OK) to listOf(iapOwned),
            result(BillingResponseCode.OK) to emptyList(),
            // SUBS-only gate query: sub found.
            result(BillingResponseCode.OK) to listOf(subOwned),
        )
        val connection = BillingConnection(client = client)
        connection.refreshPurchases()

        val gateView = connection.querySubscriptions()

        gateView shouldBe listOf(subOwned)
        // The gate must have queried SUBS, not INAPP (clientReturning ignores the params, so this
        // would otherwise go unnoticed). zza() is the params' only product-type accessor — a
        // billing library upgrade renaming it breaks this line loudly at compile time.
        verify(exactly = 2) {
            client.queryPurchasesAsync(match<QueryPurchasesParams> { it.zza() == BillingClient.ProductType.SUBS }, any())
        }
        // The SUBS-only commit updates the reactive view WITHOUT disturbing the IAP snapshot —
        // wiping it would briefly un-Pro a one-time-purchase owner.
        connection.purchases.first().map { it.purchaseToken } shouldContainExactly listOf("sub", "iap")
        val updates = connection.freshUpdates.take(2).toList()
        // Partial by definition: it proves what the SUBS query found, never absence of the rest.
        updates[1].purchases shouldBe listOf(subOwned)
        updates[1].isFullSnapshot shouldBe false
    }

    @Test fun `a failed querySubscriptions propagates and commits nothing`() = runTest2 {
        val iapOwned = purchase(1_000, token = "iap")
        val subOwned = purchase(2_000, token = "sub", products = listOf(OurSku.Sub.PRO_UPGRADE.id))
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(iapOwned),
                result(BillingResponseCode.OK) to listOf(subOwned),
                result(BillingResponseCode.ERROR) to emptyList(),
            ),
        )
        val freshUpdates = mutableListOf<BillingConnection.FreshUpdate>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            connection.freshUpdates.collect { freshUpdates.add(it) }
        }
        connection.refreshPurchases()

        // Fail-closed contract: the gate must see the error, not an empty "no subscriptions".
        shouldThrow<BillingClientException> { connection.querySubscriptions() }
        runCurrent()

        // No commit and no fresh emission from the failed query — only the refresh's own. Both
        // snapshots survive, including the SUB one the failed query was about.
        connection.purchases.first().map { it.purchaseToken } shouldContainExactly listOf("sub", "iap")
        freshUpdates.size shouldBe 1
    }

    @Test fun `querySubscriptions clears an older sub overlay it could have seen`() = runTest2 {
        // The sub arrived via purchase event, then the user refunded/cancelled it away: the gate
        // query verifies empty and must supersede the stale event — otherwise the ghost sub keeps
        // blocking the one-time purchase.
        val subEvent = purchase(1_000, token = "sub-event", products = listOf(OurSku.Sub.PRO_UPGRADE.id))
        val connection = BillingConnection(
            client = clientReturning(result(BillingResponseCode.OK) to emptyList()),
        )
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(subEvent))

        connection.querySubscriptions() shouldBe emptyList()
        connection.purchases.first() shouldBe emptyList()
    }

    @Test fun `querySubscriptions prefers a racing event's renewal state for the same token`() = runTest2 {
        // The stale query result says the sub no longer renews, but a purchase event that landed
        // while the query was in flight says it does (user just re-subscribed): the gate must see
        // the overlay version, or the fail-closed double-billing check lets the buy through.
        val queried = purchase(1_000, token = "same", products = listOf(OurSku.Sub.PRO_UPGRADE.id)).apply {
            every { isAutoRenewing } returns false
        }
        val raced = purchase(1_000, token = "same", products = listOf(OurSku.Sub.PRO_UPGRADE.id)).apply {
            every { isAutoRenewing } returns true
        }
        val pendingListeners = mutableListOf<PurchasesResponseListener>()
        val client = mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                pendingListeners.add(secondArg())
            }
        }
        val connection = BillingConnection(client = client)

        val gate = async(start = CoroutineStart.UNDISPATCHED) { connection.querySubscriptions() }
        runCurrent()
        pendingListeners.size shouldBe 1

        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(raced))
        pendingListeners.single().onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf(queried))
        runCurrent()

        val gateView = gate.await()
        gateView.single().isAutoRenewing shouldBe true
    }

    @Test fun `querySubscriptions excludes iap overlay entries but keeps untyped ones`() = runTest2 {
        val iapEvent = purchase(1_000, token = "iap-event")
        val unknownEvent = purchase(2_000, token = "unknown", products = listOf("some.unknown.product"))
        val connection = BillingConnection(
            client = clientReturning(result(BillingResponseCode.OK) to emptyList()),
        )
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(iapEvent))
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(unknownEvent))

        val gateView = connection.querySubscriptions()

        // An IAP can't be the blocking subscription; an unknown product might be, so it stays in
        // on the safe side.
        gateView.map { it.purchaseToken } shouldBe listOf("unknown")
        // Excluded from the gate view only — the reducer still owns both entries.
        connection.purchases.first().map { it.purchaseToken } shouldContainExactly listOf("unknown", "iap-event")
    }

    @Test fun `querySubscriptions filters pending subscription results`() = runTest2 {
        val pendingSub = mockk<Purchase>().apply {
            every { purchaseState } returns PurchaseState.PENDING
            every { purchaseTime } returns 1_000L
            every { purchaseToken } returns "pending-sub"
            every { this@apply.products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
        }
        val connection = BillingConnection(
            client = clientReturning(result(BillingResponseCode.OK) to listOf(pendingSub)),
        )

        // A PENDING subscription is not an active one — it must neither block the gate nor
        // surface as owned.
        connection.querySubscriptions() shouldBe emptyList()
        connection.purchases.first() shouldBe emptyList()
    }

    // endregion

    // region acknowledgement

    @Test fun `a late ack callback after the caller gave up is ignored`() = runTest2 {
        var listener: AcknowledgePurchaseResponseListener? = null
        val client = mockk<BillingClient>().apply {
            every { acknowledgePurchase(any<AcknowledgePurchaseParams>(), any()) } answers {
                listener = secondArg()
            }
        }
        val connection = BillingConnection(client = client)

        val ack = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1_000) { connection.acknowledgePurchase(purchase(1_000)) }
        }
        runCurrent()
        listener.shouldNotBeNull()

        advanceTimeBy(1_001) // the ack path's per-attempt timeout fires while Play is still silent
        runCurrent()
        ack.await() shouldBe null

        // Play answers late, on its own thread: resuming the abandoned continuation must be a no-op,
        // not an IllegalStateException — the bounded ack attempts depend on that contract.
        listener!!.onAcknowledgePurchaseResponse(result(BillingResponseCode.OK))
        runCurrent()
    }

    // endregion
}
