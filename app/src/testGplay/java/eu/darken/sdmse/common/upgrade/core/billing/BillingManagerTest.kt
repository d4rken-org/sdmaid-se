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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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
    fun `failed launch surfaces as the already-owned user error and is reported`() = runTest2 {
        val manager = manager(BillingResponseCode.ITEM_ALREADY_OWNED)

        shouldThrow<ItemAlreadyOwnedBillingException> {
            manager.startIapFlow(activity, OurSku.Iap.PRO_UPGRADE, null)
        }
        verify(exactly = 1) { Bugs.report(any()) }
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
}
