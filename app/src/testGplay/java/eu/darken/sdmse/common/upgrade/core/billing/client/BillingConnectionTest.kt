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
        state: Int = PurchaseState.PURCHASED,
    ) = mockk<Purchase>().apply {
        every { purchaseTime } returns time
        every { purchaseToken } returns token
        every { this@apply.products } returns products
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
    }

    private fun pendingPurchase(
        time: Long,
        token: String = "pending-$time",
        products: List<String> = listOf(OurSku.Iap.PRO_UPGRADE.id),
    ) = purchase(time = time, token = token, products = products, state = PurchaseState.PENDING)

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

    @Test fun `a known pending purchase suppresses the couldn't-verify error`() {
        // The user's payment is being processed: that IS a usable answer about this account, so a
        // failing sibling query must not turn the screen into "can't reach Play".
        val pending = pendingPurchase(1_000)

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(pending)),
            sub = Result.failure(RuntimeException("SUBS query failed")),
            typeOf = typeOf,
        ) shouldBe listOf(pending)
    }

    @Test fun `an unknown pending purchase does not suppress the couldn't-verify error`() {
        // A pending payment for a product this app doesn't sell says nothing about the type whose
        // query failed — counting it as a find would swallow a real "couldn't verify".
        shouldThrow<RuntimeException> {
            BillingConnection.combinePurchaseResults(
                iap = Result.success(listOf(pendingPurchase(1_000, products = listOf("some.unknown.product")))),
                sub = Result.failure(RuntimeException("SUBS query failed")),
                typeOf = typeOf,
            )
        }
    }

    @Test fun `an unknown PURCHASED product still suppresses the error`() {
        // Ownership of anything is authoritative: every product this app sells is a Pro SKU, so an
        // unrecognized owned product means our own SKU list is stale, not that Play failed.
        val owned = purchase(1_000, products = listOf("some.unknown.product"))

        BillingConnection.combinePurchaseResults(
            iap = Result.success(listOf(owned)),
            sub = Result.failure(RuntimeException("SUBS query failed")),
            typeOf = typeOf,
        ) shouldBe listOf(owned)
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

    @Test fun `a pending purchase event enters the state but never the fresh stream`() = runTest2 {
        val pending = pendingPurchase(1_000, token = "pending")
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        val freshUpdates = mutableListOf<BillingConnection.FreshUpdate>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            connection.freshUpdates.collect { freshUpdates.add(it) }
        }
        connection.refreshPurchases() // settles the state; predates the event

        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(pending))
        runCurrent()

        // Visible as state (the UI must be able to show a payment in progress)...
        connection.purchases.first().map { it.purchaseToken } shouldBe listOf("pending")
        // ...but the fresh stream feeds the entitlement and grace bookkeeping, so only the
        // refresh's own emission is on it — a pending payment confirms nothing.
        freshUpdates.size shouldBe 1
        freshUpdates.single().purchases shouldBe emptyList()
    }

    @Test fun `a pending query result enters the state but never the fresh stream`() = runTest2 {
        val pending = pendingPurchase(1_000, token = "pending")
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(pending),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        val refresh = connection.refreshPurchases()

        refresh.purchases.map { it.purchaseToken } shouldBe listOf("pending")
        refresh.confirmed shouldBe emptyList()
        refresh.hasConfirmedProPurchase shouldBe false
        connection.purchases.first().map { it.purchaseToken } shouldBe listOf("pending")
        val update = connection.freshUpdates.first()
        update.purchases shouldBe emptyList()
        update.isFullSnapshot shouldBe true
    }

    @Test fun `a completing payment supersedes its own pending entry`() = runTest2 {
        // Play delivers the same purchaseToken again once the payment cleared: the dedup must let
        // the PURCHASED instance win instead of leaving the user with two records.
        val pending = pendingPurchase(1_000, token = "same")
        val purchased = purchase(1_000, token = "same")
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        connection.refreshPurchases() // settles the state; predates both events
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(pending))
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(purchased))

        val merged = connection.purchases.first()
        merged.size shouldBe 1
        merged.single().purchaseState shouldBe PurchaseState.PURCHASED
        // The completion is fresh Play data: it must reach the grace bookkeeping, unlike the
        // pending event before it.
        val updates = connection.freshUpdates.take(2).toList()
        updates[0].purchases shouldBe emptyList()
        updates[1].purchases shouldBe listOf(purchased)
    }

    @Test fun `an unspecified-state purchase is dropped everywhere`() = runTest2 {
        val unspecified = purchase(1_000, token = "unspecified", state = PurchaseState.UNSPECIFIED_STATE)
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(unspecified),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(unspecified))

        val refresh = connection.refreshPurchases()

        // Neither owned nor a payment in progress: it must not reach state, refresh or UI.
        refresh.purchases shouldBe emptyList()
        connection.purchases.first() shouldBe emptyList()
    }

    @Test fun `a pending-only overlay survivor does not suppress absence bookkeeping`() = runTest2 {
        // A pending payment arrives while a refresh is in flight: the queries verified empty, and
        // the surviving PENDING overlay proves no ownership — the snapshot still proves absence,
        // unlike the PURCHASED case (which must not start a false unconfirmed-grace episode).
        val pendingListeners = mutableListOf<PurchasesResponseListener>()
        val client = mockk<BillingClient>().apply {
            every { queryPurchasesAsync(any<QueryPurchasesParams>(), any()) } answers {
                pendingListeners.add(secondArg())
            }
        }
        val connection = BillingConnection(client = client)

        val refresh = async(start = CoroutineStart.UNDISPATCHED) { connection.refreshPurchases() }
        runCurrent()
        connection.onPurchasesUpdated(result(BillingResponseCode.OK), listOf(pendingPurchase(1_000)))
        pendingListeners.forEach { it.onQueryPurchasesResponse(result(BillingResponseCode.OK), mutableListOf()) }
        runCurrent()
        refresh.await()

        val update = connection.freshUpdates.first()
        update.purchases shouldBe emptyList()
        update.isFullSnapshot shouldBe true
    }

    @Test fun `a refresh reports only what its own queries confirmed`() = runTest2 {
        val iapOwned = purchase(1_000, token = "iap")
        val connection = BillingConnection(
            client = clientReturning(
                // Refresh 1: both succeed, IAP owned.
                result(BillingResponseCode.OK) to listOf(iapOwned),
                result(BillingResponseCode.OK) to emptyList(),
                // Refresh 2: IAP fails (stale snapshot retained), SUB confirms a pending payment.
                result(BillingResponseCode.ERROR) to emptyList(),
                result(BillingResponseCode.OK) to listOf(
                    pendingPurchase(2_000, token = "pending-sub", products = listOf(OurSku.Sub.PRO_UPGRADE.id))
                ),
            ),
        )
        connection.refreshPurchases()

        val second = connection.refreshPurchases()

        // The retained IAP purchase is in the committed view, but it is NOT something these
        // queries confirmed — reporting it as confirmed Pro would keep the grace clock frozen
        // through an indefinite outage.
        second.purchases.map { it.purchaseToken } shouldContainExactly listOf("pending-sub", "iap")
        second.confirmed shouldBe emptyList()
        second.hasConfirmedProPurchase shouldBe false
        second.isComplete shouldBe false
        second.partialError.shouldNotBeNull()
    }

    @Test fun `a confirmed known purchase is reported as confirmed pro`() = runTest2 {
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to listOf(purchase(1_000, token = "iap")),
                result(BillingResponseCode.ERROR) to emptyList(),
            ),
        )

        val refresh = connection.refreshPurchases()

        refresh.confirmed.map { it.purchaseToken } shouldBe listOf("iap")
        refresh.hasConfirmedProPurchase shouldBe true
        refresh.isComplete shouldBe false
    }

    @Test fun `a refresh is stamped with its commit time`() = runTest2 {
        val before = System.currentTimeMillis()
        val connection = BillingConnection(
            client = clientReturning(
                result(BillingResponseCode.OK) to emptyList(),
                result(BillingResponseCode.OK) to emptyList(),
            ),
        )

        val refresh = connection.refreshPurchases()
        val after = System.currentTimeMillis()

        (refresh.occurredAt in before..after) shouldBe true
        // The same instant travels on the fresh update, so a confirmation and a later failure
        // signal can be ordered against each other.
        refresh.occurredAt shouldBe connection.freshUpdates.first().occurredAt
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

    // Play's QueryProductDetailsParams rejects a product list that mixes INAPP and SUBS, so an
    // "omitted sku" fixture needs a second sku of the SAME type as the one Play does answer with.
    private object OtherIap : Sku.Iap {
        override val id: String = "eu.darken.sdmse.iap.other"
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
        // Iterating the response instead of the request would silently return only the details Play
        // did send and never notice that the second requested sku is missing.
        val connection = BillingConnection(
            client = skuClient(details = listOf(productDetails(OurSku.Iap.PRO_UPGRADE.id))),
        )

        shouldThrow<OfferUnavailableBillingException> {
            connection.querySkus(OurSku.Iap.PRO_UPGRADE, OtherIap)
        }.sku shouldBe OtherIap
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
