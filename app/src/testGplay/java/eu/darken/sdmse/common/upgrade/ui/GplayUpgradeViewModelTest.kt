package eu.darken.sdmse.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.GplayServiceUnavailableException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
        val upgradeInfo = MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        val repo = mockk<UpgradeRepoGplay>(relaxed = true)
        every { repo.upgradeInfo } returns upgradeInfo
        every { repo.wasEverPro } returns MutableStateFlow(false)
        coEvery { repo.querySkus(OurSku.Iap.PRO_UPGRADE) } coAnswers {
            delay(6_000)
            emptyList()
        }
        coEvery { repo.querySkus(OurSku.Sub.PRO_UPGRADE) } coAnswers {
            delay(6_000)
            emptyList()
        }

        val vm = UpgradeViewModel(
            handle = SavedStateHandle(mapOf("forced" to false)),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            upgradeRepo = repo,
        )

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

    private fun mockRepo(): UpgradeRepoGplay = mockk<UpgradeRepoGplay>(relaxed = true).apply {
        every { upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
        every { wasEverPro } returns MutableStateFlow(false)
    }

    private fun buildVm(repo: UpgradeRepoGplay): UpgradeViewModel = UpgradeViewModel(
        handle = SavedStateHandle(mapOf("forced" to false)),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        upgradeRepo = repo,
    )

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
        val repo = mockk<UpgradeRepoGplay>(relaxed = true)
        every { repo.upgradeInfo } returns MutableStateFlow(UpgradeRepoGplay.Info(false, null, null))
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
        val repo = mockk<UpgradeRepoGplay>(relaxed = true)
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
}
