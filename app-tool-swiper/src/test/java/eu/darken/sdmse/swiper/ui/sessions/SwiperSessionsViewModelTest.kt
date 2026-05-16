package eu.darken.sdmse.swiper.ui.sessions

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavigationController
import eu.darken.sdmse.common.picker.PickerResult
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.swiper.core.FileTypeCategory
import eu.darken.sdmse.swiper.core.FileTypeFilter
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SortOrder
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.swiper.core.tasks.SwiperScanTask
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class SwiperSessionsViewModelTest : BaseTest() {

    private fun session(
        id: String = "session-1",
        sourcePaths: List<APath> = listOf(LocalPath.build("storage", "emulated", "0", "DCIM")),
        state: SessionState = SessionState.READY,
    ): SwipeSession = SwipeSession(
        sessionId = id,
        sourcePaths = sourcePaths,
        currentIndex = 0,
        totalItems = 10,
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        lastModifiedAt = Instant.parse("2025-01-01T00:00:00Z"),
        state = state,
    )

    private fun sessionWithStats(
        session: SwipeSession = session(),
        keep: Int = 0,
        delete: Int = 0,
        undecided: Int = 10,
        deleted: Int = 0,
        deleteFailed: Int = 0,
    ): Swiper.SessionWithStats = Swiper.SessionWithStats(
        session = session,
        keepCount = keep,
        deleteCount = delete,
        undecidedCount = undecided,
        deletedCount = deleted,
        deleteFailedCount = deleteFailed,
    )

    private class Harness(
        val vm: SwiperSessionsViewModel,
        val swiper: Swiper,
        val taskSubmitter: TaskSubmitter,
        val navCtrl: NavigationController,
        val pickerResults: MutableSharedFlow<PickerResult>,
    )

    // Extension on TestScope so the harness can launch a state-keep-alive collector inside the test's
    // own scope. safeStateIn uses WhileSubscribed(5000), which means upstream collection only starts
    // when there's at least one subscriber — without this, state.value stays at the initialValue.
    private fun TestScope.harness(
        sessions: List<Swiper.SessionWithStats> = emptyList(),
        progress: Progress.Data? = null,
        isPro: Boolean = false,
        areas: Set<DataArea> = emptySet(),
    ): Harness {
        val sessionsFlow = MutableStateFlow(sessions)
        val progressFlow = MutableStateFlow(progress)
        val upgradeInfo: UpgradeRepo.Info = mockk<UpgradeRepo.Info>(relaxed = true).apply {
            every { this@apply.isPro } returns isPro
        }
        val upgradeFlow = MutableStateFlow<UpgradeRepo.Info>(upgradeInfo)
        val areaStateFlow = MutableStateFlow(DataAreaManager.State(areas = areas))
        // Use a hot SharedFlow so tests can inject picker results after construction. The VM's init
        // block collects this; emitting a PickerResult here exercises the createSession path.
        val pickerResults = MutableSharedFlow<PickerResult>(replay = 0, extraBufferCapacity = 1)

        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSessionsWithStats() } returns sessionsFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true)
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { this@apply.upgradeInfo } returns upgradeFlow
        }
        // currentAreas() is an extension that reads state.first().areas — no separate stub needed.
        val dataAreaManager = mockk<DataAreaManager>().apply {
            every { state } returns areaStateFlow
        }
        val navCtrl = mockk<NavigationController>(relaxed = true).apply {
            every { consumeResults<PickerResult>(any()) } returns pickerResults
        }

        val vm = SwiperSessionsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            swiper = swiper,
            taskSubmitter = taskSubmitter,
            upgradeRepo = upgradeRepo,
            navCtrl = navCtrl,
            dataAreaManager = dataAreaManager,
        )

        // Keep state subscribed via backgroundScope. Tests can now read state.value (or .first())
        // and see upstream-derived values. backgroundScope is auto-cancelled by runTest at exit.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.state.collect { /* keep subscription alive */ }
        }

        return Harness(
            vm = vm,
            swiper = swiper,
            taskSubmitter = taskSubmitter,
            navCtrl = navCtrl,
            pickerResults = pickerResults,
        )
    }

    @Test
    fun `state reflects sessionsWithStats from Swiper`() = runTest2 {
        val s = sessionWithStats()
        val h = harness(sessions = listOf(s))
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.sessionsWithStats shouldBe listOf(s)
        state.isScanning shouldBe false
    }

    @Test
    fun `state isScanning reflects progress flow`() = runTest2 {
        val h = harness(progress = Progress.Data())
        advanceUntilIdle()

        h.vm.state.first().isScanning shouldBe true
    }

    @Test
    fun `state isPro reflects upgradeRepo`() = runTest2 {
        val proHarness = harness(isPro = true)
        advanceUntilIdle()
        proHarness.vm.state.first().isPro shouldBe true

        val freeHarness = harness(isPro = false)
        advanceUntilIdle()
        freeHarness.vm.state.first().isPro shouldBe false
    }

    @Test
    fun `canCreateNewSession true when isPro regardless of session count`() = runTest2 {
        // Free version session limit is 2. Make 5 sessions and verify isPro overrides the cap.
        val sessions = (1..5).map { sessionWithStats(session = session(id = "s$it")) }
        val h = harness(sessions = sessions, isPro = true)
        advanceUntilIdle()
        h.vm.state.first().canCreateNewSession shouldBe true
    }

    @Test
    fun `canCreateNewSession false when free user is at session limit`() = runTest2 {
        val sessions = (1..2).map { sessionWithStats(session = session(id = "s$it")) }
        val h = harness(sessions = sessions, isPro = false)
        advanceUntilIdle()
        h.vm.state.first().canCreateNewSession shouldBe false
    }

    @Test
    fun `canCreateNewSession true when free user below limit`() = runTest2 {
        val sessions = listOf(sessionWithStats(session = session(id = "s1")))
        val h = harness(sessions = sessions, isPro = false)
        advanceUntilIdle()
        h.vm.state.first().canCreateNewSession shouldBe true
    }

    @Test
    fun `picker result with non-empty paths triggers createSession`() = runTest2 {
        val h = harness()
        val paths = setOf<APath>(LocalPath.build("storage", "emulated", "0", "Pictures"))
        coEvery { h.swiper.createSession(paths) } returns "new-id"

        h.pickerResults.tryEmit(PickerResult(selectedPaths = paths))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.createSession(paths) }
        h.vm.state.first().selectedPaths shouldBe paths
    }

    @Test
    fun `picker result with empty paths does not call createSession but still updates selectedPaths`() = runTest2 {
        val h = harness()

        h.pickerResults.tryEmit(PickerResult(selectedPaths = emptySet()))
        advanceUntilIdle()

        // Defends the `if (paths.isNotEmpty())` guard in init — a regression that dropped it would
        // call createSession with an empty set, which crashes (require(paths.isNotEmpty())).
        coVerify(exactly = 0) { h.swiper.createSession(any()) }
        h.vm.state.first().selectedPaths shouldBe emptySet<APath>()
    }

    @Test
    fun `scanSession submits SwiperScanTask`() = runTest2 {
        val h = harness()
        coEvery { h.taskSubmitter.submit(any()) } returns mockk(relaxed = true)

        h.vm.scanSession("session-x")
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submit(SwiperScanTask(sessionId = "session-x")) }
        // scanningSessionId resets to null in finally — the spinner is gone by the time scan returns.
        h.vm.state.first().scanningSessionId shouldBe null
    }

    @Test
    fun `cancelScan calls taskSubmitter cancel for SWIPER tool type`() = runTest2 {
        val h = harness()

        h.vm.cancelScan()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.cancel(SDMTool.Type.SWIPER) }
    }

    @Test
    fun `continueSession with cache hit skips refresh and navigates`() = runTest2 {
        val h = harness()
        coEvery { h.swiper.hasSessionLookups("s1") } returns true

        h.vm.continueSession("s1")
        advanceUntilIdle()

        coVerify(exactly = 0) { h.swiper.refreshSessionLookups(any()) }
    }

    @Test
    fun `continueSession with cache miss triggers refresh before navigating`() = runTest2 {
        val h = harness()
        coEvery { h.swiper.hasSessionLookups("s1") } returns false

        h.vm.continueSession("s1")
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.refreshSessionLookups("s1") }
        h.vm.state.first().refreshingSessionId shouldBe null
    }

    @Test
    fun `discardSession when not actively scanning just calls swiper`() = runTest2 {
        val h = harness()

        h.vm.discardSession("s1")
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.cancel(any()) }
        coVerify(exactly = 1) { h.swiper.discardSession("s1") }
    }

    @Test
    fun `renameSession delegates to swiper updateSessionLabel`() = runTest2 {
        val h = harness()

        h.vm.renameSession("s1", "My Session")
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.updateSessionLabel("s1", "My Session") }
    }

    @Test
    fun `renameSession with null label clears the label`() = runTest2 {
        val h = harness()

        h.vm.renameSession("s1", null)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.updateSessionLabel("s1", null) }
    }

    @Test
    fun `updateSessionFilter delegates to swiper`() = runTest2 {
        val h = harness()
        val filter = FileTypeFilter(categories = setOf(FileTypeCategory.IMAGES))

        h.vm.updateSessionFilter("s1", filter)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.updateSessionFilter("s1", filter) }
    }

    @Test
    fun `updateSessionSortOrder delegates to swiper`() = runTest2 {
        val h = harness()

        h.vm.updateSessionSortOrder("s1", SortOrder.SIZE_DESC)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.swiper.updateSessionSortOrder("s1", SortOrder.SIZE_DESC) }
    }

    @Test
    fun `riskySessionIds includes sessions with sensitive-root source paths`() = runTest2 {
        val sensitiveRoot = LocalPath.build("storage", "emulated", "0")
        val sensitiveArea = DataArea(
            path = sensitiveRoot,
            type = DataArea.Type.SDCARD,
            label = "Internal".toCaString(),
            userHandle = UserHandle2(handleId = 0),
        )
        val riskySession = sessionWithStats(
            session = session(id = "risky", sourcePaths = listOf(sensitiveRoot)),
        )
        val safeSession = sessionWithStats(
            session = session(
                id = "safe",
                sourcePaths = listOf(LocalPath.build("storage", "emulated", "0", "DCIM")),
            ),
        )

        val h = harness(
            sessions = listOf(riskySession, safeSession),
            areas = setOf<DataArea>(sensitiveArea),
        )
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.isSessionRisky("risky") shouldBe true
        state.isSessionRisky("safe") shouldBe false
    }

    @Test
    fun `riskySessionIds excludes sessions when no sensitive areas present`() = runTest2 {
        val s = sessionWithStats()
        val h = harness(sessions = listOf(s), areas = emptySet())
        advanceUntilIdle()

        h.vm.state.first().riskySessionIds shouldBe emptySet()
    }

    @Test
    fun `findSensitiveRoots filters paths matching sensitive areas`() = runTest2 {
        val sensitivePath = LocalPath.build("storage", "emulated", "0")
        val safePath = LocalPath.build("storage", "emulated", "0", "DCIM")
        val sensitiveArea = DataArea(
            path = sensitivePath,
            type = DataArea.Type.SDCARD,
            label = "Internal".toCaString(),
            userHandle = UserHandle2(handleId = 0),
        )
        val h = harness(areas = setOf(sensitiveArea))

        val result = h.vm.findSensitiveRoots(listOf<APath>(sensitivePath, safePath))
        result shouldBe listOf<APath>(sensitivePath)
    }

    @Test
    fun `freeVersionLimit and freeSessionLimit expose SwiperSettings constants`() = runTest2 {
        val h = harness()
        advanceUntilIdle()
        val state = h.vm.state.first()
        // Pin the user-visible cap so a refactor that renames the constant immediately fails the test
        // rather than silently changing the UI text.
        state.freeVersionLimit shouldBe 50
        state.freeSessionLimit shouldBe 2
    }
}
