package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import eu.darken.sdmse.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.Instant

class UpgradeRepoGplayTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val billingManager = mockk<BillingManager>()
    private val billingCache = mockk<BillingCache>()

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. billingData is stubbed only
    // because the upgradeInfo flow references it at construction; it is never collected here.
    private fun repo(lastProAt: Long, lastSku: String = ""): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(BillingData(emptySet()))
        val lastPro = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(lastProAt)
        }
        every { billingCache.lastProStateAt } returns lastPro
        val lastProSku = mockk<DataStoreValue<String>>(relaxed = true).apply {
            every { flow } returns flowOf(lastSku)
        }
        every { billingCache.lastProStateSku } returns lastProSku
        return UpgradeRepoGplay(scope, billingManager, billingCache)
    }

    private fun proPurchase() = mockk<Purchase>().apply {
        every { products } returns OurSku.PRO_SKUS.map { it.id }
        every { purchaseTime } returns Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
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

    @Test fun `grace period is 7 days`() {
        // Guards against the unit error where 7 * 24 * 60 * 1000 (2.8h) was used instead of 7 days,
        // which dropped paying users to non-Pro within hours of a transient empty/failed billing response.
        UpgradeRepoGplay.GRACE_PERIOD_MS shouldBe 604_800_000L
    }

    @Test fun `restore returns pro when a purchase is found`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val expired = System.currentTimeMillis() - UpgradeRepoGplay.GRACE_PERIOD_MS - 1_000
        repo(lastProAt = expired).restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `restore keeps pro within grace when the query errors`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `restore rethrows the error when it happens outside grace`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        shouldThrow<RuntimeException> {
            repo(lastProAt = 0L).restorePurchaseNow()
        }
    }

    @Test fun `permanent IAP keeps grace well beyond the subscription window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        // 20 days ago: past the 7-day subscription window, but within the 30-day IAP window.
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Iap.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `subscription grace expires after the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Sub.PRO_UPGRADE.id)
            .restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `IAP grace window is longer than the subscription window`() {
        (UpgradeRepoGplay.GRACE_PERIOD_IAP_MS > UpgradeRepoGplay.GRACE_PERIOD_MS) shouldBe true
        UpgradeRepoGplay.GRACE_PERIOD_IAP_MS shouldBe Duration.ofDays(30).toMillis()
    }

    @Test fun `preferredProSku prefers the permanent IAP when both are owned`() {
        val iap = PurchasedSku(OurSku.Iap.PRO_UPGRADE, mockk<Purchase>())
        val sub = PurchasedSku(OurSku.Sub.PRO_UPGRADE, mockk<Purchase>())

        UpgradeRepoGplay.preferredProSku(listOf(sub, iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(iap))?.id shouldBe OurSku.Iap.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(listOf(sub))?.id shouldBe OurSku.Sub.PRO_UPGRADE.id
        UpgradeRepoGplay.preferredProSku(emptyList()) shouldBe null
    }

    @Test fun `already-owned buy attempt silently restores the purchase instead of erroring`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore finds nothing`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val errors = mutableListOf<Throwable>()
        // Grace expired -> the restore can't rescue the entitlement either.
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore itself errors`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }
}
