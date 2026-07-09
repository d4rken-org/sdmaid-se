package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.Purchase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BillingConnectionTest : BaseTest() {

    private fun purchase(time: Long) = mockk<Purchase>().apply { every { purchaseTime } returns time }

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
}
