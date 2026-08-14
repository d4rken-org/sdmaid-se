package eu.darken.sdmse.common.upgrade.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.core.FossUpgrade
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoFoss
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
import java.io.IOException
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
        every { openGithubSponsorsPage() } returns true
        // Explicit: a relaxed mock would answer the Boolean with false, i.e. "record already
        // existed", silently turning every thanks-toast assertion below into a no-op.
        coEvery { persistUpgrade() } returns true
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

        val view = async { vm.state.first { it.view != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await().view shouldBe FossUpgradeView.STATUS_FREE
    }

    @Test
    fun `manage route shows the upgraded status to supporters`() = runTest2(context = testDispatcher) {
        val vm = buildVm(repo = mockRepo(MutableStateFlow(upgradedInfo())))

        val view = async { vm.state.first { it.view != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await().view shouldBe FossUpgradeView.STATUS_UPGRADED
    }

    @Test
    fun `supporterSince reflects the repo's upgradedAt`() = runTest2(context = testDispatcher) {
        // Derived in the same emission as the view: the upgraded status must never render a frame
        // without the date it is supposed to carry.
        val vm = buildVm(repo = mockRepo(MutableStateFlow(upgradedInfo())))

        val state = async { vm.state.first { it.view != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        state.await() shouldBe UpgradeViewModel.State(
            view = FossUpgradeView.STATUS_UPGRADED,
            supporterSince = Instant.EPOCH,
        )
    }

    @Test
    fun `default and forced routes show the pitch`() = runTest2(context = testDispatcher) {
        val defaultVm = buildVm()
        val defaultView = async { defaultVm.state.first { it.view != null } }
        defaultVm.bindRoute(UpgradeRoute())

        val forcedVm = buildVm()
        val forcedView = async { forcedVm.state.first { it.view != null } }
        forcedVm.bindRoute(UpgradeRoute(forced = true))
        advanceUntilIdle()

        defaultView.await().view shouldBe FossUpgradeView.PITCH
        forcedView.await().view shouldBe FossUpgradeView.PITCH
    }

    @Test
    fun `asking for upgrade options switches the free status to the pitch`() = runTest2(context = testDispatcher) {
        val vm = buildVm()
        vm.bindRoute(UpgradeRoute(manage = true))

        val freeView = async { vm.state.first { it.view != null } }
        advanceUntilIdle()
        freeView.await().view shouldBe FossUpgradeView.STATUS_FREE

        val pitchView = async { vm.state.first { it.view == FossUpgradeView.PITCH } }
        vm.onShowUpgradeOptions()
        advanceUntilIdle()

        pitchView.await().view shouldBe FossUpgradeView.PITCH
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
        val view = async { recreatedVm.state.first { it.view != null } }
        recreatedVm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()

        view.await().view shouldBe FossUpgradeView.PITCH
    }

    @Test
    fun `completing the upgrade lands on the upgraded status even from the pitch`() = runTest2(
        context = testDispatcher,
    ) {
        val info = MutableStateFlow(UpgradeRepoFoss.Info())
        val vm = buildVm(repo = mockRepo(info))
        vm.bindRoute(UpgradeRoute(manage = true))
        vm.onShowUpgradeOptions()

        val pitchView = async { vm.state.first { it.view != null } }
        advanceUntilIdle()
        pitchView.await().view shouldBe FossUpgradeView.PITCH

        val upgradedView = async { vm.state.first { it.view == FossUpgradeView.STATUS_UPGRADED } }
        info.value = upgradedInfo()
        advanceUntilIdle()

        upgradedView.await().view shouldBe FossUpgradeView.STATUS_UPGRADED
    }

    @Test
    fun `forced route lands on the upgraded status when the sponsor flow completes`() = runTest2(
        context = testDispatcher,
    ) {
        // Forced routes (Pro-locked settings entry) deliberately don't auto-close, so the screen is
        // still up when the unlock lands — it must flip to the supporter status instead of keeping
        // the sales pitch, which reads as "sponsoring didn't work".
        val info = MutableStateFlow(UpgradeRepoFoss.Info())
        val vm = buildVm(repo = mockRepo(info))

        val navEvents = mutableListOf<NavEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { navEvents.add(it) } }

        val pitchView = async { vm.state.first { it.view != null } }
        vm.bindRoute(UpgradeRoute(forced = true))
        advanceUntilIdle()
        pitchView.await().view shouldBe FossUpgradeView.PITCH

        val upgradedView = async { vm.state.first { it.view == FossUpgradeView.STATUS_UPGRADED } }
        info.value = upgradedInfo()
        advanceUntilIdle()

        upgradedView.await().view shouldBe FossUpgradeView.STATUS_UPGRADED
        // The don't-auto-close semantics of forced routes are unchanged — status, not navigation,
        // is what acknowledges the upgrade here.
        navEvents.shouldBeEmpty()
        collector.cancel()
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

    /**
     * Process death between the sponsor launch and the return: the screen's in-memory return
     * tracker is gone, so the handle-backed pending launch has to carry the state across. Without
     * it the very first return after a recreation is dropped and the supporter never gets unlocked.
     */
    @Test
    fun `a sponsor return after process recreation still persists the upgrade`() = runTest2(
        context = testDispatcher,
    ) {
        val handle = SavedStateHandle()
        val firstVm = buildVm(handle = handle)
        firstVm.goGithubSponsors()
        advanceUntilIdle()

        // Same handle, fresh ViewModel — as after the process was killed while the browser was up.
        val repo = mockRepo()
        val recreatedVm = buildVm(repo = repo, handle = handle)
        recreatedVm.hasPendingSponsorLaunch() shouldBe true

        val thanks = async { recreatedVm.toastEvents.first() }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        recreatedVm.checkSponsorReturn()
        advanceUntilIdle()

        thanks.await() shouldBe R.string.upgrade_screen_thanks_toast
        coVerify(exactly = 1) { repo.persistUpgrade() }
        // Consumed: a later resume must not re-run the unlock.
        recreatedVm.hasPendingSponsorLaunch() shouldBe false
    }

    @Test
    fun `a long sponsor visit by an already upgraded user does not re-persist the upgrade`() = runTest2(
        context = testDispatcher,
    ) {
        // The store transaction keeps the existing record either way, so this is about the feedback:
        // no redundant write attempt, and no thanks toast for an unlock that already happened.
        val repo = mockRepo(MutableStateFlow(upgradedInfo()))
        val vm = buildVm(repo = repo)

        val nudges = mutableListOf<Int>()
        val thanks = mutableListOf<Int>()
        val snackbarCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.snackbarEvents.collect { nudges.add(it) }
        }
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.toastEvents.collect { thanks.add(it) } }

        vm.goGithubSponsors()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.persistUpgrade() }
        nudges.shouldBeEmpty()
        thanks.shouldBeEmpty()
        snackbarCollector.cancel()
        toastCollector.cancel()
    }

    @Test
    fun `a sponsor return whose record already existed stays quiet`() = runTest2(context = testDispatcher) {
        // The isPro fast path reads a shareIn replay that can be stale, so a supporter's return can
        // get past it. Only the store transaction knows the record is already there — it keeps it and
        // reports "not created", and there is no unlock to thank anyone for.
        val repo = mockRepo()
        coEvery { repo.persistUpgrade() } returns false
        val vm = buildVm(repo = repo)

        val nudges = mutableListOf<Int>()
        val thanks = mutableListOf<Int>()
        val snackbarCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.snackbarEvents.collect { nudges.add(it) }
        }
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.toastEvents.collect { thanks.add(it) } }

        vm.goGithubSponsors()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        coVerify(exactly = 1) { repo.persistUpgrade() }
        thanks.shouldBeEmpty()
        nudges.shouldBeEmpty()
        // Consumed: the visit was evaluated, there is nothing left to retry.
        vm.hasPendingSponsorLaunch() shouldBe false

        snackbarCollector.cancel()
        toastCollector.cancel()
    }

    @Test
    fun `a failed persist restores the pending sponsor launch`() = runTest2(context = testDispatcher) {
        // The marker is consumed before the write. If the write then fails, dropping it would eat a
        // valid sponsor visit for good — the next return/resume has to be able to retry the unlock.
        val repo = mockRepo()
        coEvery { repo.persistUpgrade() } throws IOException("write failed")
        val vm = buildVm(repo = repo)

        val thanks = mutableListOf<Int>()
        val errors = mutableListOf<Throwable>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.toastEvents.collect { thanks.add(it) } }
        val errorCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.errorEvents.collect { errors.add(it) } }

        vm.goGithubSponsors()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe true
        thanks.shouldBeEmpty()
        // Rethrown, not swallowed: the failure still travels the normal error path.
        errors.single().shouldBeInstanceOf<IOException>()

        toastCollector.cancel()
        errorCollector.cancel()
    }

    @Test
    fun `a thrown entitlement read restores the pending sponsor launch`() = runTest2(context = testDispatcher) {
        // The guard's entitlement read happens after the marker was consumed, so it can eat the
        // sponsor visit just as a failed write can. Installed after arming: the ViewModel's own init
        // collectors already hold the working flow, so the only failing read is the guard's.
        val repo = mockRepo()
        val vm = buildVm(repo = repo)

        val thanks = mutableListOf<Int>()
        val errors = mutableListOf<Throwable>()
        val toastCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.toastEvents.collect { thanks.add(it) } }
        val errorCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.errorEvents.collect { errors.add(it) } }

        vm.goGithubSponsors()
        advanceUntilIdle()
        every { repo.upgradeInfo } returns flow { throw IOException("read failed") }

        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe true
        thanks.shouldBeEmpty()
        coVerify(exactly = 0) { repo.persistUpgrade() }
        errors.single().shouldBeInstanceOf<IOException>()

        toastCollector.cancel()
        errorCollector.cancel()
    }

    @Test
    fun `a hung entitlement read releases the visit when the screen dies`() = runTest2(context = testDispatcher) {
        // A read that never answers holds the check open with the marker already consumed. When the
        // screen goes away the coroutine is cancelled — the catch's cancellation path has to hand
        // the sponsor visit back, otherwise it is lost with nothing to retry from.
        val repo = mockRepo()
        val vm = buildVm(repo = repo)

        vm.goGithubSponsors()
        advanceUntilIdle()
        every { repo.upgradeInfo } returns flow { awaitCancellation() }

        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()
        // Suspended inside the guard's read, marker already consumed.
        vm.hasPendingSponsorLaunch() shouldBe false

        // The ViewModel going away: vmScope shares viewModelScope's job, so this is the cleared VM.
        vm.vmScope.cancel()
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe true
        coVerify(exactly = 0) { repo.persistUpgrade() }
    }

    @Test
    fun `a newer sponsor launch survives a failed older attempt`() = runTest2(context = testDispatcher) {
        // The restore must not clobber a launch armed while the old attempt was still suspended:
        // the newer visit is the one the user is actually waiting on.
        val repo = mockRepo()
        val gate = CompletableDeferred<Unit>()
        coEvery { repo.persistUpgrade() } coAnswers {
            gate.await()
            throw IOException("write failed")
        }
        val handle = SavedStateHandle()
        val vm = buildVm(repo = repo, handle = handle)

        val errors = mutableListOf<Throwable>()
        val errorCollector = launch(start = CoroutineStart.UNDISPATCHED) { vm.errorEvents.collect { errors.add(it) } }

        vm.goGithubSponsors()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()
        // Consumed and parked in the write.
        vm.hasPendingSponsorLaunch() shouldBe false

        // A second sponsor visit while the first attempt is still hanging.
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
        val newerPressedAt = SystemClock.elapsedRealtime()
        vm.goGithubSponsors()
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe true
        // Mirrors the ViewModel's private KEY_SPONSOR_PRESSED_AT: the newer timestamp must still be
        // the one stored, the failed older attempt must not have written its own back over it.
        handle.get<Long>("sponsor_pressed_at") shouldBe newerPressedAt
        errors.single().shouldBeInstanceOf<IOException>()

        errorCollector.cancel()
    }

    @Test
    fun `a sponsor page that never opened arms nothing and a later retry still works`() = runTest2(
        context = testDispatcher,
    ) {
        // A silently failed launch must not leave the heuristic armed: an unrelated later
        // background round-trip would otherwise hand out supporter status for free.
        val repo = mockRepo()
        every { repo.openGithubSponsorsPage() } returns false
        val vm = buildVm(repo = repo)

        vm.goGithubSponsors()
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe false

        // And the failure must not brick the button either — the next working attempt arms as usual.
        every { repo.openGithubSponsorsPage() } returns true
        vm.goGithubSponsors()
        advanceUntilIdle()

        vm.hasPendingSponsorLaunch() shouldBe true
    }

    @Test
    fun `a second sponsor tap while a launch is pending opens the page only once`() = runTest2(
        context = testDispatcher,
    ) {
        val repo = mockRepo()
        val vm = buildVm(repo = repo)

        vm.goGithubSponsors()
        vm.goGithubSponsors()
        advanceUntilIdle()

        verify(exactly = 1) { repo.openGithubSponsorsPage() }
    }

    @Test
    fun `a long donate visit from the status view does not re-persist the upgrade`() = runTest2(
        context = testDispatcher,
    ) {
        // The status view's donate button is unarmed on purpose: a supporter browsing the sponsors
        // page for a while must not run the unlock heuristic again — no write attempt, no toast.
        val repo = mockRepo(MutableStateFlow(upgradedInfo()))
        val vm = buildVm(repo = repo)

        val state = async { vm.state.first { it.view != null } }
        vm.bindRoute(UpgradeRoute(manage = true))
        advanceUntilIdle()
        state.await().supporterSince shouldBe Instant.EPOCH

        vm.openSponsors()
        advanceUntilIdle()

        verify(exactly = 1) { repo.openGithubSponsorsPage() }
        vm.hasPendingSponsorLaunch() shouldBe false

        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        vm.checkSponsorReturn()
        advanceUntilIdle()

        coVerify(exactly = 0) { repo.persistUpgrade() }
        vm.state.value.supporterSince shouldBe Instant.EPOCH
    }
}
