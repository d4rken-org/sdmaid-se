package eu.darken.sdmse.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingClientException
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnection
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.assertions.throwables.shouldThrow
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    private fun connection(
        refreshResults: List<Collection<Purchase>> = listOf(emptyList()),
        refreshComplete: Boolean = true,
        purchasesFlow: Flow<Collection<Purchase>> = flowOf(emptyList()),
        freshUpdatesFlow: Flow<BillingConnection.FreshUpdate> = emptyFlow(),
        failures: Flow<BillingResult> = emptyFlow(),
    ) = mockk<BillingConnection>().apply {
        coEvery { refreshPurchases() } returnsMany
            refreshResults.map { BillingConnection.PurchaseRefresh(it, isComplete = refreshComplete) }
        every { purchases } returns purchasesFlow
        every { freshUpdates } returns freshUpdatesFlow
        every { purchaseFailures } returns failures
    }

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
        BillingManager(backgroundScope, providerOf(connection))

    private fun TestScope.manager(provider: BillingConnectionProvider): BillingManager =
        BillingManager(backgroundScope, provider)

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

    @Test fun `a failed first attempt marks billing as settled`() = runTest2 {
        // The connect loop swallows connection errors (it retries forever), so downstream flows
        // stay silent during an outage -- gates like isProSettled need the explicit signal.
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                throw BillingException("Play is down")
            }
        }
        val manager = manager(provider)

        manager.isSettled.first() shouldBe false
        runCurrent() // first attempt fails
        manager.isSettled.first() shouldBe true
    }

    @Test fun `a cold refresh that can't verify anything retries instead of starving billing`() = runTest2 {
        // "Nothing found AND a query failed" throws from refreshPurchases: the old onEach swallowed
        // that, leaving billingData/isSettled starved forever with no retry.
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
        manager.isSettled.first() shouldBe true

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

    @Test fun `a non-invalidating action error does not emit`() = runTest2 {
        // A strict querySubscriptions failure whose code is NOT invalidating leaves the connection
        // installed, so no connect-loop iteration fails and nothing feeds the episode clock.
        val conn = connection().apply {
            coEvery { querySubscriptions() } throws BillingClientException(result(BillingResponseCode.ERROR))
        }
        val manager = manager(conn)
        val failures = collectFailures(manager)
        runCurrent() // connection established

        shouldThrow<Exception> { manager.querySubscriptions() }
        advanceUntilIdle()

        failures shouldBe emptyList()
    }

    // endregion
}
