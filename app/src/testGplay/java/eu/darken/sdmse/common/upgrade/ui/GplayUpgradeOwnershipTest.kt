package eu.darken.sdmse.common.upgrade.ui

import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import io.mockk.every
import io.mockk.mockk
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class GplayUpgradeOwnershipTest : BaseTest() {

    private fun mockPurchase(skuId: String, autoRenewing: Boolean = false): Purchase = mockk<Purchase>().apply {
        every { products } returns listOf(skuId)
        every { isAutoRenewing } returns autoRenewing
        every { purchaseTime } returns 1234L
    }

    private fun info(vararg purchases: Purchase) = UpgradeRepoGplay.Info(
        false,
        BillingData(purchases = purchases.toList()),
        null,
    )

    @Test
    fun `no purchases means no ownership`() {
        val ownership = info().toOwnership()

        ownership.hasIap.shouldBeFalse()
        ownership.subscription.shouldBeNull()
        ownership.ownsAnything.shouldBeFalse()
    }

    @Test
    fun `one-time purchase maps to iap ownership`() {
        val ownership = info(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")).toOwnership()

        ownership.hasIap.shouldBeTrue()
        ownership.subscription.shouldBeNull()
        ownership.ownsAnything.shouldBeTrue()
    }

    @Test
    fun `renewing subscription maps to renewing ownership`() {
        val ownership = info(mockPurchase("upgrade.pro", autoRenewing = true)).toOwnership()

        ownership.hasIap.shouldBeFalse()
        ownership.subscription.shouldNotBeNull().isAutoRenewing.shouldBeTrue()
    }

    @Test
    fun `cancelled but still running subscription maps to non-renewing ownership`() {
        val ownership = info(mockPurchase("upgrade.pro", autoRenewing = false)).toOwnership()

        ownership.subscription.shouldNotBeNull().isAutoRenewing.shouldBeFalse()
    }

    @Test
    fun `owning both products is represented as both`() {
        val ownership = info(
            mockPurchase("eu.darken.sdmse.iap.upgrade.pro"),
            mockPurchase("upgrade.pro", autoRenewing = false),
        ).toOwnership()

        ownership.hasIap.shouldBeTrue()
        ownership.subscription.shouldNotBeNull().isAutoRenewing.shouldBeFalse()
    }

    @Test
    fun `multiple subscription records stay renewing if any record still renews`() {
        // A retained purchase event can coexist with fresher query-cache data for the same sub;
        // display must err on the renewing side — the purchase gate re-verifies freshly anyway.
        val ownership = info(
            mockPurchase("upgrade.pro", autoRenewing = false),
            mockPurchase("upgrade.pro", autoRenewing = true),
        ).toOwnership()

        ownership.subscription.shouldNotBeNull().isAutoRenewing.shouldBeTrue()
    }

    @Test
    fun `unknown products do not create ownership`() {
        val ownership = info(mockPurchase("some.unknown.sku")).toOwnership()

        ownership.ownsAnything shouldBe false
    }

    private fun pendingInfo(vararg pending: Purchase) = UpgradeRepoGplay.Info(
        false,
        BillingData(purchases = emptyList(), pendingPurchases = pending.toList()),
        null,
    )

    @Test
    fun `a pending payment sets the pending flag`() {
        pendingInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")).toPendingFlag().shouldBeTrue()
        pendingInfo(mockPurchase("upgrade.pro")).toPendingFlag().shouldBeTrue()
    }

    @Test
    fun `no pending payment leaves the flag off`() {
        info(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")).toPendingFlag().shouldBeFalse()
        pendingInfo().toPendingFlag().shouldBeFalse()
    }

    @Test
    fun `an unknown pending product does not set the flag`() {
        // Nothing to explain and nothing to lock: a product this app doesn't sell isn't the Pro
        // upgrade the user is waiting for.
        pendingInfo(mockPurchase("some.unknown.sku")).toPendingFlag().shouldBeFalse()
    }

    @Test
    fun `a pending payment is not ownership`() {
        val ownership = pendingInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")).toOwnership()

        ownership.hasIap.shouldBeFalse()
        ownership.subscription.shouldBeNull()
        ownership.ownsAnything.shouldBeFalse()
    }
}
