package eu.darken.sdmse.common.upgrade.core.billing

import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import eu.darken.sdmse.common.upgrade.core.OurSku
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PurchasedSkuTest : BaseTest() {

    @Test fun `rendering a purchased sku keeps identifying purchase data out of logs`() {
        // Debug recordings are attached to support emails: the purchase token and order ID must not
        // travel with them, while the entitlement-diagnosis fields must.
        val purchase = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Iap.PRO_UPGRADE.id)
            every { purchaseState } returns PurchaseState.PURCHASED
            every { isAcknowledged } returns true
            every { isAutoRenewing } returns false
            every { purchaseTime } returns 1_000L
            every { purchaseToken } returns "SENTINEL-TOKEN"
            every { orderId } returns "SENTINEL-ORDER"
        }

        val rendered = PurchasedSku(OurSku.Iap.PRO_UPGRADE, purchase).toString()

        rendered shouldNotContain "SENTINEL-TOKEN"
        rendered shouldNotContain "SENTINEL-ORDER"
        rendered shouldContain OurSku.Iap.PRO_UPGRADE.id
        rendered shouldContain "acknowledged=true"
    }
}
