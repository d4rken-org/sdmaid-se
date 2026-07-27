package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.sdmse.common.upgrade.core.OurSku
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BillingClientExtensionsTest : BaseTest() {

    @Test
    fun `redacted keeps the diagnostic fields and drops the identifying ones`() {
        val purchase = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Iap.PRO_UPGRADE.id)
            every { purchaseState } returns PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { isAutoRenewing } returns false
            every { purchaseTime } returns 1234L
        }

        val rendered = purchase.redacted()

        rendered shouldContain OurSku.Iap.PRO_UPGRADE.id
        rendered shouldContain "acknowledged=true"
        // Never touches the accessors that carry the token / order ID, so they cannot reach a
        // support log even indirectly through toString().
        rendered shouldNotContain "token"
        rendered shouldNotContain "orderId"
    }

    @Test
    fun `redacted never throws, so a diagnostic cannot break the billing path`() {
        // These lambdas run on the billing path whenever a recording is active. A formatter that
        // throws would replace a real billing result -- or a real billing exception -- with a
        // diagnostics failure.
        val hostile = mockk<Purchase>().apply {
            every { products } throws RuntimeException("nope")
        }

        hostile.redacted() shouldContain "unreadable"
        listOf(hostile).redacted() shouldContain "unreadable"
    }

    @Test
    fun `an empty collection renders as empty, not as null`() {
        emptyList<Purchase>().redacted() shouldBe "[]"
    }
}
