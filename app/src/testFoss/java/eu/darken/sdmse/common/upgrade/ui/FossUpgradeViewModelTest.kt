package eu.darken.sdmse.common.upgrade.ui

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.core.FossUpgrade
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import org.robolectric.shadows.ShadowSystemClock
import testhelpers.BaseTest
import testhelpers.TestApplication
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FossUpgradeViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun upgradedInfo() = UpgradeRepoFoss.Info(
        isPro = true,
        upgradedAt = Instant.EPOCH,
        fossUpgradeType = FossUpgrade.Type.GITHUB_SPONSORS,
    )

    private fun mockRepo(
        info: MutableStateFlow<UpgradeRepoFoss.Info> = MutableStateFlow(UpgradeRepoFoss.Info()),
    ): UpgradeRepoFoss = mockk<UpgradeRepoFoss>(relaxed = true).apply {
        every { upgradeInfo } returns info
    }

    private fun buildVm(
        repo: UpgradeRepoFoss = mockRepo(),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = UpgradeViewModel(
        handle = handle,
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
        upgradeRepo = repo,
    )

    @Test
    fun `manage route shows the free status to non-upgraded users`() = runTest2(context = testDispatcher) {
        val vm = buildVm()

        val view = async { vm.state.first { it != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await() shouldBe FossUpgradeView.STATUS_FREE
    }

    @Test
    fun `manage route shows the upgraded status to supporters`() = runTest2(context = testDispatcher) {
        val vm = buildVm(repo = mockRepo(MutableStateFlow(upgradedInfo())))

        val view = async { vm.state.first { it != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await() shouldBe FossUpgradeView.STATUS_UPGRADED
    }

    @Test
    fun `default and forced routes show the pitch`() = runTest2(context = testDispatcher) {
        val defaultVm = buildVm()
        val defaultView = async { defaultVm.state.first { it != null } }
        defaultVm.bindRoute(UpgradeRoute())

        val forcedVm = buildVm()
        val forcedView = async { forcedVm.state.first { it != null } }
        forcedVm.bindRoute(UpgradeRoute(forced = true))
        advanceUntilIdle()

        defaultView.await() shouldBe FossUpgradeView.PITCH
        forcedView.await() shouldBe FossUpgradeView.PITCH
    }

    @Test
    fun `asking for upgrade options switches the free status to the pitch`() = runTest2(context = testDispatcher) {
        val vm = buildVm()
        vm.bindRoute(UpgradeRoute(manage = true))

        val freeView = async { vm.state.first { it != null } }
        advanceUntilIdle()
        freeView.await() shouldBe FossUpgradeView.STATUS_FREE

        val pitchView = async { vm.state.first { it == FossUpgradeView.PITCH } }
        vm.onShowUpgradeOptions()
        advanceUntilIdle()

        pitchView.await() shouldBe FossUpgradeView.PITCH
    }

    @Test
    fun `the upgrade-options choice survives process recreation`() = runTest2(context = testDispatcher) {
        val handle = SavedStateHandle()
        val firstVm = buildVm(handle = handle)
        firstVm.bindRoute(UpgradeRoute(manage = true))
        firstVm.onShowUpgradeOptions()
        advanceUntilIdle()

        // Same handle, fresh ViewModel — as after the process was killed on the pitch.
        val recreatedVm = buildVm(handle = handle)
        val view = async { recreatedVm.state.first { it != null } }
        recreatedVm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await() shouldBe FossUpgradeView.PITCH
    }

    @Test
    fun `completing the upgrade lands on the upgraded status even from the pitch`() = runTest2(
        context = testDispatcher,
    ) {
        val info = MutableStateFlow(UpgradeRepoFoss.Info())
        val vm = buildVm(repo = mockRepo(info))
        vm.bindRoute(UpgradeRoute(manage = true))
        vm.onShowUpgradeOptions()

        val pitchView = async { vm.state.first { it != null } }
        advanceUntilIdle()
        pitchView.await() shouldBe FossUpgradeView.PITCH

        val upgradedView = async { vm.state.first { it == FossUpgradeView.STATUS_UPGRADED } }
        info.value = upgradedInfo()
        advanceUntilIdle()

        upgradedView.await() shouldBe FossUpgradeView.STATUS_UPGRADED
    }

    @Test
    fun `default route bounces an upgraded user out of the screen`() = runTest2(context = testDispatcher) {
        val vm = buildVm(repo = mockRepo(MutableStateFlow(upgradedInfo())))

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        vm.bindRoute(UpgradeRoute())
        advanceUntilIdle()

        navEvents shouldBe listOf(NavEvent.Up)
        collector.cancel()
    }

    @Test
    fun `manage route keeps an upgraded user on the screen`() = runTest2(context = testDispatcher) {
        val vm = buildVm(repo = mockRepo(MutableStateFlow(upgradedInfo())))

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        navEvents.shouldBeEmpty()
        collector.cancel()
    }

    @Test
    fun `a too-quick sponsor return only nudges, it does not upgrade`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val vm = buildVm(repo = repo)

        val nudge = async { vm.snackbarEvents.first() }
        vm.goGithubSponsors()
        vm.checkSponsorReturn()
        advanceUntilIdle()

        nudge.await() shouldBe R.string.upgrade_screen_sponsor_return_too_quick
        coVerify(exactly = 0) { repo.persistUpgrade() }
    }

    @Test
    fun `a too-quick sponsor return stays silent for already upgraded users`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo(MutableStateFlow(upgradedInfo()))
        val vm = buildVm(repo = repo)

        val nudges = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.snackbarEvents.collect { nudges.add(it) } }

        vm.goGithubSponsors()
        vm.checkSponsorReturn()
        advanceUntilIdle()

        nudges.shouldBeEmpty()
        coVerify(exactly = 0) { repo.persistUpgrade() }
        collector.cancel()
    }

    @Test
    fun `a sponsor return after the delay persists the upgrade`() = runTest2(context = testDispatcher) {
        val repo = mockRepo()
        val vm = buildVm(repo = repo)

        val thanks = async { vm.toastEvents.first() }
        vm.goGithubSponsors()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        thanks.await() shouldBe R.string.upgrade_screen_thanks_toast
        coVerify(exactly = 1) { repo.persistUpgrade() }
    }
}
