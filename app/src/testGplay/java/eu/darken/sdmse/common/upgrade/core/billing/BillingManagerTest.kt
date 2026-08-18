package eu.darken.sdmse.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.sdmse.common.debug.logging.Logging.Priority.INFO
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingClientException
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnection
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnectionProvider
import eu.darken.sdmse.common.upgrade.core.billing.work.PurchaseAckScheduler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingManagerTest : BaseTest() {

    // Relaxed: the safety net is fail-open plumbing around the ack pass — only the dedicated
    // tests below assert on it.
    private val ackScheduler = mockk<PurchaseAckScheduler>(relaxed = true)

    @BeforeEach
    fun setup() {
        mockkObject(Bugs)
        justRun { Bugs.report(any()) }
    }

    @AfterEach
    fun teardown() {
        unmockkObject(Bugs)
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    private fun purchase() = mockk<Purchase>().apply {
        every { purchaseState } returns PurchaseState.PURCHASED
        every { purchaseTime } returns 1_000L
        every { purchaseToken } returns "token"
        every { isAcknowledged } returns true
    }

    // A payment Play is still processing: carried by the state, but never owned and never ackable.
    private fun pendingPurchase(token: String = "pending-token") = mockk<Purchase>().apply {
        every { purchaseState } returns PurchaseState.PENDING
        every { purchaseTime } returns 2_000L
        every { purchaseToken } returns token
        every { isAcknowledged } returns false
        every { products } returns listOf(OurSku.Iap.PRO_UPGRADE.id)
    }

    // An unacknowledged purchase with its own token: the ack bookkeeping (log level, permanent
    // failure reports) is keyed per token, so tests need distinguishable ones.
    private fun unackedPurchase(
        token: String,
        time: Long = 1_000L,
        product: String = OurSku.Iap.PRO_UPGRADE.id,
    ) = mockk<Purchase>().apply {
        every { purchaseState } returns PurchaseState.PURCHASED
        every { purchaseTime } returns time
        every { purchaseToken } returns token
        every { isAcknowledged } returns false
        every { products } returns listOf(product)
        every { isAutoRenewing } returns false
    }

    private fun connection(
        refreshResults: List<Collection<Purchase>> = listOf(emptyList()),
        refreshComplete: Boolean = true,
        // Full refresh outcomes for the tests that care about provenance (confirmed set, commit
        // time, partial error); overrides the plain refreshResults shorthand.
        refreshes: List<BillingConnection.PurchaseRefresh>? = null,
        purchasesFlow: Flow<Collection<Purchase>> = flowOf(emptyList()),
        freshUpdatesFlow: Flow<BillingConnection.FreshUpdate> = emptyFlow(),
        failures: Flow<BillingResult> = emptyFlow(),
    ) = mockk<BillingConnection>().apply {
        coEvery { refreshPurchases() } returnsMany (
            refreshes ?: refreshResults.map {
                BillingConnection.PurchaseRefresh(
                    purchases = it,
                    confirmed = it,
                    hasConfirmedProPurchase = it.isNotEmpty(),
                    isComplete = refreshComplete,
                )
            }
            )
        every { purchases } returns purchasesFlow
        every { freshUpdates } returns freshUpdatesFlow
        every { purchaseFailures } returns failures
    }

    // An incomplete reconciliation: it committed what it found, but one product type couldn't be
    // checked. The default cause is NON-invalidating — the dead-binder codes are opted into.
    private fun partialRefresh(
        purchases: Collection<Purchase> = emptyList(),
        confirmed: Collection<Purchase> = emptyList(),
        hasConfirmedPro: Boolean = false,
        occurredAt: Long = 4242L,
        error: Throwable = GplayServiceUnavailableException(
            BillingClientException(result(BillingResponseCode.ERROR))
        ),
    ) = BillingConnection.PurchaseRefresh(
        purchases = purchases,
        confirmed = confirmed,
        hasConfirmedProPurchase = hasConfirmedPro,
        isComplete = false,
        occurredAt = occurredAt,
        partialError = error,
    )

    private fun completeRefresh(purchases: Collection<Purchase> = emptyList()) = BillingConnection.PurchaseRefresh(
        purchases = purchases,
        confirmed = purchases,
        hasConfirmedProPurchase = purchases.isNotEmpty(),
        isComplete = true,
    )

    // The real provider flow emits one connection and stays open for its lifetime -- flowOf() would
    // complete immediately, which the connect loop rightly treats as a connection failure.
    private fun providerOf(connection: BillingConnection): BillingConnectionProvider =
        mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                emit(connection)
                awaitCancellation()
            }
        }

    private fun TestScope.manager(connection: BillingConnection): BillingManager =
        BillingManager(backgroundScope, providerOf(connection), ackScheduler)

    private fun TestScope.manager(provider: BillingConnectionProvider): BillingManager =
        BillingManager(backgroundScope, provider, ackScheduler)

    // region launch failure mapping

    // A manager whose connection fails launchBillingFlow with the given launch-result code --
    // the path Play uses for immediate "buy" failures (returned result, not an exception).
    private fun TestScope.launchFailingManager(launchFailureCode: Int): BillingManager = manager(
        connection().apply {
            coEvery { launchBillingFlow(any(), any(), null) } throws
                BillingClientException(result(launchFailureCode))
        }
    )

    @Test fun `already-owned launch surfaces the mapped exception without a bug report`() = runTest2 {
        // The repo layer auto-handles this by restoring; it's an expected user state, not a defect.
        shouldThrow<ItemAlreadyOwnedBillingException> {
            launchFailingManager(BillingResponseCode.ITEM_ALREADY_OWNED)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `user cancel from the launch result stays silent bug-report-wise`() = runTest2 {
        shouldThrow<UserCanceledBillingException> {
            launchFailingManager(BillingResponseCode.USER_CANCELED)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `billing-unavailable maps to the service error without a bug report`() = runTest2 {
        shouldThrow<GplayServiceUnavailableException> {
            launchFailingManager(BillingResponseCode.BILLING_UNAVAILABLE)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `developer errors are rethrown and reported`() = runTest2 {
        shouldThrow<BillingClientException> {
            launchFailingManager(BillingResponseCode.DEVELOPER_ERROR)
                .startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 1) { Bugs.report(any()) }
    }

    @Test fun `an unavailable offer surfaces without a bug report`() = runTest2 {
        // Play withholding a product or offer is a merchandising state, not a defect on our side:
        // it must stay off the bug-report path (it is already a user-friendly BillingException).
        val manager = manager(
            connection().apply {
                coEvery { launchBillingFlow(any(), any(), null) } throws
                    OfferUnavailableBillingException(OurSku.Iap.PRO_UPGRADE, null)
            }
        )

        shouldThrow<OfferUnavailableBillingException> {
            manager.startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `cancellation during the iap flow is neither reported nor mapped`() = runTest2 {
        val manager = manager(
            connection().apply {
                coEvery { launchBillingFlow(any(), any(), null) } throws CancellationException("caller died")
            }
        )

        shouldThrow<CancellationException> {
            manager.startIapFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    // endregion

    // region fresh billing data

    @Test fun `manual refresh returns the fresh purchases`() = runTest2 {
        val owned = purchase()
        // First result feeds the initial per-connection refresh, second the manual one.
        val manager = manager(connection(refreshResults = listOf(emptyList(), listOf(owned))))

        manager.refresh() shouldBe BillingData(listOf(owned))
    }

    @Test fun `the connection's fresh updates pass through as fresh billing data`() = runTest2 {
        // The emissions themselves (commit order, query-confirmed-only, snapshot provenance) are
        // produced and tested at the BillingConnection level; the manager only re-shapes them.
        val owned = purchase()
        val manager = manager(
            connection(
                freshUpdatesFlow = flowOf(
                    BillingConnection.FreshUpdate(listOf(owned), isFullSnapshot = false, occurredAt = 4242L),
                ),
            )
        )

        val fresh = manager.freshBillingData.first()
        fresh.data shouldBe BillingData(listOf(owned))
        fresh.isFullSnapshot shouldBe false
        // The commit time must propagate verbatim: the entitlement layer keys episode ordering on it.
        fresh.occurredAt shouldBe 4242L
    }

    @Test fun `non-OK purchase events are exposed as purchase failures`() = runTest2 {
        val alreadyOwned = result(BillingResponseCode.ITEM_ALREADY_OWNED)
        val manager = manager(
            connection(failures = flowOf(alreadyOwned))
        )

        manager.purchaseFailures.first() shouldBe alreadyOwned
    }

    // endregion

    // region connect loop: retry, demand, invalidation

    @Test fun `connection retry waits out its backoff when nothing kicks it`() = runTest2 {
        var attempts = 0
        val healthy = connection()
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                if (attempts == 1) throw BillingException("Play is updating itself")
                emit(healthy)
                awaitCancellation()
            }
        }
        val manager = manager(provider)

        val t0 = currentTime
        manager.billingData.first()

        // First backoff is 2s; a passive subscriber sits it out (virtual time auto-advances).
        (currentTime - t0 >= 2_000) shouldBe true
        attempts shouldBe 2
    }

    @Test fun `a failed first attempt trips the failure-settled signal`() = runTest2 {
        // The connect loop swallows connection errors (it retries forever), so downstream flows
        // stay silent during an outage -- consumers need the explicit signal to settle their seed.
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                throw BillingException("Play is down")
            }
        }
        val manager = manager(provider)

        manager.isFailureSettled.first() shouldBe false
        runCurrent() // first attempt fails
        manager.isFailureSettled.first() shouldBe true
    }

    @Test fun `a healthy connection does not trip the failure-settled signal`() = runTest2 {
        // Success-settledness travels WITH the data: billingData emitting IS the settle signal,
        // there is no parallel success flag that could lead the data.
        val manager = manager(connection(purchasesFlow = flowOf(listOf(purchase()))))

        manager.billingData.first().purchases.size shouldBe 1
        manager.isFailureSettled.first() shouldBe false
    }

    @Test fun `a cold refresh that can't verify anything retries instead of starving billing`() = runTest2 {
        // "Nothing found AND a query failed" throws from refreshPurchases: the old onEach swallowed
        // that, leaving billingData/isFailureSettled starved forever with no retry.
        val bad = connection().apply {
            coEvery { refreshPurchases() } throws
                GplayServiceUnavailableException(RuntimeException("cold Play, broken queries"))
        }
        val good = connection()
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) bad else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)

        runCurrent() // attempt 1: connects, initial refresh fails -> connection failure
        manager.isFailureSettled.first() shouldBe true

        advanceTimeBy(2_001) // backoff, then attempt 2 heals
        manager.billingData.first() shouldBe BillingData(emptyList())
        attempts shouldBe 2
    }

    @Test fun `active demand cuts the reconnect backoff short`() = runTest2 {
        val owned = purchase()
        var attempts = 0
        val healthy = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                if (attempts == 1) throw BillingException("Play is updating itself")
                emit(healthy)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        runCurrent() // first connection attempt fails, the 2s backoff starts

        // A user tapping restore/buy right after fixing their Play situation must not wait out the
        // backoff timer -- the demand signal reconnects immediately.
        val refreshed = async { manager.refresh() }
        runCurrent()

        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2
        currentTime shouldBeLessThan 2_000L
    }

    @Test fun `demand during a failing connection attempt is not lost`() = runTest2 {
        val owned = purchase()
        val healthy = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                if (attempts == 1) {
                    delay(1_000) // demand arrives while this attempt is still in flight
                    throw BillingException("Play is updating itself")
                }
                emit(healthy)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        runCurrent() // attempt 1 in flight, suspended

        val refreshed = async { manager.refresh() } // demand lands mid-attempt
        runCurrent()
        advanceTimeBy(1_001) // attempt 1 fails; the pending demand must skip the backoff
        runCurrent()

        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2
        currentTime shouldBeLessThan 2_000L
    }

    @Test fun `demand served by a healthy connection does not skip a later backoff`() = runTest2 {
        var attempts = 0
        val die = CompletableDeferred<Unit>()
        val healthy = connection(refreshResults = List(4) { emptyList<Purchase>() })
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                when (attempts++) {
                    0 -> {
                        emit(healthy)
                        die.await()
                        throw BillingException("connection died")
                    }

                    else -> {
                        emit(healthy)
                        awaitCancellation()
                    }
                }
            }
        }
        val manager = manager(provider)
        runCurrent()

        val refreshed = async { manager.refresh() } // demand is served by the healthy connection
        runCurrent()
        refreshed.await()

        die.complete(Unit) // now the connection dies
        runCurrent()

        // The long-served demand must not short-circuit this backoff.
        advanceTimeBy(1_900)
        runCurrent()
        attempts shouldBe 1

        advanceTimeBy(200)
        runCurrent()
        attempts shouldBe 2
    }

    @Test fun `backoff streak resets after a successful connection`() = runTest2 {
        var attempts = 0
        val healthy = connection(refreshResults = List(4) { emptyList<Purchase>() })
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                when (attempts++) {
                    0 -> throw BillingException("fail 1") // streak 1 -> 2s backoff
                    1 -> {
                        emit(healthy) // success resets the streak...
                        throw BillingException("fail 2") // ...so this must back off 2s, not 8s
                    }

                    else -> {
                        emit(healthy)
                        awaitCancellation()
                    }
                }
            }
        }
        manager(provider)

        advanceTimeBy(2_001)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()

        // With a lifetime attempt counter the second backoff would be 8s and the third attempt
        // wouldn't have run yet.
        attempts shouldBe 3
    }

    @Test fun `unexpected provider completion does not tight-loop`() = runTest2 {
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow<BillingConnection> {
                attempts++
                // Completes normally without ever emitting -- the loop must treat this as a
                // failure WITH growing backoff, not spin reconnecting.
            }
        }
        manager(provider)

        runCurrent()
        attempts shouldBe 1
        advanceTimeBy(1_999)
        attempts shouldBe 1
        advanceTimeBy(10) // ~2s: streak 1 backoff expires
        runCurrent()
        attempts shouldBe 2
        // Streak grows (no successful connection in between): next backoff is 8s.
        advanceTimeBy(7_000)
        attempts shouldBe 2
        advanceTimeBy(1_100)
        runCurrent()
        attempts shouldBe 3
    }

    @Test fun `a connection that dies after succeeding backs off without spinning`() = runTest2 {
        var attempts = 0
        val healthy = connection(refreshResults = List(6) { emptyList<Purchase>() })
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(healthy)
                // Dies right after connecting: each cycle is a SUCCESS (streak resets) followed by
                // a failure, so the backoff stays at the 2s floor -- but never below it.
            }
        }
        manager(provider)

        runCurrent()
        attempts shouldBe 1
        advanceTimeBy(1_999)
        attempts shouldBe 1
        advanceTimeBy(10)
        runCurrent()
        attempts shouldBe 2
    }

    @Test fun `a hanging initial refresh times out into the retry`() = runTest2 {
        val bad = connection().apply {
            coEvery { refreshPurchases() } coAnswers { awaitCancellation() }
        }
        val good = connection()
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) bad else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)

        runCurrent() // attempt 1: refresh hangs
        advanceTimeBy(30_001) // initial-refresh timeout fires -> failure
        advanceTimeBy(2_001) // backoff, attempt 2

        manager.billingData.first() shouldBe BillingData(emptyList())
        attempts shouldBe 2
    }

    @Test fun `an action-level disconnect invalidates the connection`() = runTest2 {
        val owned = purchase()
        // Connection 1: healthy initial refresh, but the next action reports the binder dead —
        // arriving user-friendly-MAPPED, the way the refresh path actually delivers it.
        val dead = connection().apply {
            coEvery { refreshPurchases() } returns
                BillingConnection.PurchaseRefresh(emptyList(), isComplete = true) andThenThrows
                GplayServiceUnavailableException(
                    BillingClientException(result(BillingResponseCode.SERVICE_DISCONNECTED))
                )
        }
        val good = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) dead else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        runCurrent() // connected via connection 1

        // Play never delivered onBillingServiceDisconnected: without invalidation, connection 1
        // would stay installed and every later action would keep failing against a dead binder.
        shouldThrow<GplayServiceUnavailableException> { manager.refresh() }

        // The next action is fresh demand -- it skips the backoff and lands on connection 2.
        val refreshed = async { manager.refresh() }
        advanceUntilIdle()

        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2
    }

    // endregion

    // region connection failure feed

    // Drains the manager's connectionFailures (occurrence timestamps) into a list. Launched on
    // backgroundScope so it lives for the whole test.
    //
    // Drive it with runCurrent() (or a suspension of the test body), NEVER with advanceUntilIdle():
    // that one stops as soon as no FOREGROUND event is left and never runs backgroundScope work, so
    // a signal sent from the test body would sit unconsumed and the list would read empty.
    private fun TestScope.collectFailures(manager: BillingManager): List<Long> = mutableListOf<Long>().also { out ->
        backgroundScope.launch { manager.connectionFailures.collect { out.add(it) } }
    }

    @Test fun `a failure buffered before subscription is still delivered`() = runTest2 {
        // The consumer (UpgradeRepoGplay) and the connect loop both start in init with no ordering,
        // so a failure can fire before anyone subscribes — the UNLIMITED channel must buffer it.
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow { throw BillingException("Play is down") }
        }
        val manager = manager(provider)
        runCurrent() // attempt 1 fails and enqueues BEFORE any collector subscribes

        val failures = collectFailures(manager)
        runCurrent()
        failures.isNotEmpty() shouldBe true
    }

    @Test fun `a failed connection attempt emits a connection failure`() = runTest2 {
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow { throw BillingException("Play is down") }
        }
        val manager = manager(provider)
        val failures = collectFailures(manager)

        runCurrent() // attempt 1 fails (backoff not yet elapsed -> exactly one iteration)
        failures.size shouldBe 1
    }

    @Test fun `a failing initial refresh emits a connection failure`() = runTest2 {
        val bad = connection().apply {
            coEvery { refreshPurchases() } throws
                GplayServiceUnavailableException(RuntimeException("cold Play, broken queries"))
        }
        val manager = manager(bad)
        val failures = collectFailures(manager)

        runCurrent() // connects, initial refresh throws -> connection failure
        failures.size shouldBe 1
    }

    @Test fun `a hanging initial refresh emits a connection failure on timeout`() = runTest2 {
        val bad = connection().apply {
            coEvery { refreshPurchases() } coAnswers { awaitCancellation() }
        }
        val manager = manager(bad)
        val failures = collectFailures(manager)

        runCurrent() // attempt 1: refresh hangs
        advanceTimeBy(30_001) // initial-refresh timeout fires -> failure (backoff not yet elapsed)
        runCurrent()
        failures.size shouldBe 1
    }

    @Test fun `an unexpected provider completion emits a connection failure`() = runTest2 {
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow<BillingConnection> {
                // completes normally without ever emitting -> treated as a failure
            }
        }
        val manager = manager(provider)
        val failures = collectFailures(manager)

        runCurrent()
        failures.size shouldBe 1
    }

    @Test fun `an established connection alone does not emit`() = runTest2 {
        // A healthy connection that stays open must not look like a reconciliation failure.
        val manager = manager(connection())
        val failures = collectFailures(manager)

        runCurrent()
        failures shouldBe emptyList()
    }

    @Test fun `a cancelled connect loop does not emit`() = runTest2 {
        // Scope death is not an outage: the CancellationException path must stay silent.
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow { throw CancellationException("scope died") }
        }
        val manager = manager(provider)
        val failures = collectFailures(manager)

        runCurrent()
        failures shouldBe emptyList()
    }

    @Test fun `an action-level disconnect emits a connection failure`() = runTest2 {
        val owned = purchase()
        // Connection 1 connects, then a later action reports the binder dead (invalidating code) —
        // the invalidation tears the connection down, which the connect loop sees as a failure.
        val dead = connection().apply {
            coEvery { refreshPurchases() } returns
                BillingConnection.PurchaseRefresh(emptyList(), isComplete = true) andThenThrows
                GplayServiceUnavailableException(
                    BillingClientException(result(BillingResponseCode.SERVICE_DISCONNECTED))
                )
        }
        val good = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) dead else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        val failures = collectFailures(manager)
        runCurrent() // connected via connection 1 (success, no emit)

        shouldThrow<GplayServiceUnavailableException> { manager.refresh() }

        // A second action is fresh demand: it deterministically drives the loop THROUGH the
        // invalidation catch (where the failure is emitted) and onto connection 2, which heals.
        val refreshed = async { manager.refresh() }
        advanceUntilIdle()
        refreshed.await() shouldBe BillingData(listOf(owned))

        // The invalidated connection is a failed reconciliation. Exact count isn't pinned (the
        // reconnect may fail an extra iteration) and doesn't matter — downstream is idempotent.
        failures.isNotEmpty() shouldBe true
    }

    @Test fun `a strict gate failure does not feed the episode clock`() = runTest2 {
        // The gate surfaces its own failure to the user (fail-closed) and leaves the connection
        // installed. The episode clock is fed by the connect loop and by refresh() — a gate that
        // the user aborted mid-purchase is not a reconciliation outcome.
        val conn = connection(refreshes = listOf(completeRefresh(), partialRefresh()))
        val manager = manager(conn)
        val failures = collectFailures(manager)
        runCurrent() // connection established

        shouldThrow<Exception> { manager.refreshStrict() }
        runCurrent()

        failures shouldBe emptyList()
    }

    @Test fun `a strict gate failure on a dead connection still tears the connection down`() = runTest2 {
        // The gate's throw happens after useConnection already returned, so its dead-binder
        // detection can't fire — without the explicit invalidation connection 1 stays installed and
        // every later purchase check runs against the corpse. Feeding the episode clock still stays
        // off this path.
        val owned = purchase()
        val dead = connection(
            refreshes = listOf(
                completeRefresh(),
                partialRefresh(
                    occurredAt = 4242L,
                    error = GplayServiceUnavailableException(
                        BillingClientException(result(BillingResponseCode.SERVICE_DISCONNECTED))
                    ),
                ),
            ),
        )
        val good = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) dead else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        val failures = collectFailures(manager)
        runCurrent() // connection 1 established

        shouldThrow<Exception> { manager.refreshStrict() }
        // Teardown takes several dispatches and runs on backgroundScope — runCurrent, never
        // advanceUntilIdle, which would leave the connect loop untouched.
        runCurrent()

        // The next action is fresh demand: it skips the reconnect backoff and lands on connection 2.
        val refreshed = async { manager.refresh() }
        advanceUntilIdle()
        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2

        runCurrent()
        // The torn-down connection is a failed loop iteration (wall-clock stamped), but the gate's
        // own partial refresh must never reach the feed with its commit time.
        failures.isNotEmpty() shouldBe true
        failures shouldNotContain 4242L
    }

    @Test fun `a partial reconciliation without a confirmed pro purchase signals its commit time`() = runTest2 {
        // The pending-only cold start: Play answered for one type with a payment in progress, the
        // other failed. Nothing confirms Pro, so the grace episode clock must advance — stamped
        // with the refresh's COMMIT time, so a confirmation landing in between stays newer.
        val pending = pendingPurchase()
        val conn = connection(
            refreshes = listOf(partialRefresh(purchases = listOf(pending), occurredAt = 4242L)),
            purchasesFlow = flowOf(listOf(pending)),
        )
        val manager = manager(conn)
        val failures = collectFailures(manager)

        runCurrent()

        // Still published: a partial refresh is a usable connection, and starving billingData would
        // leave the screen at Loading forever.
        manager.billingData.first() shouldBe BillingData(
            purchases = emptyList(),
            pendingPurchases = listOf(pending),
        )
        failures shouldBe listOf(4242L)
    }

    @Test fun `a partial manual refresh signals too`() = runTest2 {
        // Manual Restore runs the same reconciliation as the connect loop — before this it was the
        // only path whose partial outcome silently vanished.
        val conn = connection(refreshes = listOf(completeRefresh(), partialRefresh(occurredAt = 7_777L)))
        val manager = manager(conn)
        val failures = collectFailures(manager)
        runCurrent()

        manager.refresh()
        runCurrent() // the collector lives on backgroundScope, which advanceUntilIdle would skip

        failures shouldBe listOf(7_777L)
    }

    @Test fun `a partial refresh that confirmed pro does not signal`() = runTest2 {
        val owned = purchase()
        val conn = connection(
            refreshes = listOf(
                completeRefresh(),
                partialRefresh(purchases = listOf(owned), confirmed = listOf(owned), hasConfirmedPro = true),
            ),
        )
        val manager = manager(conn)
        val failures = collectFailures(manager)
        runCurrent()

        manager.refresh()
        runCurrent()

        // Pro WAS confirmed by this round-trip; the failed sibling type proves nothing against it.
        failures shouldBe emptyList()
    }

    @Test fun `an invalidating partial refresh tears the connection down`() = runTest2 {
        // The dead-binder teardown used to ride refreshPurchases' throw path through useConnection.
        // A partial refresh returns instead of throwing, so without the explicit invalidation the
        // dead connection would stay installed for every later caller.
        val owned = purchase()
        val dead = connection(
            refreshes = listOf(
                partialRefresh(
                    purchases = listOf(owned),
                    confirmed = listOf(owned),
                    hasConfirmedPro = true,
                    error = GplayServiceUnavailableException(
                        BillingClientException(result(BillingResponseCode.SERVICE_DISCONNECTED))
                    ),
                ),
            ),
        )
        val good = connection(refreshResults = listOf(emptyList(), listOf(owned)))
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                emit(if (attempts == 1) dead else good)
                awaitCancellation()
            }
        }
        val manager = manager(provider)
        runCurrent() // connection 1 established; its partial refresh signals the invalidation

        // The next action is fresh demand: it skips the reconnect backoff and lands on connection 2.
        // await() is what drives the connect loop here — it runs on backgroundScope, which
        // advanceUntilIdle() alone would never touch.
        val refreshed = async { manager.refresh() }
        advanceUntilIdle()

        refreshed.await() shouldBe BillingData(listOf(owned))
        attempts shouldBe 2
    }

    // endregion

    // region pending purchases

    @Test fun `billing data splits owned purchases from pending payments`() = runTest2 {
        val owned = purchase()
        val pending = pendingPurchase()
        val manager = manager(connection(purchasesFlow = flowOf(listOf(owned, pending))))

        // The split is what keeps a payment in progress out of every entitlement decision while
        // still letting the UI show it.
        manager.billingData.first() shouldBe BillingData(
            purchases = listOf(owned),
            pendingPurchases = listOf(pending),
        )
    }

    @Test fun `a pending purchase is never acknowledged`() = runTest2 {
        val pending = pendingPurchase()
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(pending))
        advanceTimeBy(400_000)
        runCurrent()

        // Play rejects acking a pending purchase permanently: an unfiltered pass would fire a bug
        // report on every pass, forever, for a purchase that has nothing to acknowledge yet.
        acks shouldBe emptyList()
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test fun `a follow-up refresh after a partial publish recovers the full state`() = runTest2 {
        val pending = pendingPurchase()
        val owned = purchase()
        val purchases = purchasesFlow()
        val conn = connection(
            refreshes = listOf(
                partialRefresh(purchases = listOf(pending)),
                completeRefresh(listOf(owned)),
            ),
            purchasesFlow = purchases,
        )
        val manager = manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(pending))
        manager.billingData.first().pendingPurchases shouldBe listOf(pending)

        // The payment completed: the next reconciliation returns the owned purchase, no reconnect
        // needed (the partial one left a working connection installed).
        manager.refresh() shouldBe BillingData(listOf(owned))
    }

    @Test fun `refreshStrict fails closed on an incomplete refresh`() = runTest2 {
        val conn = connection(
            refreshes = listOf(
                completeRefresh(),
                partialRefresh(purchases = listOf(purchase()), confirmed = listOf(purchase())),
            ),
        )
        val manager = manager(conn)
        runCurrent()

        // A gate must not treat "one product type couldn't be checked" as "nothing else is owned":
        // that is exactly how a double purchase gets through.
        shouldThrow<GplayServiceUnavailableException> { manager.refreshStrict() }
    }

    @Test fun `refreshStrict returns the split data on a complete refresh`() = runTest2 {
        val owned = purchase()
        val pending = pendingPurchase()
        val conn = connection(refreshes = listOf(completeRefresh(), completeRefresh(listOf(owned, pending))))
        val manager = manager(conn)
        runCurrent()

        manager.refreshStrict() shouldBe BillingData(
            purchases = listOf(owned),
            pendingPurchases = listOf(pending),
        )
    }

    // endregion

    // region purchase acknowledgement

    // Controllable per-connection streams: the manager's purchases flow is distinctUntilChanged, so
    // a test must be able to drive the canonical list and the fresh-data signal independently.
    // replay=1 so an emission that lands before the shared flow's subscriber arrives isn't lost.
    private fun purchasesFlow() = MutableSharedFlow<Collection<Purchase>>(replay = 1, extraBufferCapacity = 8)

    private fun freshUpdatesFlow() = MutableSharedFlow<BillingConnection.FreshUpdate>(extraBufferCapacity = 8)

    // Scripted acknowledgePurchase: records every call (in order) and answers per invocation index,
    // so a test can script "fail, fail, succeed" or a hang without re-stubbing the connection.
    private fun BillingConnection.scriptAck(
        answer: suspend (call: Int, purchase: Purchase) -> BillingResult,
    ): MutableList<Purchase> {
        val calls = mutableListOf<Purchase>()
        coEvery { acknowledgePurchase(any()) } coAnswers {
            val purchase = firstArg<Purchase>()
            calls.add(purchase)
            answer(calls.size, purchase)
        }
        return calls
    }

    private fun transientAckFailure(): Nothing =
        throw BillingClientException(result(BillingResponseCode.SERVICE_UNAVAILABLE))

    @Test fun `a transient ack failure is retried without a new purchases emission`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> transientAckFailure() }
        manager(conn)
        runCurrent() // connection established

        purchases.tryEmit(listOf(unacked))
        runCurrent()
        acks.size shouldBe 1

        advanceTimeBy(3_001) // first inline backoff
        runCurrent()
        acks.size shouldBe 2

        advanceTimeBy(6_001) // second inline backoff
        runCurrent()
        acks.size shouldBe 3

        // Nothing new arrives from Play: the list is byte-identical (and deduped), so ONLY the
        // reschedule timer can re-drive the pass -- the old chain died here.
        advanceTimeBy(290_000)
        runCurrent()
        acks.size shouldBe 3

        advanceTimeBy(11_000)
        runCurrent()
        acks.size shouldBe 4
    }

    @Test fun `a fresh round-trip re-drives the pass for a byte-identical purchase list`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val fresh = freshUpdatesFlow()
        val conn = connection(purchasesFlow = purchases, freshUpdatesFlow = fresh)
        val acks = conn.scriptAck { _, _ -> transientAckFailure() }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        advanceTimeBy(10_000)
        runCurrent()
        acks.size shouldBe 3

        // A refresh that returns the SAME still-unacknowledged purchase: distinctUntilChanged drops
        // the list, so before the signal-driven re-drive this could never retry the failed ack.
        purchases.tryEmit(listOf(unacked))
        fresh.tryEmit(BillingConnection.FreshUpdate(listOf(unacked), isFullSnapshot = true))
        runCurrent()

        acks.size shouldBe 4
    }

    @Test fun `a partial fresh emission never becomes the retried purchase list`() = runTest2 {
        val iap = unackedPurchase("iap-token")
        val sub = unackedPurchase("sub-token", time = 2_000L, product = OurSku.Sub.PRO_UPGRADE.id)
        val purchases = purchasesFlow()
        val fresh = freshUpdatesFlow()
        val conn = connection(purchasesFlow = purchases, freshUpdatesFlow = fresh)
        val acks = conn.scriptAck { _, _ -> transientAckFailure() }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(iap))
        advanceTimeBy(10_000)
        runCurrent()
        acks.size shouldBe 3

        // A SUBS-only query commits a partial fresh update. It is a SIGNAL only -- using its payload
        // as the retried list would silently drop the failed IAP ack.
        fresh.tryEmit(BillingConnection.FreshUpdate(listOf(sub), isFullSnapshot = false))
        advanceTimeBy(10_000)
        runCurrent()
        acks.size shouldBe 6

        // Just past the 5min reschedule the first failing pass armed (t=9s + 300s).
        advanceTimeBy(289_500)
        runCurrent()
        acks.size shouldBe 7
        acks.map { it.purchaseToken }.toSet() shouldBe setOf("iap-token")
    }

    @Test fun `BILLING_UNAVAILABLE aborts the pass but the reschedule keeps re-attempting`() = runTest2 {
        val first = unackedPurchase("token-1")
        val second = unackedPurchase("token-2", time = 2_000L)
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> throw BillingClientException(result(BillingResponseCode.BILLING_UNAVAILABLE)) }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(first, second))
        advanceTimeBy(10_000)
        runCurrent()

        // Connection-level condition: no inline retries, and the second purchase isn't attempted.
        acks.size shouldBe 1

        // The old chain treated this as terminal and never acked anything again.
        advanceTimeBy(300_001)
        runCurrent()
        acks.size shouldBe 2
    }

    @Test fun `repeated failing passes never stack more than one reschedule`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val fresh = freshUpdatesFlow()
        val conn = connection(purchasesFlow = purchases, freshUpdatesFlow = fresh)
        val acks = conn.scriptAck { _, _ -> transientAckFailure() }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        advanceTimeBy(9_002) // pass 1 exhausts its attempts and arms the timer
        runCurrent()
        acks.size shouldBe 3

        // Two more failing passes while the timer is pending: each one re-arming would produce its
        // own extra re-drive later.
        fresh.tryEmit(BillingConnection.FreshUpdate(listOf(unacked), isFullSnapshot = true))
        advanceTimeBy(9_002)
        runCurrent()
        acks.size shouldBe 6

        fresh.tryEmit(BillingConnection.FreshUpdate(listOf(unacked), isFullSnapshot = true))
        advanceTimeBy(9_002)
        runCurrent()
        acks.size shouldBe 9

        // Exactly ONE timer-driven pass (3 attempts). Stacked timers would have fired around
        // t=318s and t=327s too, adding further passes inside this window.
        advanceTimeBy(282_000)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        acks.size shouldBe 12
    }

    @Test fun `a successful ack quiets its repeats and arms no reschedule`() = runTest2 {
        val recorder = RecordingLogger()
        Logging.install(recorder)
        try {
            val unacked = unackedPurchase("token-1")
            val purchases = purchasesFlow()
            val fresh = freshUpdatesFlow()
            val conn = connection(purchasesFlow = purchases, freshUpdatesFlow = fresh)
            val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
            manager(conn)
            runCurrent()

            purchases.tryEmit(listOf(unacked))
            runCurrent()
            acks.size shouldBe 1
            // First ack of a token is INFO...
            recorder.ackLogs(INFO) shouldBe 1
            recorder.ackLogs(DEBUG) shouldBe 0

            // The snapshot still says isAcknowledged=false until a fresh query supersedes it, so the
            // ack re-fires -- but as a quiet idempotent repeat.
            fresh.tryEmit(BillingConnection.FreshUpdate(listOf(unacked), isFullSnapshot = true))
            runCurrent()
            acks.size shouldBe 2
            recorder.ackLogs(INFO) shouldBe 1
            recorder.ackLogs(DEBUG) shouldBe 1

            // A pass without failures must not arm the timer.
            advanceTimeBy(400_000)
            runCurrent()
            acks.size shouldBe 2
        } finally {
            Logging.remove(recorder)
        }
    }

    @Test fun `a hanging ack times out each attempt and reschedules`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> awaitCancellation() }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        runCurrent()
        acks.size shouldBe 1

        advanceTimeBy(30_001) // per-attempt timeout fires
        runCurrent()
        acks.size shouldBe 1

        advanceTimeBy(3_001) // 3s backoff, then attempt 2
        runCurrent()
        acks.size shouldBe 2

        advanceTimeBy(36_001) // attempt 2 times out, 6s backoff, then attempt 3
        runCurrent()
        acks.size shouldBe 3

        advanceTimeBy(30_001) // attempt 3 times out -> transient -> reschedule
        runCurrent()
        acks.size shouldBe 3

        advanceTimeBy(300_001)
        runCurrent()
        acks.size shouldBe 4
    }

    @Test fun `waiting for a connection is bounded and counts as a failed attempt`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        // Connection 1 reports the binder dead on the ack, which uninstalls it. Connection 2 only
        // materializes minutes later -- the attempts in between must time out, not park forever.
        val dead = connection(purchasesFlow = purchases)
        val acks = dead.scriptAck { call, _ ->
            if (call == 1) throw BillingClientException(result(BillingResponseCode.SERVICE_DISCONNECTED))
            result(BillingResponseCode.OK)
        }
        val healthy = connection(purchasesFlow = purchases).apply {
            coEvery { acknowledgePurchase(any()) } coAnswers {
                acks.add(firstArg())
                result(BillingResponseCode.OK)
            }
        }
        var attempts = 0
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                attempts++
                if (attempts == 1) {
                    emit(dead)
                } else {
                    delay(200_000) // Play stays unreachable
                    emit(healthy)
                }
                awaitCancellation()
            }
        }
        manager(provider)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        runCurrent()
        acks.size shouldBe 1

        // Attempts 2 and 3 never reach a connection: each burns its 30s wait budget instead.
        advanceTimeBy(100_000)
        runCurrent()
        acks.size shouldBe 1

        // The pass ended (instead of hanging), so its reschedule can heal it once Play is back.
        advanceTimeBy(300_001)
        runCurrent()
        acks.size shouldBe 2
    }

    @Test fun `scope cancellation during an ack neither retries nor reschedules`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> awaitCancellation() }
        val ackScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        BillingManager(ackScope, providerOf(conn), ackScheduler)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        runCurrent()
        acks.size shouldBe 1

        ackScope.cancel("test over")
        advanceTimeBy(400_000)
        runCurrent()

        // Scope death is not an acknowledgement failure: no further attempt, no timer.
        acks.size shouldBe 1
    }

    @Test fun `a permanent ack failure is reported once and left to organic signals`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        val purchases = purchasesFlow()
        val fresh = freshUpdatesFlow()
        val conn = connection(purchasesFlow = purchases, freshUpdatesFlow = fresh)
        val acks = conn.scriptAck { _, _ -> throw BillingClientException(result(BillingResponseCode.DEVELOPER_ERROR)) }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unacked))
        advanceTimeBy(10_000)
        runCurrent()

        // Retrying a code Play will keep returning is pointless: one attempt, one report.
        acks.size shouldBe 1
        verify(exactly = 1) { Bugs.report(any()) }

        advanceTimeBy(310_000)
        runCurrent()
        acks.size shouldBe 1

        // Fresh Play data still re-attempts it -- the ack is never abandoned, only the timer is.
        fresh.tryEmit(BillingConnection.FreshUpdate(listOf(unacked), isFullSnapshot = true))
        runCurrent()
        acks.size shouldBe 2
        verify(exactly = 1) { Bugs.report(any()) }
    }

    @Test fun `an upstream purchases failure recovers and the ack chain keeps processing`() = runTest2 {
        val unacked = unackedPurchase("token-1")
        var subscriptions = 0
        val flaky = flow {
            subscriptions++
            emit(listOf(unacked))
            // The hot shareIn has no owner to restart it: without the source-level retry this kills
            // the sharing coroutine and every subscriber (incl. the ack collector) hangs forever.
            if (subscriptions == 1) throw RuntimeException("purchases source died")
            awaitCancellation()
        }
        val conn = connection(purchasesFlow = flaky)
        val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
        manager(conn)
        runCurrent()
        acks.size shouldBe 1

        advanceTimeBy(60_001)
        runCurrent()

        subscriptions shouldBe 2
        acks.size shouldBe 2
    }

    @Test fun `the ack collector survives a failure from the pass plumbing`() = runTest2 {
        var reads = 0
        val flakyPurchase = mockk<Purchase>().apply {
            every { purchaseState } returns PurchaseState.PURCHASED
            every { purchaseTime } returns 1_000L
            every { purchaseToken } returns "token-1"
            every { products } returns listOf(OurSku.Iap.PRO_UPGRADE.id)
            every { isAutoRenewing } returns false
            // Not a BillingException and not from the ack call itself: the belt is what keeps the
            // process-lifetime collector alive through it.
            every { isAcknowledged } answers {
                if (reads++ == 0) throw IllegalStateException("Play data unreadable") else false
            }
        }
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(flakyPurchase))
        runCurrent()
        acks.size shouldBe 0

        advanceTimeBy(60_001)
        runCurrent()
        acks.size shouldBe 1
    }

    // Captures the ack log lines so the INFO-then-DEBUG log-level contract can be asserted without
    // exposing the token bookkeeping.
    private class RecordingLogger : Logging.Logger {
        private val lines = mutableListOf<Pair<Logging.Priority, String>>()

        override fun log(priority: Logging.Priority, tag: String, message: String, metaData: Map<String, Any>?) {
            synchronized(lines) { lines.add(priority to message) }
        }

        fun ackLogs(priority: Logging.Priority): Int = synchronized(lines) {
            lines.count { it.first == priority && it.second.startsWith("Acknowledging purchase:") }
        }
    }

    // endregion

    // region ack safety net sweep (ensureAllAcknowledged)

    @Test fun `sweep acknowledges what its refresh returned and reports COMPLETE`() = runTest2 {
        val unacked = unackedPurchase("token-sweep")
        val conn = connection(refreshes = listOf(completeRefresh(), completeRefresh(listOf(unacked))))
        val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
        val manager = manager(conn)
        runCurrent()

        // The ack happens IN this call, not via the async collector: the worker needs the
        // happens-before to report success.
        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.COMPLETE
        acks.map { it.purchaseToken } shouldBe listOf("token-sweep")
    }

    @Test fun `sweep with nothing to acknowledge reports COMPLETE`() = runTest2 {
        val conn = connection(refreshes = listOf(completeRefresh(), completeRefresh(listOf(purchase()))))
        val manager = manager(conn)
        runCurrent()

        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.COMPLETE
    }

    @Test fun `sweep reports RETRY when acks keep failing transiently`() = runTest2 {
        val unacked = unackedPurchase("token-sweep")
        val conn = connection(refreshes = listOf(completeRefresh(), completeRefresh(listOf(unacked))))
        conn.scriptAck { _, _ -> transientAckFailure() }
        val manager = manager(conn)
        runCurrent()

        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.RETRY
    }

    @Test fun `sweep reports PERMANENT_FAILURE on a permanently rejected ack`() = runTest2 {
        val unacked = unackedPurchase("token-sweep")
        val conn = connection(refreshes = listOf(completeRefresh(), completeRefresh(listOf(unacked))))
        conn.scriptAck { _, _ -> throw BillingClientException(result(BillingResponseCode.DEVELOPER_ERROR)) }
        val manager = manager(conn)
        runCurrent()

        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.PERMANENT_FAILURE
    }

    @Test fun `sweep reports RETRY on an incomplete refresh even with nothing to ack`() = runTest2 {
        val conn = connection(refreshes = listOf(completeRefresh(), partialRefresh()))
        val manager = manager(conn)
        runCurrent()

        // A failed product-type query may be hiding an unacknowledged purchase of that type: the
        // worker must come back instead of reporting the net complete.
        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.RETRY
    }

    @Test fun `sweep reports RETRY when the refresh itself fails`() = runTest2 {
        val conn = connection(refreshes = listOf(completeRefresh()))
        val manager = manager(conn)
        runCurrent()
        coEvery { conn.refreshPurchases() } throws BillingException("Play down")

        manager.ensureAllAcknowledged() shouldBe BillingManager.AckSweepResult.RETRY
    }

    @Test fun `an ack pass arms the safety net before attempting, with the newest refund deadline`() = runTest2 {
        val order = mutableListOf<String>()
        coEvery { ackScheduler.armForUnackedPurchases(any()) } coAnswers { order.add("arm:${firstArg<Long>()}") }
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        conn.scriptAck { _, _ ->
            order.add("ack")
            transientAckFailure()
        }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unackedPurchase("token-1", time = 5_000L), unackedPurchase("token-2", time = 9_000L)))
        // runCurrent, NOT advanceUntilIdle: the scripted ack keeps failing, so idle-advancing would
        // spin through the 5-minute re-drive cycles forever. The first attempt runs undelayed.
        runCurrent()

        // Armed (and awaited) BEFORE the first attempt: a process death during the inline retries
        // must still leave the persistent net in place. Deadline derives from the NEWEST purchase.
        order.first() shouldBe "arm:${9_000L + BillingManager.ACK_SAFETY_NET_DEADLINE_MS}"
        order.count { it == "ack" } shouldBeGreaterThan 0
    }

    @Test fun `a failing safety net arm never blocks the ack pass`() = runTest2 {
        coEvery { ackScheduler.armForUnackedPurchases(any()) } throws RuntimeException("workmanager broken")
        val purchases = purchasesFlow()
        val conn = connection(purchasesFlow = purchases)
        val acks = conn.scriptAck { _, _ -> result(BillingResponseCode.OK) }
        manager(conn)
        runCurrent()

        purchases.tryEmit(listOf(unackedPurchase("token-1")))
        runCurrent()

        // The net is an extra layer: WorkManager being broken must never stop the ack itself.
        acks.map { it.purchaseToken } shouldBe listOf("token-1")
    }

    // endregion
}
