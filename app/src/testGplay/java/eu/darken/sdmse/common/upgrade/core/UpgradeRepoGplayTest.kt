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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
    private lateinit var lastProAtMock: DataStoreValue<Long>
    private lateinit var lastProSkuMock: DataStoreValue<String>
    private lateinit var proUnconfirmedMock: DataStoreValue<Long>

    // Builds a repo whose stored last-Pro timestamp is `lastProAt`. The Unconfined scope runs the
    // init collectors (grace stamping, async already-owned) eagerly against the stubbed flows.
    private fun repo(
        lastProAt: Long,
        lastSku: String = "",
        billingData: BillingData = BillingData(emptySet()),
        freshBillingData: BillingManager.FreshData? = null,
        purchaseFailures: List<BillingResult> = emptyList(),
        proUnconfirmedSince: Long = 0L,
        connectionFailures: Flow<Long> = emptyFlow(),
    ): UpgradeRepoGplay {
        every { billingManager.billingData } returns flowOf(billingData)
        every { billingManager.freshBillingData } returns
            (freshBillingData?.let { flowOf(it) } ?: emptyFlow())
        every { billingManager.connectionFailures } returns connectionFailures
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
        proUnconfirmedMock = mockk<DataStoreValue<Long>>(relaxed = true).apply {
            every { flow } returns flowOf(proUnconfirmedSince)
        }
        every { billingCache.proUnconfirmedSince } returns proUnconfirmedMock
        coJustRun { billingCache.stampLastProState(any(), any()) }
        return UpgradeRepoGplay(scope, billingManager, billingCache, curriculumVitae)
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
            .launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { errors.add(it) }

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

    @Test fun `unknown-only purchases still fall through to the grace check`() = runTest2 {
        // A purchase list containing only products this app doesn't know maps to zero upgrades:
        // it must not take the "has purchases" branch and deny a recently-Pro user their grace.
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        coEvery { billingManager.refresh() } returns BillingData(setOf(unknown))

        repo(lastProAt = System.currentTimeMillis() - 1_000).restorePurchaseNow().isPro shouldBe true
    }

    @Test fun `unknown-only purchases without recent grace are not pro`() = runTest2 {
        val unknown = mockk<Purchase>().apply {
            every { products } returns listOf("some.unknown.product")
            every { purchaseTime } returns 1_000L
        }
        coEvery { billingManager.refresh() } returns BillingData(setOf(unknown))

        repo(lastProAt = 0L).restorePurchaseNow().isPro shouldBe false
    }

    @Test fun `a known purchase is pro even when the grace cache is unreadable`() = runTest2 {
        // Known purchases are decided before any grace-cache read: failing local storage (full
        // disk) must not turn a confirmed purchase into an error episode.
        coEvery { billingManager.refresh() } returns BillingData(setOf(proPurchase()))
        val repo = repo(lastProAt = 0L)
        every { lastProAtMock.flow } returns flow { throw IOException("disk full") }

        repo.restorePurchaseNow().isPro shouldBe true
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
        infos[1].error shouldBe null
        infos[1].isPro shouldBe false
    }

    @Test fun `a persistently failing grace cache does not kill upgradeInfo`() = runTest2 {
        val repo = repo(lastProAt = 0L)
        every { lastProAtMock.flow } returns flow { throw IOException("disk full") }

        // The retry predicate's own cache probe fails as well -- the flow must still emit instead
        // of terminating.
        repo.upgradeInfo.first().error shouldNotBe null
    }

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

        repo.launchBillingFlow(mockk<Activity>(), OurSku.Iap.PRO_UPGRADE, null) { }
        repo.autoRestoreBusy.first() shouldBe true

        gate.complete(Unit)
        repo.autoRestoreBusy.first() shouldBe false
    }

    // endregion
}
