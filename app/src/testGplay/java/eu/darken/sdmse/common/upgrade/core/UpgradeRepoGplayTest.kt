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
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private lateinit var proUnconfirmedMock: DataStoreValue<Long>

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. The Unconfined scope runs the
    // init collectors (grace stamping, async already-owned) eagerly against the stubbed flows.
    private fun repo(
        lastProAt: Long,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        purchaseFailures: List<BillingResult> = emptyList(),
        proUnconfirmedSince: Long = 0L,
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(billingData)
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
        proUnconfirmedMock = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(proUnconfirmedSince)
        }
        every { billingCache.proUnconfirmedSince } returns proUnconfirmedMock
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

    @Test fun `explicit restore stamps the grace cache, sku before timestamp`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).restorePurchaseNow().isPro shouldBe true

        coVerifyOrder {
            lastProSkuMock.update(any())
            lastProAtMock.update(any())
        }
    }

    @Test fun `background refresh stamps the grace cache from the fresh result`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).refresh()

        coVerify(exactly = 1) { lastProAtMock.update(any()) }
    }

    @Test fun `reactive emissions stamp once via the init collector, the map never stamps`() = runTest2 {
        // billingData carries a pro purchase: the persistent init collector stamps exactly once.
        // Collecting upgradeInfo runs the map at least twice (onStart-null + pro data) — the map is
        // read-only now, so if it still stamped the count would exceed one.
        val repo = repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        repo.upgradeInfo.first { it.isPro }.isPro shouldBe true

        coVerify(exactly = 1) { lastProAtMock.update(any()) }
    }

    @Test fun `fresh empty result during grace starts the unconfirmed episode clock`() = runTest2 {
        // Default billingData is empty: the init collector sees a fresh empty reconciliation
        // while grace is active -> the episode clock is stamped (set-if-unset inside the update).
        repo(lastProAt = System.currentTimeMillis() - 1_000)

        coVerify(exactly = 1) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `fresh empty result without recent pro does not start the clock`() = runTest2 {
        repo(lastProAt = 0L)

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `confirming a purchase closes the unconfirmed episode`() = runTest2 {
        repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        coVerifyOrder {
            lastProSkuMock.update(any())
            lastProAtMock.update(any())
            proUnconfirmedMock.update(any())
        }
    }

    @Test fun `failed refresh during grace records an unconfirmed episode`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        // Update #1 comes from the init collector (fresh empty result during grace), update #2
        // from the failed explicit refresh — a sustained outage must feed the clock too.
        repo(lastProAt = System.currentTimeMillis() - 1_000).refresh()

        coVerify(exactly = 2) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `already-owned restore that only yields grace still surfaces the error`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val errors = mutableListOf<Throwable>()
        // Grace is active: the restore's Info reports isPro=true, but no actual purchase came
        // back — the entitlement Play claims is owned is still missing, so the dialog must show.
        repo(lastProAt = System.currentTimeMillis() - 1_000)
            .launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `unconfirmed episode stamp is set-if-unset with stale and future replacement`() = runTest2 {
        // The transform's "now" is frozen when the recorder runs (repo construction), so the
        // bounds must bracket construction, not the assertion time.
        val beforeConstruction = System.currentTimeMillis()
        val lastProAt = beforeConstruction - 1_000
        repo(lastProAt = lastProAt)
        val afterConstruction = System.currentTimeMillis()

        val transform = slot<(Long) -> Long?>()
        coVerify { proUnconfirmedMock.update(capture(transform)) }

        // Unset -> stamped with "now".
        val stamped = transform.captured(0L)!!
        (stamped in beforeConstruction..afterConstruction) shouldBe true
        // A stamp from the current episode (newer than the confirmation) is kept.
        val current = lastProAt + 500
        transform.captured(current) shouldBe current
        // A stale stamp from an earlier episode (older than the confirmation) is replaced.
        transform.captured(lastProAt - 5_000) shouldBe stamped
        // A future stamp (clock moved backwards since it was written) is replaced.
        val future = System.currentTimeMillis() + Duration.ofDays(1).toMillis()
        transform.captured(future) shouldBe stamped
    }

    @Test fun `confirmation clears the unconfirmed episode by writing zero`() = runTest2 {
        repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        val transform = slot<(Long) -> Long?>()
        coVerify { proUnconfirmedMock.update(capture(transform)) }

        transform.captured(123_456L) shouldBe 0L
    }

    @Test fun `future confirmation timestamp does not start an episode`() = runTest2 {
        // Clock moved backwards: lastProStateAt is "in the future". Without the sinceConfirm > 0
        // guard this would pass the window check and re-stamp the episode on every attempt.
        repo(lastProAt = System.currentTimeMillis() + Duration.ofDays(1).toMillis())

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `timed-out refresh records an unconfirmed episode`() = runTest2 {
        coEvery { billingManager.refresh() } coAnswers {
            delay(Duration.ofMinutes(5).toMillis()) // longer than the 30s refresh timeout
            BillingData(emptySet())
        }

        // Update #1 from the init collector (fresh empty during grace), #2 from the timeout path —
        // a hanging connection is also a fresh attempt that couldn't confirm Pro.
        repo(lastProAt = System.currentTimeMillis() - 1_000).refresh()

        coVerify(exactly = 2) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `already-owned restore returning a different sku still surfaces the error`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        val subOnly = mockk<Purchase>().apply {
            every { products } returns listOf(OurSku.Sub.PRO_UPGRADE.id)
            every { purchaseTime } returns 1234L
        }
        coEvery { billingManager.refresh() } returns BillingData(setOf(subOnly))

        val errors = mutableListOf<Throwable>()
        // The restore found the SUB, but Play claimed the IAP is owned — not reconciled.
        repo(lastProAt = 0L).launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
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
}
