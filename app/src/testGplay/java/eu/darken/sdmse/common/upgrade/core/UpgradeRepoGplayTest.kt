package eu.darken.sdmse.common.upgrade.core

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.BillingManager
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.ItemAlreadyOwnedBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PendingPurchaseBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PurchasedSku
import eu.darken.sdmse.common.upgrade.core.billing.UserCanceledBillingException
import eu.darken.sdmse.common.upgrade.core.billing.work.PurchaseAckScheduler
import eu.darken.sdmse.main.core.CurriculumVitae
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.time.Duration
import java.time.Instant

class UpgradeRepoGplayTest : BaseTest() {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val billingManager = mockk<BillingManager>()
    private val billingCache = mockk<BillingCache>()
    private val curriculumVitae = mockk<CurriculumVitae>(relaxed = true)
    private val ackScheduler = mockk<PurchaseAckScheduler>(relaxed = true)
    private lateinit var lastProAtMock: DataStoreValue<Long>
    private lateinit var lastProSkuMock: DataStoreValue<String>
    private lateinit var proUnconfirmedMock: DataStoreValue<Long>

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. The Unconfined scope runs the
    // init collectors (grace stamping, async already-owned) eagerly against the stubbed flows.
    private fun repo(
        lastProAt: Long,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        billingDataFlow: Flow<BillingData>? = null,
        freshBillingData: BillingManager.FreshData? = null,
        purchaseFailures: List<BillingResult> = emptyList(),
        purchaseFailureFlow: Flow<BillingResult>? = null,
        proUnconfirmedSince: Long = 0L,
        connectionFailures: Flow<Long> = emptyFlow(),
        failureSettled: Flow<Boolean> = flowOf(false),
        // The cache-backed flows are dereferenced during construction, so a test that needs a
        // FAILING one has to install it here — overriding the mock afterwards is too late.
        lastProAtFlow: Flow<Long>? = null,
        proUnconfirmedFlow: Flow<Long>? = null,
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns (billingDataFlow ?: flowOf(billingData))
        every { billingManager.freshBillingData } returns
            (freshBillingData?.let { flowOf(it) } ?: emptyFlow())
        every { billingManager.connectionFailures } returns connectionFailures
        every { billingManager.isFailureSettled } returns failureSettled
        // A hot flow lets a test drive async already-owned events AFTER construction, e.g. while an
        // earlier recovery is still in flight; the cold list covers the static wiring cases.
        every { billingManager.purchaseFailures } returns when {
            purchaseFailureFlow != null -> purchaseFailureFlow
            purchaseFailures.isEmpty() -> emptyFlow()
            else -> flowOf(*purchaseFailures.toTypedArray())
        }
        lastProAtMock = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns (lastProAtFlow ?: flowOf(lastProAt))
        }
        every { billingCache.lastProStateAt } returns lastProAtMock
        lastProSkuMock = mockk<DataStoreValue<String>>(relaxed = true).apply {
            every { flow } returns flowOf(lastSku)
        }
        every { billingCache.lastProStateSku } returns lastProSkuMock
        proUnconfirmedMock = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns (proUnconfirmedFlow ?: flowOf(proUnconfirmedSince))
        }
        every { billingCache.proUnconfirmedSince } returns proUnconfirmedMock
        coJustRun { billingCache.stampLastProState(any(), any()) }
        return UpgradeRepoGplay(scope, billingManager, billingCache, curriculumVitae, ackScheduler)
    }

    private fun result(code: Int): BillingResult = BillingResult.newBuilder().setResponseCode(code).build()

    // Starts a launch without blocking the test body: launchBillingFlowNow suspends until the
    // launch resolved, but these tests drive gates/async events while it is in flight. The
    // Unconfined scope keeps the old eager semantics (runs until the first real suspension).
    private fun UpgradeRepoGplay.startLaunch(onError: (Throwable) -> Unit = {}) {
        scope.launch { launchBillingFlowNow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null, onError) }
    }

    private fun proPurchase() = mockk<Purchase>().apply {
        every { products } returns OurSku.PRO_SKUS.map { it.id }
        every { purchaseTime } returns Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
    }

    // A payment Play is still processing. Lands in BillingData.pendingPurchases, never in
    // purchases — the split happens at the billing layer, this is what the repo receives.
    private fun pendingPurchase(productId: String = OurSku.Iap.PRO_UPGRADE.id) = mockk<Purchase>().apply {
        every { products } returns listOf(productId)
        every { purchaseTime } returns 1_000L
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

    // region pending payments

    @Test fun `a pending payment is mapped but grants nothing`() {
        val info = UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = BillingData(purchases = emptySet(), pendingPurchases = setOf(pendingPurchase())),
        )

        info.pendingSkus shouldBe listOf(OurSku.Iap.PRO_UPGRADE)
        // The whole point of the split: visible, never an entitlement.
        info.upgrades shouldBe emptyList()
        info.isPro shouldBe false
        info.upgradedAt shouldBe null
    }

    @Test fun `a pending payment for an unknown product is dropped`() {
        UpgradeRepoGplay.Info(
            gracePeriod = false,
            billingData = BillingData(
                purchases = emptySet(),
                pendingPurchases = setOf(pendingPurchase("some.unknown.product")),
            ),
        ).pendingSkus shouldBe emptyList()
    }

    @Test fun `the grace-substituted info keeps the pending payment visible`() = runTest2 {
        // The audience that needs the explanation most: Pro is running on grace while Play is still
        // processing the payment that will renew it. Dropping the data here (as the grace branch
        // used to) left them with a silent screen.
        coEvery { billingManager.refresh() } returns BillingData(emptySet(), setOf(pendingPurchase()))

        val outcome = repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow()

        outcome.info.isPro shouldBe true
        outcome.info.pendingSkus shouldBe listOf(OurSku.Iap.PRO_UPGRADE)
    }

    @Test fun `a restore that only finds a pending payment reports it without pro`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet(), setOf(pendingPurchase()))

        val outcome = repo(lastProAt = 0L).restorePurchaseNow()

        outcome.shouldBeInstanceOf<UpgradeRepoGplay.RestoreOutcome.Checked>()
        outcome.info.isPro shouldBe false
        outcome.info.pendingSkus shouldBe listOf(OurSku.Iap.PRO_UPGRADE)
    }

    @Test fun `a manual restore over a partial refresh still advances the unconfirmed episode`() = runTest2 {
        // The restore itself succeeds (a pending payment IS an answer), so nothing on this path
        // throws — the episode clock is fed by the manager's reconciliation signal instead, which
        // carries the refresh's commit time.
        val failures = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val confirmedAt = System.currentTimeMillis() - 1_000
        coEvery { billingManager.refresh() } returns BillingData(emptySet(), setOf(pendingPurchase()))
        val repo = repo(lastProAt = confirmedAt, connectionFailures = failures)

        repo.restorePurchaseNow().info.isPro shouldBe true
        failures.emit(confirmedAt + 500)
        advanceUntilIdle()

        coVerify { proUnconfirmedMock.update(any()) }
    }

    @Test fun `verifyPurchaseStateNow fails closed instead of substituting grace`() = runTest2 {
        val boom = GplayServiceUnavailableException(RuntimeException("one product type failed"))
        coEvery { billingManager.refreshStrict() } throws boom

        // Even a recent owner gets the error: a gate that can't verify must not let a purchase
        // through on the strength of a grace window.
        shouldThrow<GplayServiceUnavailableException> {
            repo(lastProAt = System.currentTimeMillis() - 1_000).verifyPurchaseStateNow()
        }
    }

    @Test fun `verifyPurchaseStateNow reports the fresh split state`() = runTest2 {
        coEvery { billingManager.refreshStrict() } returns BillingData(
            purchases = setOf(proPurchase()),
            pendingPurchases = setOf(pendingPurchase(OurSku.Sub.PRO_UPGRADE.id)),
        )

        val info = repo(lastProAt = 0L).verifyPurchaseStateNow()

        info.upgrades.map { it.sku } shouldBe OurSku.PRO_SKUS.toList()
        info.pendingSkus shouldBe listOf(OurSku.Sub.PRO_UPGRADE)
        info.isSettled shouldBe true
    }

    @Test fun `already-owned recovery reports a pending payment instead of restore tips`() = runTest2 {
        // Play refuses to re-sell a product whose payment it is still processing. The already-owned
        // dialog would tell the user to restore, which cannot help.
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet(), setOf(pendingPurchase()))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors.single().shouldBeInstanceOf<PendingPurchaseBillingException>()
    }

    // endregion

    @Test fun `grace period is 7 days`() {
        // Guards against the unit error where 7 * 24 * 60 * 1000 (2.8h) was used instead of 7 days,
        // which dropped paying users to non-Pro within hours of a transient empty/failed billing response.
        UpgradeRepoGplay.GRACE_PERIOD_MS shouldBe 604_800_000L
    }

    @Test fun `restore returns pro when a purchase is found`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))

        repo(lastProAt = 0L).restorePurchaseNow().info.isPro shouldBe true
    }

    @Test fun `restore keeps pro within grace when the query comes back empty`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().info.isPro shouldBe true
    }

    @Test fun `restore is not pro when the query is empty and grace has expired`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val expired = System.currentTimeMillis() - UpgradeRepoGplay.GRACE_PERIOD_MS - 1_000
        repo(lastProAt = expired).restorePurchaseNow().info.isPro shouldBe false
    }

    @Test fun `restore keeps pro within grace when the query errors`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().info.isPro shouldBe true
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
            .restorePurchaseNow().info.isPro shouldBe true
    }

    @Test fun `subscription grace expires after the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        repo(lastProAt = twentyDaysAgo, lastSku = OurSku.Sub.PRO_UPGRADE.id)
            .restorePurchaseNow().info.isPro shouldBe false
    }

    @Test fun `legacy empty last SKU falls back to the short window`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(emptySet())
        val twentyDaysAgo = System.currentTimeMillis() - Duration.ofDays(20).toMillis()

        // Existing installs have a timestamp but no recorded SKU: they keep the old 7-day window
        // until the next successful query records one.
        repo(lastProAt = twentyDaysAgo, lastSku = "").restorePurchaseNow().info.isPro shouldBe false
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

    @Test fun `a confirmation stamps the grace cache with the fresh data's occurrence time`() = runTest2 {
        // The confirmation anchor must be the success's COMMIT time, not processing-now: it's what
        // BillingCache compares against to decide which episode to close and how a later failure
        // orders against this success.
        val occurredAt = 7_777_000L
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(
                BillingData(setOf(proPurchase())),
                isFullSnapshot = true,
                occurredAt = occurredAt,
            ),
        )

        coVerify(exactly = 1) { billingCache.stampLastProState(OurSku.Iap.PRO_UPGRADE.id, occurredAt) }
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
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore finds nothing`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val errors = mutableListOf<Throwable>()
        // Grace expired -> the restore can't rescue the entitlement either.
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `already-owned buy attempt falls back to the error dialog when restore itself errors`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
    }

    @Test fun `user cancel during the buy flow stays silent`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            UserCanceledBillingException(RuntimeException("launch result"))

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `cancellation of the buy flow never reaches the error callback`() = runTest2 {
        // A cancelled coroutine is not an error: surfacing it would show a spurious dialog.
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws CancellationException("scope died")

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors shouldBe emptyList()
    }

    @Test fun `other buy flow failures reach the error callback`() = runTest2 {
        val failure = RuntimeException("launch failed")
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws failure

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        errors.single() shouldBe failure
    }

    @Test fun `a launch that never resolves times out instead of parking the busy guard`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } coAnswers { awaitCancellation() }
        val errors = mutableListOf<Throwable>()
        val repo = repo(lastProAt = 0L).apply { launchTimeoutMs = 50L }
        repo.launchBillingFlowNow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }
        errors.single().shouldBeInstanceOf<GplayServiceUnavailableException>()
        repo.purchaseLaunchSku.value shouldBe null
    }

    @Test fun `wasEverPro degrades to false when the cache is unreadable`() = runTest2 {
        val repo = repo(lastProAt = 0L, lastProAtFlow = flow { throw IOException("disk full") })
        repo.wasEverPro.first() shouldBe false
    }

    @Test fun `proUnconfirmedSince degrades to zero when the cache is unreadable`() = runTest2 {
        val repo = repo(lastProAt = 0L, proUnconfirmedFlow = flow { throw IOException("disk full") })
        repo.proUnconfirmedSince.first() shouldBe 0L
    }

    @Test fun `fresh empty full snapshot during grace starts the unconfirmed episode clock`() = runTest2 {
        repo(
            lastProAt = System.currentTimeMillis() - 1_000,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = true),
        )

        coVerify(exactly = 1) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `a partial empty fresh result does not start the clock`() = runTest2 {
        // A partial snapshot (purchase event, single-type query) proves presence of what it
        // contains, never the absence of anything else — it must not start an episode.
        repo(
            lastProAt = System.currentTimeMillis() - 1_000,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = false),
        )

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `fresh empty result without recent pro does not start the clock`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = true),
        )

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `confirming a purchase closes the unconfirmed episode in the stamp transaction`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(proPurchase())), isFullSnapshot = true),
        )

        // The episode clear rides the same atomic cache transaction as the confirmation stamp —
        // no separate write on the episode value.
        coVerify(exactly = 1) { billingCache.stampLastProState(any(), any()) }
        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `failed refresh during grace records an unconfirmed episode`() = runTest2 {
        // A fresh attempt that FAILED also can't confirm Pro — a sustained outage (queries
        // erroring, never empty-succeeding) must feed the clock too.
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = System.currentTimeMillis() - 1_000).refresh()

        coVerify(exactly = 1) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `timed-out refresh records an unconfirmed episode`() = runTest2 {
        coEvery { billingManager.refresh() } coAnswers {
            delay(Duration.ofMinutes(5).toMillis()) // longer than the 30s refresh timeout
            BillingData(emptySet())
        }

        // A hanging connection is also a fresh attempt that couldn't confirm Pro.
        repo(lastProAt = System.currentTimeMillis() - 1_000).refresh()

        coVerify(exactly = 1) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `future confirmation timestamp does not start an episode`() = runTest2 {
        // Clock moved backwards: lastProStateAt is "in the future". Without the sinceConfirm > 0
        // guard this would pass the window check and re-stamp the episode on every attempt.
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        repo(lastProAt = System.currentTimeMillis() + Duration.ofDays(1).toMillis()).refresh()

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `unconfirmed episode stamp is set-if-unset with stale and future replacement`() = runTest2 {
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")

        // The transform's "now" is frozen when the recorder runs, so the bounds must bracket the
        // triggering refresh, not the assertion time.
        val beforeTrigger = System.currentTimeMillis()
        val lastProAt = beforeTrigger - 1_000
        repo(lastProAt = lastProAt).refresh()
        val afterTrigger = System.currentTimeMillis()

        val transform = slot<(Long) -> Long?>()
        coVerify { proUnconfirmedMock.update(capture(transform)) }

        // Unset -> stamped with "now".
        val stamped = transform.captured(0L)!!
        (stamped in beforeTrigger..afterTrigger) shouldBe true
        // A stamp from the current episode (newer than the confirmation) is kept.
        val current = lastProAt + 500
        transform.captured(current) shouldBe current
        // A stale stamp from an earlier episode (older than the confirmation) is replaced.
        transform.captured(lastProAt - 5_000) shouldBe stamped
        // A future stamp (clock moved backwards since it was written) is replaced.
        val future = System.currentTimeMillis() + Duration.ofDays(1).toMillis()
        transform.captured(future) shouldBe stamped
    }

    @Test fun `a connection-failure feed emission during grace starts the episode`() = runTest2 {
        // Connect-loop failures don't reach an explicit refresh() caller; the feed must still
        // advance the episode clock for a recently-Pro user. Driven as a live hot emission AFTER
        // construction, so it proves the collector reacts to real events, not just wiring.
        val failures = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val confirmedAt = System.currentTimeMillis() - 1_000
        repo(lastProAt = confirmedAt, connectionFailures = failures)

        val failedAt = confirmedAt + 500 // after the confirmation -> a genuine post-confirm outage
        failures.emit(failedAt)
        advanceUntilIdle()

        val transform = slot<(Long) -> Long?>()
        coVerify { proUnconfirmedMock.update(capture(transform)) }
        // The episode is stamped with the failure's OWN occurrence time, not processing-now.
        transform.captured(0L) shouldBe failedAt
    }

    @Test fun `a connection-failure feed emission without recent pro does not start the episode`() = runTest2 {
        val failures = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        repo(lastProAt = 0L, connectionFailures = failures)

        failures.emit(System.currentTimeMillis())
        advanceUntilIdle()

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `a connection failure superseded by a later confirmation does not reopen the episode`() = runTest2 {
        // The blocker case: a failure buffered during an outage is consumed only AFTER a later retry
        // succeeded and stamped Pro (lastProStateAt). Because the event carries its own time, which
        // is older than the confirmation, it must be dropped rather than reopening a closed episode.
        val failures = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val confirmedAt = System.currentTimeMillis()
        repo(lastProAt = confirmedAt, connectionFailures = failures)

        failures.emit(confirmedAt - 5_000) // the failure happened before the confirmation
        advanceUntilIdle()

        coVerify(exactly = 0) { proUnconfirmedMock.update(any()) }
    }

    @Test fun `repeated connection failures keep the original episode timestamp`() = runTest2 {
        val failures = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val confirmedAt = System.currentTimeMillis() - 10_000
        repo(lastProAt = confirmedAt, connectionFailures = failures)

        val firstFailure = confirmedAt + 1_000
        val secondFailure = confirmedAt + 2_000
        failures.emit(firstFailure)
        failures.emit(secondFailure)
        advanceUntilIdle()

        // Both failures are processed (neither is dropped), so the count is exactly two.
        val transforms = mutableListOf<(Long) -> Long?>()
        coVerify(exactly = 2) { proUnconfirmedMock.update(capture(transforms)) }
        transforms.size shouldBe 2
        // Set-if-unset: the second failure, applied to the first episode's stamp, preserves it —
        // the episode start never moves once opened.
        transforms[1](firstFailure) shouldBe firstFailure
    }

    @Test fun `already-owned restore that only yields grace still surfaces the error`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val errors = mutableListOf<Throwable>()
        // Grace is active: the restore's Info reports isPro=true, but no actual purchase came
        // back — the entitlement Play claims is owned is still missing, so the dialog must show.
        repo(lastProAt = System.currentTimeMillis() - 1_000)
            .startLaunch { errors.add(it) }

        errors.single().shouldBeInstanceOf<ItemAlreadyOwnedBillingException>()
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
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

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

    @Test fun `overlapping already-owned recoveries coalesce into one restore`() = runTest2 {
        coEvery { billingManager.startIapFlow(any(), any(), null) } throws
            ItemAlreadyOwnedBillingException(RuntimeException("launch result"))
        val gate = CompletableDeferred<Unit>()
        coEvery { billingManager.refresh() } coAnswers {
            gate.await()
            BillingData(setOf(proPurchase()))
        }

        val errors = mutableListOf<Throwable>()
        // The two recovery triggers must arrive on DIFFERENT paths: a second launch call would be
        // coalesced by the launch-level single-flight and never reach a recovery at all.
        val asyncFailures = MutableSharedFlow<BillingResult>(extraBufferCapacity = 1)
        val repo = repo(lastProAt = 0L, purchaseFailureFlow = asyncFailures)

        // Trigger 1: a buy tap whose launch result comes back ITEM_ALREADY_OWNED.
        repo.startLaunch { errors.add(it) }
        repo.autoRestoreBusy.first() shouldBe true

        // Trigger 2: Play's async already-owned event lands while that restore is still in flight.
        // The subscriber check keeps the coalescing assertion honest: a dropped emission would
        // otherwise look exactly like a successfully coalesced one.
        asyncFailures.subscriptionCount.first() shouldBe 1
        asyncFailures.emit(result(BillingResponseCode.ITEM_ALREADY_OWNED))
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        // Both triggers joined the SAME restore -- one Play query, no stacked recoveries.
        coVerify(exactly = 1) { billingManager.refresh() }
        // Trigger 1 resolved: the restore reconciled the SKU (no dialog) and the launch released.
        errors shouldBe emptyList()
        repo.purchaseLaunchSku.first() shouldBe null
        repo.autoRestoreBusy.first() shouldBe false

        // Trigger 2 resolved too: the async collector is serial, so it could only process this
        // second event if its await on the joined restore had returned.
        asyncFailures.emit(result(BillingResponseCode.ITEM_ALREADY_OWNED))
        advanceUntilIdle()
        coVerify(exactly = 2) { billingManager.refresh() }
    }

    @Test fun `unknown-only purchases still fall through to the grace check`() = runTest2 {
        // A purchase list containing only products this app doesn't know maps to zero upgrades:
        // it must not take the "has purchases" branch and deny a recently-Pro user their grace.
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        coEvery { billingManager.refresh() } returns BillingData(setOf(unknown))

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().info.isPro shouldBe true
    }

    @Test fun `unknown-only purchases without recent grace are not pro`() = runTest2 {
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        coEvery { billingManager.refresh() } returns BillingData(setOf(unknown))

        repo(lastProAt = 0L).restorePurchaseNow().info.isPro shouldBe false
    }

    @Test fun `a known purchase is pro even when the grace cache is unreadable`() = runTest2 {
        // Known purchases are decided before any grace-cache read: failing local storage (full
        // disk) must not turn a confirmed purchase into an error episode.
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))
        val repo = repo(lastProAt = 0L)
        every { lastProAtMock.flow } returns flow { throw IOException("disk full") }

        repo.restorePurchaseNow().info.isPro shouldBe true
    }

    @Test fun `upgradeInfo recovers after a transient grace cache failure`() = runTest2 {
        val repo = repo(lastProAt = 0L)
        var reads = 0
        every { lastProAtMock.flow } returns flow {
            if (reads++ == 0) throw IOException("disk full")
            emit(0L)
        }

        // First the mapping fails (and the fallback probe fails too, since it reads the same
        // storage) -> an error Info keeps the flow alive; after the capped delay it recovers.
        val infos = repo.upgradeInfo.take(2).toList()
        infos[0].error shouldNotBe null
        // A local storage failure is a definitive best-knowledge outcome -> settled.
        infos[0].isSettled shouldBe true
        infos[1].error shouldBe null
        infos[1].isPro shouldBe false
        // Monotonic across the retry resubscribe: the re-emitted null seed must not regress an
        // already-settled stream back to unsettled (the runningReduce latch).
        infos[1].isSettled shouldBe true
    }

    @Test fun `a persistently failing grace cache does not kill upgradeInfo`() = runTest2 {
        val repo = repo(lastProAt = 0L)
        every { lastProAtMock.flow } returns flow { throw IOException("disk full") }

        // The retry predicate's own cache probe fails as well -- the flow must still emit instead
        // of terminating.
        repo.upgradeInfo.first().error shouldNotBe null
    }

    // region settledness travels with the data

    @Test fun `ownership and settledness arrive in the same emission`() = runTest2 {
        // The core structural guarantee: no settled-non-Pro emission may precede the owner
        // emission on a healthy cold start — settled-by-success IS the data emission.
        val repo = repo(lastProAt = 0L, billingData = BillingData(setOf(proPurchase())))

        val infos = repo.upgradeInfo.take(2).toList()
        infos[0].isPro shouldBe false
        infos[0].isSettled shouldBe false
        infos[1].isPro shouldBe true
        infos[1].isSettled shouldBe true
    }

    @Test fun `the pre-settle seed is unsettled`() = runTest2 {
        val repo = repo(lastProAt = 0L, billingDataFlow = emptyFlow())

        repo.upgradeInfo.first().apply {
            isPro shouldBe false
            isSettled shouldBe false
        }
    }

    @Test fun `a connect-loop failure settles the seed`() = runTest2 {
        val repo = repo(lastProAt = 0L, billingDataFlow = emptyFlow(), failureSettled = flowOf(true))

        repo.upgradeInfo.first { it.isSettled }.isPro shouldBe false
    }

    @Test fun `a connect-loop failure settles the grace mapping for a recent owner`() = runTest2 {
        val repo = repo(
            lastProAt = System.currentTimeMillis() - 1_000,
            billingDataFlow = emptyFlow(),
            failureSettled = flowOf(true),
        )

        repo.upgradeInfo.first { it.isSettled }.isPro shouldBe true
    }

    @Test fun `a settled flip on identical content passes distinctUntilChanged`() = runTest2 {
        val failureSettled = MutableStateFlow(false)
        val repo = repo(lastProAt = 0L, billingDataFlow = emptyFlow(), failureSettled = failureSettled)

        val infos = async { repo.upgradeInfo.take(2).toList() }
        advanceUntilIdle()
        failureSettled.value = true

        infos.await().let {
            it[0].isSettled shouldBe false
            it[1].isSettled shouldBe true
            it[1].isPro shouldBe it[0].isPro
        }
    }

    @Test fun `restore results are settled`() = runTest2 {
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))
        repo(lastProAt = 0L).restorePurchaseNow().info.isSettled shouldBe true

        // The grace fallback is a definitive substitution for a real round-trip -> also settled.
        coEvery { billingManager.refresh() } throws RuntimeException("Play unavailable")
        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().info.apply {
            isPro shouldBe true
            isSettled shouldBe true
        }
    }

    @Test fun `a Play error absorbed by grace is reported as inconclusive`() = runTest2 {
        // Entitlement is unchanged (grace still keeps Pro), but the lookup never landed. Without
        // this the UI can't tell it apart from a successful empty query and would tell a recent
        // owner that Play was checked and had nothing.
        val boom = RuntimeException("Play unavailable")
        coEvery { billingManager.refresh() } throws boom

        val outcome = repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow()

        outcome.shouldBeInstanceOf<UpgradeRepoGplay.RestoreOutcome.Inconclusive>()
        outcome.cause shouldBe boom
        outcome.info.isPro shouldBe true
    }

    @Test fun `a successful empty query is reported as checked even when grace keeps pro`() = runTest2 {
        // Mirror image of the test above: Play DID answer, so escalation copy is warranted.
        coEvery { billingManager.refresh() } returns BillingData(emptySet())

        val outcome = repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow()

        outcome.shouldBeInstanceOf<UpgradeRepoGplay.RestoreOutcome.Checked>()
        outcome.info.isPro shouldBe true
    }

    @Test fun `after a failure the seed settles before the data lands - accepted residual`() = runTest2 {
        // Documented D7 residual: isFailureSettled is sticky, so a failure-then-recovery sequence
        // pairs the null seed with settled=true for the moment before the billing replay arrives —
        // identical to the old sticky settledOnce, and strictly narrower (requires a prior
        // failure). The structural guarantee this change makes is about the PURE success path:
        // without a failure, no settled emission ever precedes the first reconciled data.
        val failureSettled = MutableStateFlow(true) // a previous connect attempt failed
        val repo = repo(
            lastProAt = 0L,
            billingData = BillingData(setOf(proPurchase())),
            failureSettled = failureSettled,
        )

        val infos = repo.upgradeInfo.take(2).toList()
        infos[0].isPro shouldBe false
        infos[0].isSettled shouldBe true
        infos[1].isPro shouldBe true
        infos[1].isSettled shouldBe true
    }

    @Test fun `a committed partial snapshot settles even without a known purchase`() = runTest2 {
        // data != null means a COMMITTED round-trip, not a complete one: the manager tolerates one
        // failed product type when the other returned a purchase, so an unknown-only result still
        // settles (matches the old parallel signal; grace covers a recently-confirmed owner).
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        val repo = repo(lastProAt = 0L, billingData = BillingData(setOf(unknown)))

        repo.upgradeInfo.first { it.isSettled }.isPro shouldBe false
    }

    // endregion

    @Test fun `retry delay grows and caps at five minutes`() {
        UpgradeRepoGplay.retryDelayMs(0) shouldBe 30_000L
        UpgradeRepoGplay.retryDelayMs(1) shouldBe 60_000L
        UpgradeRepoGplay.retryDelayMs(2) shouldBe 120_000L
        UpgradeRepoGplay.retryDelayMs(3) shouldBe 240_000L
        UpgradeRepoGplay.retryDelayMs(4) shouldBe 300_000L
        UpgradeRepoGplay.retryDelayMs(100) shouldBe 300_000L
        UpgradeRepoGplay.retryDelayMs(Long.MAX_VALUE) shouldBe 300_000L
    }

    // region pro state tracking

    @Test fun `fresh data with a known purchase records PURCHASED`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(setOf(proPurchase())), isFullSnapshot = false),
        )

        coVerify(exactly = 1) { curriculumVitae.updateProState(CurriculumVitae.ProState.PURCHASED) }
    }

    @Test fun `an empty full snapshot within grace records GRACE`() = runTest2 {
        repo(
            lastProAt = System.currentTimeMillis() - 1_000,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = true),
        )

        coVerify(exactly = 1) { curriculumVitae.updateProState(CurriculumVitae.ProState.GRACE) }
    }

    @Test fun `an empty full snapshot outside grace records FREE`() = runTest2 {
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = true),
        )

        coVerify(exactly = 1) { curriculumVitae.updateProState(CurriculumVitae.ProState.FREE) }
    }

    @Test fun `an empty partial snapshot records nothing`() = runTest2 {
        // A partial refresh proves ownership of what it contains, never absence: without a known
        // upgrade it can't distinguish GRACE from FREE and must not fake a downward transition.
        repo(
            lastProAt = 0L,
            freshBillingData = BillingManager.FreshData(BillingData(emptySet()), isFullSnapshot = false),
        )

        coVerify(exactly = 0) { curriculumVitae.updateProState(any()) }
    }

    // endregion

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

        repo.startLaunch()
        repo.autoRestoreBusy.first() shouldBe true

        gate.complete(Unit)
        repo.autoRestoreBusy.first() shouldBe false
    }

    // endregion

    // region ack safety net

    @Test fun `launching a billing flow arms the persistent ack safety net first`() = runTest2 {
        val order = mutableListOf<String>()
        coEvery { ackScheduler.armForBillingFlowLaunch() } coAnswers { order.add("arm") }
        coEvery { billingManager.startIapFlow(any(), any(), null) } coAnswers { order.add("launch") }

        repo(lastProAt = 0L).startLaunch()

        // Armed (and awaited) BEFORE the Play sheet can open: the process may die around the sheet,
        // and the WorkManager transaction has to land first to be worth anything.
        order shouldBe listOf("arm", "launch")
    }

    @Test fun `a failing safety net arm never blocks the purchase flow`() = runTest2 {
        coEvery { ackScheduler.armForBillingFlowLaunch() } throws RuntimeException("workmanager broken")
        coJustRun { billingManager.startIapFlow(any(), any(), null) }

        val errors = mutableListOf<Throwable>()
        repo(lastProAt = 0L).startLaunch { errors.add(it) }

        // The net is best-effort: the foreground ack path still exists, the purchase must proceed.
        errors shouldBe emptyList()
        coVerify { billingManager.startIapFlow(any(), any(), null) }
    }

    @Test fun `onAppStart arms the periodic ack sweep`() = runTest2 {
        repo(lastProAt = 0L).onAppStart()

        coVerify(exactly = 1) { ackScheduler.armPeriodicSweep() }
    }

    @Test fun `onAppStart swallows a scheduler failure`() = runTest2 {
        coEvery { ackScheduler.armPeriodicSweep() } throws RuntimeException("workmanager broken")

        // Fail-open: process start must never be taken down by a scheduling problem.
        repo(lastProAt = 0L).onAppStart()
    }


    // endregion
}
