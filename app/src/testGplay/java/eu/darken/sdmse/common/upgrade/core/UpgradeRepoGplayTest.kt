package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import eu.darken.sdmse.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import eu.darken.sdmse.common.upgrade.core.billing.UserCanceledBillingException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    private lateinit var lastProAtMock: DataStoreValue<Long>
    private lateinit var lastProSkuMock: DataStoreValue<String>

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. The Unconfined scope runs the
    // init collectors (grace stamping, async already-owned) eagerly against the stubbed flows.
    private fun repo(
        lastProAt: Long,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        freshBillingData: BillingManager.FreshData? = null,
        purchaseFailures: List<BillingResult> = emptyList(),
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(billingData)
        every { billingManager.freshBillingData } returns
            (freshBillingData?.let { flowOf(it) } ?: emptyFlow())
        every { billingManager.isSettled } returns flowOf(true)
        every { billingManager.purchaseFailures } returns
            if (purchaseFailures.isEmpty()) emptyFlow() else flowOf(*purchaseFailures.toTypedArray())
        lastProAtMock = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(lastProAt)
        }
        every { billingCache.lastProStateAt } returns lastProAtMock
        lastProSkuMock = mockk<DataStoreValue<String>>(relaxed = true).apply {
            every { flow } returns flowOf(lastSku)
        }
        every { billingCache.lastProStateSku } returns lastProSkuMock
        coJustRun { billingCache.stampLastProState(any(), any()) }
        return UpgradeRepoGplay(scope, billingManager, billingCache)
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

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

    @Test fun `legacy empty last SKU falls back to the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        // Existing installs have a timestamp but no recorded SKU: they keep the old 7-day window
        // until the next successful query records one.
        repo(lastProAt = twentyDaysAgo, lastSku = "").restorePurchaseNow().isPro shouldBe false
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

    // region grace stamping provenance

    @Test fun `mapped billing data does not stamp the grace timestamp`() = runTest2 {
        // The reactive mapping runs on replayed (stale) data too, e.g. when the upgrade screen is
        // reopened in a long-lived process -- that must not extend the grace window.
        val repo = repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        repo.upgradeInfo.first { it.isPro }.isPro shouldBe true
        repo.upgradeInfo.first { it.isPro }.isPro shouldBe true

        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `fresh billing data stamps the grace cache`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(
                BillingData(setOf(proPurchase())),
                isFullSnapshot = true,
            ),
        )

        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, any()) }
    }

    @Test fun `fresh data without a known pro SKU does not stamp`() = runTest2 {
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(unknown)), isFullSnapshot = true),
        )

        coVerify(exactly = 0) { billingCache.stampLastProState(any(), any()) }
    }

    @Test fun `a non-full snapshot does not downgrade the IAP grace class`() = runTest2 {
        // A purchase event or partial refresh proves ownership of what it contains, not the
        // absence of the permanent IAP -- the 30d window must not silently become 7d.
        val subOnly = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 1_000L,
            lastSku = OurSku.Iap.PRO_UPGRADE.id,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(subOnly)), isFullSnapshot = false),
        )

        // Timestamp refreshes, but the stored SKU keeps the permanent IAP's 30-day class.
        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, any()) }
    }

    @Test fun `a full snapshot with only a subscription stamps the subscription class`() = runTest2 {
        // Play confirmed the IAP is really gone: downgrading the grace class is now legitimate.
        val subOnly = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
            every { purchaseTime } returns 1_000L
        }
        repo(
            lastProAt = 1_000L,
            lastSku = OurSku.Iap.PRO_UPGRADE.id,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(subOnly)), isFullSnapshot = true),
        )

        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Sub.PRO_UPGRADE.id, any()) }
    }

    // endregion

    // region buy flow + already-owned recovery

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

    @Test fun `user cancel during the buy flow stays silent`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            UserCanceledBillingException(RuntimeException("launch result"))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `cancellation of the buy flow never reaches the error callback`() = runTest2 {
        // A cancelled coroutine is not an error: surfacing it would show a spurious dialog.
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws CancellationException("scope died")

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `other buy flow failures reach the error callback`() = runTest2 {
        val failure = RuntimeException("launch failed")
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws failure

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single() shouldBe failure
    }

    @Test fun `async already-owned purchase event triggers a silent restore`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(
            lastProAt = 0L,
            purchaseFailures = listOf(result(BillingResponseCode.ITEM_ALREADY_OWNED)),
        )

        coVerify(exactly = 1) { billingManager.refresh() }
    }

    @Test fun `other async purchase failures do not trigger a restore`() = runTest2 {
        repo(
            lastProAt = 0L,
            purchaseFailures = listOf(result(BillingResponseCode.DEVELOPER_ERROR)),
        )

        coVerify(exactly = 0) { billingManager.refresh() }
    }

    @Test fun `overlapping already-owned recoveries coalesce into one restore`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        val gate = CompletableDeferred<Unit>()
        coEvery { billingManager.refresh() } coAnswers {
            gate.await()
            BillingData(setOf(proPurchase()))
        }

        val errors = mutableListOf<Throwable>()
        val repo = repo(lastProAt = 0L)
        // Two buy taps race into the already-owned recovery while the first restore is in flight.
        repo.launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }
        repo.launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        repo.autoRestoreBusy.first() shouldBe true
        gate.complete(Unit)

        // Both triggers joined the SAME restore -- one Play query, no stacked recoveries.
        coVerify(exactly = 1) { billingManager.refresh() }
        errors shouldBe emptyList()
        repo.autoRestoreBusy.first() shouldBe false
    }

    @Test fun `auto restore busy state rises and falls around the recovery`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        val gate = CompletableDeferred<Unit>()
        coEvery { billingManager.refresh() } coAnswers {
            gate.await()
            BillingData(setOf(proPurchase()))
        }

        val repo = repo(lastProAt = 0L)
        repo.autoRestoreBusy.first() shouldBe false

        repo.launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { }
        repo.autoRestoreBusy.first() shouldBe true

        gate.complete(Unit)
        repo.autoRestoreBusy.first() shouldBe false
    }

    // endregion
}
