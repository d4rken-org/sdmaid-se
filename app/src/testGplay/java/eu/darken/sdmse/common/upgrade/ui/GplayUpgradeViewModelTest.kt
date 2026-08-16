package eu.darken.sdmse.common.upgrade.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.BillingData
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import eu.darken.sdmse.common.upgrade.core.billing.OfferUnavailableBillingException
import eu.darken.sdmse.common.upgrade.core.billing.PendingPurchaseBillingException
import eu.darken.sdmse.common.upgrade.core.billing.Sku
import eu.darken.sdmse.main.ui.navigation.SupportFormRoute
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayUpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `service timeout becomes unavailable state and error event instead of crashing`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } coAnswers {
            delay(20_000) // longer than the 15s SKU query timeout
            emptyList()
        }
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } coAnswers {
            delay(20_000)
            emptyList()
        }

        val vm = buildVm(repo)

        val unavailableState = async {
            vm.state.first { it is GplayUpgradeUiState.Unavailable }
        }
        val forwardedError = async { vm.errorEvents.first() }

        advanceUntilIdle()

        unavailableState.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        forwardedError.await().shouldBeInstanceOf<GplayServiceUnavailableException>()
        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()

        coVerify(exactly = 1) { repo.querySkus(OurSku.Iap.PRO_UPGRADE) }
        coVerify(exactly = 1) { repo.querySkus(OurSku.Sub.PRO_UPGRADE) }
    }

    @Test
    fun `a slow but healthy Play store loads instead of tripping the timeout`() = runTest2(
        context = testDispatcher,
    ) {
        // The first-ever billing query after Play sign-in measured 8.5s on-device: the old 5s
        // timeout turned that healthy store into a false "Play unavailable".
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } coAnswers {
            delay(9_000)
            emptyList()
        }

        val vm = buildVm(repo)

        val loaded = async { vm.state.first { it is GplayUpgradeUiState.Loaded } }
        advanceUntilIdle()

        loaded.await().shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
    }

    @Test
    fun `retry recovers the screen after a full unavailable episode`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        var calls = 0
        coEvery { repo.querySkus(any()) } coAnswers {
            // First generation (both product types) fails; the retried generation succeeds.
            if (calls++ < 2) throw GplayServiceUnavailableException(RuntimeException("Play hiccup"))
            emptyList()
        }
        val vm = buildVm(repo)

        val unavailable = async { vm.state.first { it is GplayUpgradeUiState.Unavailable } }
        advanceUntilIdle()
        unavailable.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()

        // Without the retry, the Lazily-cached failure bricked the screen for the VM lifetime.
        vm.retrySkuQuery()
        val loaded = async { vm.state.first { it is GplayUpgradeUiState.Loaded } }
        advanceUntilIdle()

        loaded.await().shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        coVerify(exactly = 4) { repo.querySkus(any()) }
    }

    @Test
    fun `onResume retries the query after a failure`() = runTest2(
        context = testDispatcher,
    ) {
        // MainActivity's per-resume refresh only covers the entitlement -- coming back to the screen
        // after a Play outage has to re-run the screen-local SKU query too.
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } throws GplayServiceUnavailableException(RuntimeException("Play hiccup"))
        val vm = buildVm(repo)

        // WhileSubscribed: without a live subscriber the retry has no upstream to re-run.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        coVerify(exactly = 1) { repo.querySkus(OurSku.Iap.PRO_UPGRADE) }
        coVerify(exactly = 1) { repo.querySkus(OurSku.Sub.PRO_UPGRADE) }

        vm.onResume()
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.querySkus(OurSku.Iap.PRO_UPGRADE) }
        coVerify(exactly = 2) { repo.querySkus(OurSku.Sub.PRO_UPGRADE) }
    }

    @Test
    fun `onResume does not re-query when offers are already loaded`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        coVerify(exactly = 2) { repo.querySkus(any()) }

        vm.onResume()
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.querySkus(any()) }
    }

    @Test
    fun `both product types unavailable surfaces the merchandising error, not a connectivity one`() = runTest2(
        context = testDispatcher,
    ) {
        // Play answered OK and simply has no sellable offer here (region, account eligibility,
        // pulled product). Reporting that as "can't connect to Google Play" tells the user to
        // clear Play's cache and reboot, which cannot help.
        val repo = mockRepo()
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } throws
            OfferUnavailableBillingException(OurSku.Iap.PRO_UPGRADE, null)
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } throws
            OfferUnavailableBillingException(OurSku.Sub.PRO_UPGRADE, null)
        val vm = buildVm(repo)

        val unavailableState = async { vm.state.first { it is GplayUpgradeUiState.Unavailable } }
        val forwardedError = async { vm.errorEvents.first() }
        advanceUntilIdle()

        forwardedError.await().shouldBeInstanceOf<OfferUnavailableBillingException>()
        val state = unavailableState.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        state.error.shouldBeInstanceOf<OfferUnavailableBillingException>()
    }

    @Test
    fun `a mixed failure keeps the conservative connectivity error`() = runTest2(
        context = testDispatcher,
    ) {
        // One sku failed for a non-merchandising reason: a real Play problem can't be ruled out,
        // so the conservative "can't reach Play" copy stays.
        val repo = mockRepo()
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } throws
            OfferUnavailableBillingException(OurSku.Iap.PRO_UPGRADE, null)
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val unavailableState = async { vm.state.first { it is GplayUpgradeUiState.Unavailable } }
        val forwardedError = async { vm.errorEvents.first() }
        advanceUntilIdle()

        forwardedError.await().shouldBeInstanceOf<GplayServiceUnavailableException>()
        val state = unavailableState.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        state.error.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `a connectivity failure on both product types stays a connectivity error`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val unavailableState = async { vm.state.first { it is GplayUpgradeUiState.Unavailable } }
        val forwardedError = async { vm.errorEvents.first() }
        advanceUntilIdle()

        forwardedError.await().shouldBeInstanceOf<GplayServiceUnavailableException>()
        val state = unavailableState.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
        state.error.shouldBeInstanceOf<GplayServiceUnavailableException>()
    }

    @Test
    fun `a single failed product type keeps the screen loaded and surfaces the error once`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val boom = IllegalStateException("IAP details broken")
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } throws boom
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { vm.state.first { it is GplayUpgradeUiState.Loaded } }
        val forwardedError = async { vm.errorEvents.first() }
        advanceUntilIdle()

        // The working product type is still offered; only the failure is reported.
        loaded.await().shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        forwardedError.await() shouldBe boom
    }

    @Test
    fun `the repo's auto-restore busy state folds into the busy op`() = runTest2(
        context = testDispatcher,
    ) {
        val autoBusy = MutableStateFlow(false)
        val repo = mockRepo()
        every { repo.autoRestoreBusy } returns autoBusy
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val idle = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded } as GplayUpgradeUiState.Loaded
        }
        advanceUntilIdle()
        idle.await().busy shouldBe null

        // The invisible already-owned recovery must pause the entitlement actions like a manual
        // restore does -- the user can't be allowed to race it with a buy or another restore.
        autoBusy.value = true
        val busy = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded && it.busy != null }
        }
        advanceUntilIdle()
        (busy.await() as GplayUpgradeUiState.Loaded).busy shouldBe BusyOp.RESTORE
    }

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null, isSettled = true))
        every { wasEverPro } returns MutableStateFlow(false)
        every { proUnconfirmedSince } returns MutableStateFlow(0L)
        // Relaxed mocks return a no-op Flow that never emits -- the state combine would starve.
        every { autoRestoreBusy } returns MutableStateFlow(false)
        every { purchaseLaunchSku } returns MutableStateFlow<Sku?>(null)
        // Both purchase paths run the pre-purchase gate: the default is a clean account (nothing
        // owned, nothing pending), so tests only stub it when the gate IS the subject.
        coEvery { verifyPurchaseStateNow() } returns UpgradeRepoGplay.Info(false, null, null, isSettled = true)
    }

    private fun buildVm(
        repo: UpgradeRepoGplay,
        webpageTool: WebpageTool = mockk(relaxed = true),
    ): UpgradeViewModel = UpgradeViewModel(
        handle = SavedStateHandle(mapOf("forced" to false)),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        upgradeRepo = repo,
        webpageTool = webpageTool,
    )

    private fun mockPurchase(skuId: String, autoRenewing: Boolean = false): Purchase = mockk<Purchase>().apply {
        every { products } returns listOf(skuId)
        every { isAutoRenewing } returns autoRenewing
        every { purchaseTime } returns 1234L
    }

    private fun proInfo(vararg purchases: Purchase) = UpgradeRepoGplay.Info(
        false,
        BillingData(purchases = purchases.toList()),
        null,
        // Ownership data implies a committed reconciliation -> always settled.
        isSettled = true,
    )

    // Play is still processing a payment: nothing owned, nothing granted, but the purchase paths
    // must treat it as a blocking answer.
    private fun pendingInfo(skuId: String = OurSku.Iap.PRO_UPGRADE.id) = UpgradeRepoGplay.Info(
        false,
        BillingData(purchases = emptyList(), pendingPurchases = listOf(mockPurchase(skuId))),
        null,
        isSettled = true,
    )

    /** Play answered. The default for restore mocks; use Inconclusive only to model a non-answer. */
    private fun checked(info: UpgradeRepoGplay.Info) = UpgradeRepoGplay.RestoreOutcome.Checked(info)

    @Test
    fun `restore that finds a purchase emits RestoreSucceeded`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns checked(proInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreSucceeded
    }

    @Test
    fun `restore results are held back until the minimum visible duration`() = runTest2(context = testDispatcher) {
        // The repo answers instantly here — the user must still see the check "run": the result
        // event may only surface once RESTORE_MIN_VISIBLE_MS elapsed.
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns checked(proInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro")))
        val vm = buildVm(repo)

        val received = mutableListOf<UpgradeEvents>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.events.collect { received.add(it) } }

        vm.restorePurchase()
        testScheduler.advanceTimeBy(UpgradeViewModel.RESTORE_MIN_VISIBLE_MS - 100)
        testScheduler.runCurrent()
        received.shouldBeEmpty()

        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()
        received shouldBe listOf<UpgradeEvents>(UpgradeEvents.RestoreSucceeded)
        collector.cancel()
    }

    @Test
    fun `restore with no purchase emits RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns checked(UpgradeRepoGplay.Info(false, null, null))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test
    fun `restore that finds a pending payment emits PurchasePending`() = runTest2(context = testDispatcher) {
        // Play answered and DID find the purchase — it just isn't paid for yet. RestoreFailed would
        // send this user through account troubleshooting and support for something that resolves
        // itself.
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns checked(pendingInfo())
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchasePending
    }

    @Test
    fun `restore that times out emits RestoreInconclusive not RestoreFailed`() = runTest2(context = testDispatcher) {
        // A timeout proves nothing about ownership: the budget also covers connecting and the
        // refresh mutex. RestoreFailed would assert a completed check and steer the user toward
        // the multi-account explanation for what may just be a slow Play.
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(30_000) // longer than the 15s restore timeout
            checked(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreInconclusive
    }

    @Test
    fun `a Play error absorbed by grace emits RestoreInconclusive not RestoreFailed`() = runTest2(
        context = testDispatcher,
    ) {
        // Same non-answer as a timeout, and the affected user is by definition a recent owner --
        // exactly who must not be told Play was checked and had nothing.
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.RestoreOutcome.Inconclusive(
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true),
            RuntimeException("Play unavailable"),
        )
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreInconclusive
    }

    @Test
    fun `restore that errors forwards the error instead of RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.restorePurchaseNow() } throws boom
        val vm = buildVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
    }

    @Test
    fun `previously-pro on this device flows into the loaded banner flag`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.wasEverPro } returns MutableStateFlow(true)
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } returns emptyList()
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded } as GplayUpgradeUiState.Loaded
        }
        advanceUntilIdle()

        loaded.await().wasPreviouslyPro shouldBe true
    }

    @Test
    fun `banner flag stays off while grace still keeps the user pro`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        // gracePeriod = true => Info.isPro is true even without a current raw purchase.
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        every { repo.wasEverPro } returns MutableStateFlow(true)
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } returns emptyList()
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded } as GplayUpgradeUiState.Loaded
        }
        advanceUntilIdle()

        loaded.await().wasPreviouslyPro shouldBe false
    }

    @Test
    fun `restore is single-flight, taps during a running restore are ignored`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            checked(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        }
        val vm = buildVm(repo)

        vm.restorePurchase()
        vm.restorePurchase()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
    }

    @Test
    fun `a finished restore allows a new attempt`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns checked(UpgradeRepoGplay.Info(false, null, null))
        val vm = buildVm(repo)

        vm.restorePurchase()
        advanceUntilIdle()
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.restorePurchaseNow() }
    }

    @Test
    fun `default route bounces a pro user out of the screen`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro", autoRenewing = true)))
        val vm = buildVm(repo)

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        vm.bindRoute(UpgradeRoute())
        advanceUntilIdle()

        navEvents shouldBe listOf(NavEvent.Up)
        collector.cancel()
    }

    @Test
    fun `manage route keeps a pro user on the screen`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro", autoRenewing = true)))
        val vm = buildVm(repo)

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        navEvents.shouldBeEmpty()
        collector.cancel()
    }

    @Test
    fun `iap purchase is blocked while the subscription is still set to renew`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns
            proInfo(mockPurchase(OurSku.Sub.PRO_UPGRADE.id, autoRenewing = true))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionStillRenewing
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `iap purchase is blocked by a renewing subscription with an unknown product`() = runTest2(
        context = testDispatcher,
    ) {
        // The gate reads the RAW purchases, never the mapped upgrades: a subscription whose product
        // ID this build doesn't know (legacy SKU, renamed product) still renews and still bills, so
        // letting the one-time purchase through here charges the user for Pro twice.
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns
            proInfo(mockPurchase("some.unknown.subscription", autoRenewing = true))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionStillRenewing
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `iap purchase proceeds when the subscription is not set to renew`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns
            proInfo(mockPurchase(OurSku.Sub.PRO_UPGRADE.id, autoRenewing = false))
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
    }

    @Test
    fun `iap purchase proceeds without any subscription`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns UpgradeRepoGplay.Info(false, null, null, isSettled = true)
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
    }

    @Test
    fun `failing purchase verification blocks the purchase and forwards the error`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.verifyPurchaseStateNow() } throws boom
        val vm = buildVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `purchase verification timeout blocks the purchase with a check-failed event`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } coAnswers {
            delay(30_000) // longer than the 10s verification timeout
            UpgradeRepoGplay.Info(false, null, null, isSettled = true)
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchaseCheckFailed
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a subscription gate timeout blocks that purchase too`() = runTest2(context = testDispatcher) {
        // The subscription path used to launch unverified: a slow Play means we don't know whether
        // a payment is already pending, so it must fail closed like the one-time path.
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } coAnswers {
            delay(30_000)
            UpgradeRepoGplay.Info(false, null, null, isSettled = true)
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchaseCheckFailed
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a pending payment blocks the one-time purchase`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns pendingInfo()
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchasePending
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a pending payment blocks the subscription purchase`() = runTest2(context = testDispatcher) {
        // SKU-agnostic on purpose: the two products are alternatives, so a pending payment for
        // either one must block both — completing both charges the user twice.
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns pendingInfo()
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoSubscriptionTrial(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchasePending
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a subscription purchase is blocked when the fresh check finds an owned upgrade`() = runTest2(
        context = testDispatcher,
    ) {
        // The screen can be stale (the one-time purchase was made on another device) and Play sells
        // the subscription right next to an owned IAP — launching here charges the user for Pro a
        // second time.
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns proInfo(mockPurchase(OurSku.Iap.PRO_UPGRADE.id))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreSucceeded
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a subscription purchase is blocked when an unknown renewing subscription exists`() = runTest2(
        context = testDispatcher,
    ) {
        // Unknown product ID => zero mapped upgrades, so the ownership block above lets it through.
        // It still renews and still bills, and a second subscription for the same features is the
        // same double charge.
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } returns
            proInfo(mockPurchase("some.unknown.subscription", autoRenewing = true))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionStillRenewing
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a pending-payment launch failure surfaces as the pending dialog`() = runTest2(context = testDispatcher) {
        // Play can only report this at launch time (the gate saw a clean state moments earlier):
        // the already-owned error dialog with its restore tips would be the wrong advice.
        // The callback is captured and invoked from the test body rather than from inside the
        // answer: on a suspend function the argument list carries the continuation, so grabbing the
        // callback positionally there is a coin flip — and a wrong cast would surface as a hang.
        val repo = mockRepo()
        val onError = slot<(Throwable) -> Unit>()
        coEvery { repo.launchBillingFlowNow(any(), any(), any(), capture(onError)) } returns Unit
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        onError.captured(PendingPurchaseBillingException())
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.PurchasePending
    }

    @Test
    fun `iap taps are single-flight while a verification is running`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.verifyPurchaseStateNow() } coAnswers {
            delay(5_000)
            UpgradeRepoGplay.Info(false, null, null, isSettled = true)
        }
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        vm.onGoIap(mockk<Activity>(relaxed = true))
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.verifyPurchaseStateNow() }
        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
    }

    // A repo whose Play launch takes a while to resolve: the guard has to cover the whole
    // tap-to-sheet window, so every arbiter test needs a launch that is actually in flight.
    private fun UpgradeRepoGplay.withSlowLaunch(durationMs: Long = 5_000L) = apply {
        coEvery { launchBillingFlowNow(any(), any(), any(), any()) } coAnswers { delay(durationMs) }
    }

    @Test
    fun `subscription taps are single-flight`() = runTest2(context = testDispatcher) {
        val repo = mockRepo().withSlowLaunch()
        val vm = buildVm(repo)

        // The old fire-and-forget path had no guard at all: every tap opened another Play sheet.
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        vm.onGoSubscriptionTrial(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Sub.PRO_UPGRADE), any(), any()) }
    }

    @Test
    fun `a running subscription launch blocks the iap and restore actions`() = runTest2(context = testDispatcher) {
        val repo = mockRepo().withSlowLaunch()
        val vm = buildVm(repo)

        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        testScheduler.advanceTimeBy(1_000) // launch in flight
        testScheduler.runCurrent()
        vm.onGoIap(mockk<Activity>(relaxed = true))
        vm.restorePurchase()
        advanceUntilIdle()

        // One arbiter for all three: the purchase and the restore would otherwise run concurrent
        // Play operations against the same account state. Exactly one gate ran — the subscription's
        // own; the blocked IAP tap never got to verify anything.
        coVerify(exactly = 1) { repo.verifyPurchaseStateNow() }
        coVerify(exactly = 0) { repo.restorePurchaseNow() }
        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `a running restore blocks the purchase actions`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(5_000)
            checked(UpgradeRepoGplay.Info(false, null, null))
        }
        val vm = buildVm(repo)

        vm.restorePurchase()
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.restorePurchaseNow() }
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.verifyPurchaseStateNow() }
    }

    @Test
    fun `the arbiter is released once the launch resolved`() = runTest2(context = testDispatcher) {
        val repo = mockRepo().withSlowLaunch()
        val vm = buildVm(repo)

        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 2) { repo.launchBillingFlowNow(any(), eq(OurSku.Sub.PRO_UPGRADE), any(), any()) }
    }

    @Test
    fun `a running subscription launch is exposed as the busy op`() = runTest2(context = testDispatcher) {
        val repo = mockRepo().withSlowLaunch()
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        (vm.state.value as GplayUpgradeUiState.Loaded).busy shouldBe BusyOp.SUBSCRIPTION

        advanceUntilIdle()
        (vm.state.value as GplayUpgradeUiState.Loaded).busy shouldBe null
        collector.cancel()
    }

    @Test
    fun `a launch from another ViewModel instance blocks this one`() = runTest2(context = testDispatcher) {
        // The launch lives on AppScope and outlives the ViewModel that started it, so after a
        // rotation the fresh ViewModel must not start a second one.
        val repo = mockRepo().withSlowLaunch()
        val launchSku = MutableStateFlow<Sku?>(OurSku.Sub.PRO_UPGRADE)
        every { repo.purchaseLaunchSku } returns launchSku
        val vm = buildVm(repo)

        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        vm.restorePurchase()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
        coVerify(exactly = 0) { repo.restorePurchaseNow() }

        // Once the foreign launch resolved, this ViewModel works normally again.
        launchSku.value = null
        vm.onGoSubscription(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `subscription owner gets ownership state even when product details fail`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro", autoRenewing = true)))
        coEvery { repo.querySkus(any()) } throws IllegalStateException("No details available")
        val vm = buildVm(repo)

        val loaded = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded } as GplayUpgradeUiState.Loaded
        }
        advanceUntilIdle()

        val ownership = loaded.await().ownership
        ownership.hasIap shouldBe false
        ownership.subscription.shouldNotBeNull().isAutoRenewing.shouldBeTrue()
    }

    @Test
    fun `successful queries never render from an unsettled Info`() = runTest2(
        context = testDispatcher,
    ) {
        // The adversarial order behind the old flash: SKU queries finish BEFORE the reconciled
        // Info propagates. The screen must hold at Loading instead of rendering acquisition UI
        // from the pre-reconciliation seed — even though the queries are done.
        val repo = mockRepo()
        val infos = MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        every { repo.upgradeInfo } returns infos
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        testScheduler.advanceTimeBy(1_000)
        vm.state.value shouldBe GplayUpgradeUiState.Loading

        // The settled Info arrives (here: reconciled ownership) -> rendering proceeds.
        infos.value = proInfo(mockPurchase("upgrade.pro"))
        advanceUntilIdle()
        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        collector.cancel()
    }

    @Test
    fun `two failed queries resolve to unavailable without waiting for settled`() = runTest2(
        context = testDispatcher,
    ) {
        // Carve-out: a Done where BOTH fresh SKU queries failed is itself a definitive
        // can't-reach-Play outcome — the Unavailable card keeps its ~15s worst-case bound from
        // the query timeouts instead of also waiting out the connect loop's failure signal.
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        coEvery { repo.querySkus(any()) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val unavailable = async { vm.state.first { it is GplayUpgradeUiState.Unavailable } }
        advanceUntilIdle()

        unavailable.await().shouldBeInstanceOf<GplayUpgradeUiState.Unavailable>()
    }

    @Test
    fun `settled owner renders ownership while queries are still pending`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro")))
        coEvery { repo.querySkus(any()) } coAnswers {
            delay(60_000) // effectively never within this test
            emptyList()
        }
        val vm = buildVm(repo)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        testScheduler.advanceTimeBy(1_000)
        // Owners don't depend on offer prices: status renders immediately, never acquisition.
        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        collector.cancel()
    }

    @Test
    fun `manage subscription opens the play management page for our sub`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val webpageTool = mockk<WebpageTool>(relaxed = true)
        val vm = buildVm(repo, webpageTool)

        vm.onManageSubscription()

        verify { webpageTool.open(UpgradeViewModel.PLAY_SUBSCRIPTION_SITE) }
        UpgradeViewModel.PLAY_SUBSCRIPTION_SITE shouldContain "sku=${OurSku.Sub.PRO_UPGRADE.id}"
        UpgradeViewModel.PLAY_SUBSCRIPTION_SITE shouldContain "package="
    }

    @Test
    fun `contact support navigates to the guided support form`() = runTest2(context = testDispatcher) {
        val vm = buildVm(mockRepo())

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        vm.onContactSupport()
        advanceUntilIdle()

        navEvents shouldBe listOf(NavEvent.GoTo(SupportFormRoute))
        collector.cancel()
    }

    private suspend fun awaitLoaded(vm: UpgradeViewModel): GplayUpgradeUiState.Loaded =
        vm.state.first { it is GplayUpgradeUiState.Loaded } as GplayUpgradeUiState.Loaded

    @Test
    fun `grace-only pro gets a quiet hint without diagnostics`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        val grace = loaded.await().grace
        grace.shouldNotBeNull().showDiagnostics shouldBe false
    }

    @Test
    fun `young grace episode keeps diagnostics hidden`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        every { repo.proUnconfirmedSince } returns MutableStateFlow(
            System.currentTimeMillis() - Duration.ofHours(1).toMillis()
        )
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        loaded.await().grace.shouldNotBeNull().showDiagnostics shouldBe false
    }

    @Test
    fun `aged grace episode shows diagnostics`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        every { repo.proUnconfirmedSince } returns MutableStateFlow(
            System.currentTimeMillis() - UpgradeViewModel.GRACE_DIAGNOSTICS_AFTER_MS - 1_000
        )
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        loaded.await().grace.shouldNotBeNull().showDiagnostics shouldBe true
    }

    @Test
    fun `plain non-pro users get no grace hint`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        loaded.await().grace shouldBe null
    }

    @Test
    fun `owners get no grace hint`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro", autoRenewing = true)))
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        loaded.await().grace shouldBe null
    }

    @Test
    fun `grace user keeps the grace card when both detail queries fail`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        // During an outage (exactly when grace matters) the price queries fail too — the user
        // must keep the Loaded grace presentation, not get an acquisition-style Unavailable.
        coEvery { repo.querySkus(any()) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        loaded.await().grace.shouldNotBeNull()
    }

    @Test
    fun `grace diagnostics appear when the episode crosses the threshold`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        val base = System.currentTimeMillis()
        // Episode is 10 virtual seconds short of the threshold.
        every { repo.proUnconfirmedSince } returns MutableStateFlow(
            base - UpgradeViewModel.GRACE_DIAGNOSTICS_AFTER_MS + 10_000
        )
        coEvery { repo.querySkus(any()) } returns emptyList()
        val vm = buildVm(repo)
        var fakeNow = base
        vm.clock = { fakeNow }

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        (vm.state.value as GplayUpgradeUiState.Loaded).grace.shouldNotBeNull().showDiagnostics shouldBe false

        // Cross the boundary: wall clock moves past it, then the scheduled tick re-evaluates.
        fakeNow = base + 11_000
        advanceUntilIdle()
        (vm.state.value as GplayUpgradeUiState.Loaded).grace.shouldNotBeNull().showDiagnostics shouldBe true
        collector.cancel()
    }

    @Test
    fun `restore that only finds grace shows the troubleshooting dialog`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        // Grace keeps isPro=true, but no actual purchase came back — not a restore success.
        coEvery { repo.restorePurchaseNow() } returns checked(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null, isSettled = true))
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test
    fun `a pending payment renders while the price queries are still running`() = runTest2(
        context = testDispatcher,
    ) {
        // Price-independent like owners and grace users: the pending card is this user's answer and
        // both offers are locked anyway, so waiting on prices would hide it behind Loading.
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(pendingInfo())
        coEvery { repo.querySkus(any()) } coAnswers {
            delay(60_000) // effectively never within this test
            emptyList()
        }
        val vm = buildVm(repo)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        testScheduler.advanceTimeBy(1_000)

        val loaded = vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        loaded.hasPendingPurchase shouldBe true
        collector.cancel()
    }

    @Test
    fun `a pending payment keeps its card when both price queries fail`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(pendingInfo())
        coEvery { repo.querySkus(any()) } throws IllegalStateException("Play unavailable")
        val vm = buildVm(repo)

        val loaded = async { awaitLoaded(vm) }
        advanceUntilIdle()

        // Not the acquisition-style Unavailable card: the pending explanation must survive a price
        // outage, exactly like the grace card does.
        loaded.await().hasPendingPurchase shouldBe true
    }

    @Test
    fun `owner with failed detail queries gets no detail error dialog`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro", autoRenewing = true)))
        coEvery { repo.querySkus(any()) } throws IllegalStateException("No details available")
        val vm = buildVm(repo)

        val errors = mutableListOf<Throwable>()
        val errorCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.errorEvents.collect { errors.add(it) } }
        val stateCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        vm.state.value.shouldBeInstanceOf<GplayUpgradeUiState.Loaded>()
        errors.shouldBeEmpty()
        errorCollector.cancel()
        stateCollector.cancel()
    }
}
