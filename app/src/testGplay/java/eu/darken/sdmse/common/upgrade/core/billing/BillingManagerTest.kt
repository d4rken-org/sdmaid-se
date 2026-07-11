package eu.darken.sdmse.common.upgrade.core.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingClientException
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnection
import eu.darken.sdmse.common.upgrade.core.billing.client.BillingConnectionProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class BillingManagerTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val activity = mockk<Activity>()

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

    // A manager whose connection fails launchBillingFlow with the given launch-result code —
    // the path Play uses for immediate "buy" failures (returned result, not an exception).
    private fun manager(launchFailureCode: Int): BillingManager {
        val connection = mockk<BillingConnection>().apply {
            coEvery { refreshPurchases() } returns emptyList()
            every { purchases } returns emptyFlow()
            coEvery { launchBillingFlow(any(), any(), null) } throws BillingClientException(result(launchFailureCode))
        }
        val provider = mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flowOf(connection)
        }
        return BillingManager(scope, provider)
    }

    @Test
    fun `already-owned launch surfaces the mapped exception without a bug report`() = runTest2 {
        // The repo layer auto-handles this by restoring; it's an expected user state, not a defect.
        val manager = manager(BillingResponseCode.ITEM_ALREADY_OWNED)

        shouldThrow<ItemAlreadyOwnedBillingException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test
    fun `user cancel from the launch result stays silent bug-report-wise`() = runTest2 {
        val manager = manager(BillingResponseCode.USER_CANCELED)

        shouldThrow<UserCanceledBillingException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test
    fun `billing-unavailable maps to the service error without a bug report`() = runTest2 {
        val manager = manager(BillingResponseCode.BILLING_UNAVAILABLE)

        shouldThrow<GplayServiceUnavailableException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 0) { Bugs.report(any()) }
    }

    @Test
    fun `developer errors are rethrown and reported`() = runTest2 {
        val manager = manager(BillingResponseCode.DEVELOPER_ERROR)

        shouldThrow<BillingClientException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 1) { Bugs.report(any()) }
    }

    // A provider whose first connection attempt fails; subsequent attempts succeed. Drives the
    // retryWhen backoff in BillingManager.connection under virtual time.
    private fun flakyProvider(connection: BillingConnection): BillingConnectionProvider {
        var attempts = 0
        return mockk<BillingConnectionProvider>().apply {
            every { this@apply.connection } returns flow {
                if (attempts++ == 0) throw BillingException("first connect fails")
                emit(connection)
            }
        }
    }

    private fun healthyConnection(): BillingConnection = mockk<BillingConnection>().apply {
        coEvery { refreshPurchases() } returns emptyList()
        // Must emit (not emptyFlow): billingData.first() in these tests completes on this emission.
        every { purchases } returns flowOf(emptyList())
    }

    @Test
    fun `connection retry waits out its backoff when nothing kicks it`() = runTest2 {
        val manager = BillingManager(backgroundScope, flakyProvider(healthyConnection()))

        val t0 = currentTime
        manager.billingData.first()

        // First backoff is 30s; a passive subscriber sits it out (virtual time auto-advances).
        (currentTime - t0 >= 30_000) shouldBe true
    }

    @Test
    fun `an explicit billing action wakes the connection retry out of its backoff`() = runTest2 {
        val manager = BillingManager(backgroundScope, flakyProvider(healthyConnection()))

        // Passive subscriber triggers the first (failing) attempt; the retry enters its backoff.
        val warmup = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            manager.billingData.first()
        }
        runCurrent()

        val t0 = currentTime
        manager.refresh() // useConnection kicks the backoff -> immediate reconnect
        (currentTime - t0 < 30_000) shouldBe true
        warmup.join()
    }
}
