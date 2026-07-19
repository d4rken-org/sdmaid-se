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
    fun `the repo's auto-restore busy state folds into restoreInProgress`() = runTest2(
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
        idle.await().restoreInProgress shouldBe false

        // The invisible already-owned recovery must pause the entitlement actions like a manual
        // restore does -- the user can't be allowed to race it with a buy or another restore.
        autoBusy.value = true
        val busy = async {
            vm.state.first { it is GplayUpgradeUiState.Loaded && it.restoreInProgress }
        }
        advanceUntilIdle()
        (busy.await() as GplayUpgradeUiState.Loaded).restoreInProgress shouldBe true
    }

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        every { isSettled } returns MutableStateFlow(true)
        every { wasEverPro } returns MutableStateFlow(false)
        every { proUnconfirmedSince } returns MutableStateFlow(0L)
        // Relaxed mocks return a no-op Flow that never emits -- the state combine would starve.
        every { autoRestoreBusy } returns MutableStateFlow(false)
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
    )

    @Test
    fun `restore that finds a purchase emits RestoreSucceeded`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } returns proInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro"))
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
        coEvery { repo.restorePurchaseNow() } returns proInfo(mockPurchase("eu.darken.sdmse.iap.upgrade.pro"))
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
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(false, null, null)
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
    }

    @Test
    fun `restore that times out emits RestoreFailed`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.restorePurchaseNow() } coAnswers {
            delay(30_000) // longer than the 15s restore timeout
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
            UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
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
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(false, null, null)
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
        coEvery { repo.queryCurrentSubscriptions() } returns listOf(mockPurchase("upgrade.pro", autoRenewing = true))
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
        coEvery { repo.queryCurrentSubscriptions() } returns listOf(mockPurchase("upgrade.pro", autoRenewing = false))
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
    }

    @Test
    fun `iap purchase proceeds without any subscription`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } returns emptyList()
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
    }

    @Test
    fun `failing subscription verification blocks the purchase and forwards the error`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val boom = IllegalStateException("Play unavailable")
        coEvery { repo.queryCurrentSubscriptions() } throws boom
        val vm = buildVm(repo)

        val forwardedError = async { vm.errorEvents.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        forwardedError.await() shouldBe boom
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `subscription verification timeout blocks the purchase with a check-failed event`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(30_000) // longer than the 10s verification timeout
            emptyList()
        }
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.SubscriptionCheckFailed
        coVerify(exactly = 0) { repo.launchBillingFlowNow(any(), any(), any(), any()) }
    }

    @Test
    fun `iap taps are single-flight while a verification is running`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        coEvery { repo.queryCurrentSubscriptions() } coAnswers {
            delay(5_000)
            emptyList()
        }
        val vm = buildVm(repo)

        vm.onGoIap(mockk<Activity>(relaxed = true))
        vm.onGoIap(mockk<Activity>(relaxed = true))
        vm.onGoIap(mockk<Activity>(relaxed = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.queryCurrentSubscriptions() }
        coVerify(exactly = 1) { repo.launchBillingFlowNow(any(), eq(OurSku.Iap.PRO_UPGRADE), isNull(), any()) }
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
    fun `unsettled billing stays loading only while detail queries are pending`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        every { repo.upgradeInfo } returns MutableStateFlow(proInfo(mockPurchase("upgrade.pro")))
        every { repo.isSettled } returns MutableStateFlow(false)
        coEvery { repo.querySkus(any()) } coAnswers {
            delay(2_000)
            emptyList()
        }
        val vm = buildVm(repo)

        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }

        testScheduler.advanceTimeBy(1_000)
        vm.state.value shouldBe GplayUpgradeUiState.Loading

        // The settling wait is bounded by the detail queries: once they resolve, rendering
        // proceeds even if billing never settles — a starved billing layer must degrade to a
        // rendered screen, not an endless spinner.
        advanceUntilIdle()
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(gracePeriod = true, billingData = null))
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
        coEvery { repo.restorePurchaseNow() } returns UpgradeRepoGplay.Info(gracePeriod = true, billingData = null)
        val vm = buildVm(repo)

        val event = async { vm.events.first() }
        vm.restorePurchase()
        advanceUntilIdle()

        event.await() shouldBe UpgradeEvents.RestoreFailed
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
