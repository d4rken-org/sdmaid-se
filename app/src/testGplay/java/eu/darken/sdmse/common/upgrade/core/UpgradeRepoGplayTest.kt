package eu.darken.sdmse.common.upgrade.core

import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class UpgradeRepoGplayTest : BaseTest() {

    @BeforeEach
    fun setup() {

    }

    @AfterEach
    fun teardown() {

    }


    @Test fun `test upgrade info pro status mapping`() {
        UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = null
        ).apply {
            isPro shouldBe false
            type shouldBe UpgradeRepo.Type.GPLAY
        }

        UpgradeRepoGplay.Info(
            gracePeriod = true,
            billingData = null
        ).isPro shouldBe true

        val info = UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = BillingData(
                purchases = setOf(
                    mockk<Purchase>().apply {
                        every { products } returns OurSku.PRO_SKUS.map { it.id }
                        every { purchaseTime } returns Instant.parse("2023-12-10T00:00:00Z").toEpochMilli()
                    }
                )
            )
        )
        info.isPro shouldBe true
        info.upgradedAt shouldBe Instant.parse("2023-12-10T00:00:00Z")
        info.type
    }
}