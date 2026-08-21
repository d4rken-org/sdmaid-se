package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.review.ReviewTool
import eu.darken.sdmse.common.updater.UpdateService
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

/** Contract of [DashboardViewModel.discardResults]: clears all four tools when idle, aborts when busy. */
internal class DashboardDiscardTest : BaseTest() {

    private class Harness(
        val vm: DashboardViewModel,
        val taskManager: TaskManager,
        val corpseFinder: CorpseFinder,
        val systemCleaner: SystemCleaner,
        val appCleaner: AppCleaner,
        val deduplicator: Deduplicator,
    )

    private fun TestScope.harness(
        taskState: TaskSubmitter.State = TaskSubmitter.State(),
        appCleanerState: AppCleaner.State? = null,
        corpseFinderState: CorpseFinder.State? = null,
    ): Harness {
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(taskState)
        }
        val corpseFinderFlow: Flow<CorpseFinder.State> =
            corpseFinderState?.let { flowOf(it) } ?: emptyFlow()
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply { every { state } returns corpseFinderFlow }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply { every { state } returns emptyFlow() }
        val appCleanerFlow: Flow<AppCleaner.State> =
            appCleanerState?.let { flowOf(it) } ?: emptyFlow()
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { state } returns appCleanerFlow
        }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply { every { state } returns emptyFlow() }
        val squeezer = mockk<Squeezer>(relaxed = true).apply { every { state } returns emptyFlow() }
        val appControl = mockk<AppControl>(relaxed = true).apply { every { state } returns emptyFlow() }
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns emptyFlow()
            every { progress } returns emptyFlow()
        }
        // A relaxed mock's flow never emits, which would stall the Analyzer card's combine.
        val analyzerSettings = mockk<AnalyzerSettings>(relaxed = true).apply {
            every { lowStorageThresholdBytes } returns mockk(relaxed = true) {
                every { flow } returns flowOf<Long?>(null)
            }
        }
        val statsSettings = mockk<StatsSettings>(relaxed = true).apply {
            every { retentionReports } returns mockDuration()
            every { retentionPaths } returns mockDuration()
            every { retentionSnapshots } returns mockDuration()
        }

        val vm = DashboardViewModel(
            context = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            areaManager = mockk<DataAreaManager>(relaxed = true).apply { every { latestState } returns emptyFlow() },
            taskManager = taskManager,
            setupManager = mockk<SetupManager>(relaxed = true).apply { every { state } returns emptyFlow() },
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            appControl = appControl,
            analyzer = analyzer,
            analyzerSettings = analyzerSettings,
            debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
                every { create(any(), any(), any(), any()) } returns emptyFlow()
            },
            deduplicator = deduplicator,
            squeezer = squeezer,
            swiper = mockk<Swiper>(relaxed = true).apply {
                every { getSessionsWithStats() } returns emptyFlow()
                every { progress } returns emptyFlow()
            },
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
                every { upgradeInfo } returns emptyFlow()
            },
            generalSettings = mockk<GeneralSettings>(relaxed = true),
            webpageTool = mockk<WebpageTool>(relaxed = true),
            schedulerManager = mockk<SchedulerManager>(relaxed = true).apply { every { state } returns emptyFlow() },
            updateService = mockk<UpdateService>(relaxed = true).apply { every { availableUpdate } returns emptyFlow() },
            sessionManager = mockk<DebugLogSessionManager>(relaxed = true).apply { every { sessions } returns emptyFlow() },
            motdRepo = mockk<MotdRepo>(relaxed = true).apply { every { motd } returns emptyFlow() },
            releaseManager = mockk<ReleaseManager>(relaxed = true),
            reviewTool = mockk<ReviewTool>(relaxed = true).apply { every { state } returns emptyFlow() },
            anniversaryProvider = mockk<AnniversaryProvider>(relaxed = true).apply { every { item } returns emptyFlow() },
            statsRepo = mockk<StatsRepo>(relaxed = true).apply { every { state } returns emptyFlow() },
            statsSettings = statsSettings,
            curriculumVitae = mockk<CurriculumVitae>(relaxed = true).apply { every { installedAt } returns emptyFlow() },
            spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
                every { getAllHistory(any()) } returns emptyFlow()
            },
            spaceTracker = mockk<SpaceTracker>(relaxed = true),
            deviceDetective = mockk(relaxed = true),
        )
        return Harness(vm, taskManager, corpseFinder, systemCleaner, appCleaner, deduplicator)
    }

    private fun mockDuration(): DataStoreValue<java.time.Duration> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(java.time.Duration.ZERO)
    }

    @Test
    fun `discardResults clears all four tools when idle`() = runTest2 {
        val h = harness()

        h.vm.discardResults()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.corpseFinder.discardScanData() }
        coVerify(exactly = 1) { h.systemCleaner.discardScanData() }
        coVerify(exactly = 1) { h.appCleaner.discardScanData() }
        coVerify(exactly = 1) { h.deduplicator.discardScanData() }
    }

    @Test
    fun `discardResults forgets stale task results so tool cards reset too`() = runTest2 {
        val h = harness()

        h.vm.discardResults()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.CORPSEFINDER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.SYSTEMCLEANER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.APPCLEANER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.DEDUPLICATOR) }
    }

    @Test
    fun `discardResults collapses the hero`() = runTest2 {
        val h = harness()
        h.vm.expandHero()
        h.vm.isHeroExpanded.value shouldBe true

        h.vm.discardResults()
        advanceUntilIdle()

        // Clean slate for the next scan cycle: discarding is a full reset, so the card goes away
        // and a later card-triggered scan doesn't inherit an expanded hero.
        h.vm.isHeroExpanded.value shouldBe false
    }

    @Test
    fun `discardResults aborts when tasks are running`() = runTest2 {
        val busy = TaskSubmitter.State(
            tasks = listOf(
                TaskSubmitter.ManagedTask(
                    id = "running",
                    toolType = SDMTool.Type.CORPSEFINDER,
                    task = mockk(),
                    startedAt = Instant.now(),
                ),
            ),
        )
        val h = harness(taskState = busy)

        h.vm.discardResults()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.corpseFinder.discardScanData() }
        coVerify(exactly = 0) { h.systemCleaner.discardScanData() }
        coVerify(exactly = 0) { h.appCleaner.discardScanData() }
        coVerify(exactly = 0) { h.deduplicator.discardScanData() }
        coVerify(exactly = 0) { h.taskManager.forgetCompleted(any()) }
    }

    // --- Per-card result dismiss --------------------------------------------------------------

    private fun completedTask(result: SDMTool.Task.Result) = TaskSubmitter.State(
        tasks = listOf(
            TaskSubmitter.ManagedTask(
                id = "done",
                toolType = SDMTool.Type.APPCLEANER,
                task = mockk(),
                completedAt = Instant.now(),
                result = result,
            ),
        ),
    )

    private fun appCleanerState(junks: List<AppJunk>) = AppCleaner.State(
        data = AppCleaner.Data(junks = junks),
        progress = null,
        isOtherUsersAvailable = false,
        isRunningAppsDetectionAvailable = false,
        isInaccessibleCacheAvailable = false,
        isAcsRequired = false,
    )

    // Every card builder opens with onStart { emit(null) } as its loading state. Take the first
    // resolved emission instead of the first emission, or the assertions read the loading card.
    private suspend fun Harness.appCleanerCard(): ToolDashboardCardItem =
        vm.buildAppCleanerItem().first { !it.isInitializing }

    private suspend fun Harness.corpseFinderCard(): ToolDashboardCardItem =
        vm.buildCorpseFinderItem().first { !it.isInitializing }

    @Test
    fun `a frozen delete result offers a dismiss even when residue survives it`() = runTest2 {
        // The common case: a run frees most of it but a locked cache (com.google.android.gms) stays
        // behind. Gating the dismiss on empty live data would hide it exactly here.
        val residue = mockk<AppJunk> { every { size } returns 5_000L; every { itemCount } returns 1 }
        val h = harness(
            taskState = completedTask(
                AppCleanerProcessingTask.Success(affectedSpace = 2_100_000_000L, affectedPaths = emptySet()),
            ),
            appCleanerState = appCleanerState(junks = listOf(residue)),
        )

        h.appCleanerCard().onDismissResult shouldNotBe null
    }

    @Test
    fun `dismissing a card result drops both the frozen result and the leftover scan data`() = runTest2 {
        val h = harness(
            taskState = completedTask(
                AppCleanerProcessingTask.Success(affectedSpace = 2_100_000_000L, affectedPaths = emptySet()),
            ),
            appCleanerState = appCleanerState(junks = emptyList()),
        )

        h.appCleanerCard().onDismissResult!!.invoke()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.APPCLEANER) }
        coVerify(exactly = 1) { h.appCleaner.discardScanData() }
    }

    @Test
    fun `a scan result is discardable too, not just a cleanup receipt`() = runTest2 {
        // Discarding is offered whenever the card is showing something, matching the hero's Discard
        // button, whose own rationale is that discarding makes sense while there is pending data.
        val h = harness(
            taskState = completedTask(AppCleanerScanTask.Success(itemCount = 3, recoverableSpace = 500L)),
            appCleanerState = appCleanerState(
                junks = listOf(mockk { every { size } returns 500L; every { itemCount } returns 3 }),
            ),
        )

        h.appCleanerCard().onDismissResult shouldNotBe null
    }

    @Test
    fun `live findings with no recorded task are discardable`() = runTest2 {
        // Data can outlive its task result (exclusions applied from a details screen submit no task),
        // so the offer has to follow what the card renders, not whether TaskManager remembers a run.
        val h = harness(
            appCleanerState = appCleanerState(
                junks = listOf(mockk { every { size } returns 500L; every { itemCount } returns 3 }),
            ),
        )

        h.appCleanerCard().onDismissResult shouldNotBe null
    }

    @Test
    fun `an untouched card offers nothing to discard`() = runTest2 {
        // No result and no findings: the card is showing its plain description, so there is nothing
        // for the control to clear and it must not appear.
        val h = harness(appCleanerState = appCleanerState(junks = emptyList()))

        h.appCleanerCard().onDismissResult shouldBe null
    }

    @Test
    fun `dismissing clears live data before the recorded result so no residue figure can surface`() = runTest2 {
        // Reversing these two lets resolveScanCardResult rebuild from the surviving residue for a
        // frame, flashing "5 MB can be freed" over a run that actually freed 2.1 GB.
        val residue = mockk<AppJunk> { every { size } returns 5_000L; every { itemCount } returns 1 }
        val h = harness(
            taskState = completedTask(
                AppCleanerProcessingTask.Success(affectedSpace = 2_100_000_000L, affectedPaths = emptySet()),
            ),
            appCleanerState = appCleanerState(junks = listOf(residue)),
        )

        h.appCleanerCard().onDismissResult!!.invoke()
        advanceUntilIdle()

        coVerifyOrder {
            h.appCleaner.discardScanData()
            h.taskManager.forgetCompleted(SDMTool.Type.APPCLEANER)
        }
    }

    @Test
    fun `dismissing does nothing once a task for that tool is running`() = runTest2 {
        // The control is hidden while a task runs, but a scheduler or widget can start one between
        // the composition that rendered it and the click landing.
        val busy = TaskSubmitter.State(
            tasks = listOf(
                TaskSubmitter.ManagedTask(
                    id = "done",
                    toolType = SDMTool.Type.APPCLEANER,
                    task = mockk(),
                    completedAt = Instant.now(),
                    result = AppCleanerProcessingTask.Success(
                        affectedSpace = 2_100_000_000L,
                        affectedPaths = emptySet(),
                    ),
                ),
                TaskSubmitter.ManagedTask(
                    id = "running",
                    toolType = SDMTool.Type.APPCLEANER,
                    task = mockk(),
                    startedAt = Instant.now(),
                ),
            ),
        )
        val h = harness(taskState = busy, appCleanerState = appCleanerState(junks = emptyList()))

        h.appCleanerCard().onDismissResult!!.invoke()
        advanceUntilIdle()

        coVerify(exactly = 0) { h.appCleaner.discardScanData() }
        coVerify(exactly = 0) { h.taskManager.forgetCompleted(SDMTool.Type.APPCLEANER) }
    }

    @Test
    fun `a running task for another tool does not block this card's dismiss`() = runTest2 {
        val otherToolBusy = TaskSubmitter.State(
            tasks = completedTask(
                AppCleanerProcessingTask.Success(affectedSpace = 2_100_000_000L, affectedPaths = emptySet()),
            ).tasks + TaskSubmitter.ManagedTask(
                id = "analyzer",
                toolType = SDMTool.Type.ANALYZER,
                task = mockk(),
                startedAt = Instant.now(),
            ),
        )
        val h = harness(taskState = otherToolBusy, appCleanerState = appCleanerState(junks = emptyList()))

        h.appCleanerCard().onDismissResult!!.invoke()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.appCleaner.discardScanData() }
    }

    @Test
    fun `corpsefinder dismisses via its own Data lastResult rather than TaskManager`() = runTest2 {
        // This card ignores TaskManager entirely, so an empty task state must still offer a dismiss.
        val h = harness(
            corpseFinderState = CorpseFinder.State(
                data = CorpseFinder.Data(
                    corpses = emptyList(),
                    lastResult = CorpseFinderDeleteTask.Success(affectedSpace = 500L, affectedPaths = emptySet()),
                ),
                progress = null,
                isFilterPrivateDataAvailable = false,
                isFilterDalvikCacheAvailable = false,
                isFilterArtProfilesAvailable = false,
                isFilterAppLibrariesAvailable = false,
                isFilterAppSourcesAvailable = false,
                isFilterPrivateAppSourcesAvailable = false,
                isFilterEncryptedAppResourcesAvailable = false,
            ),
        )

        val card = h.corpseFinderCard()
        card.onDismissResult shouldNotBe null

        card.onDismissResult!!.invoke()
        advanceUntilIdle()

        coVerify(exactly = 1) { h.corpseFinder.discardScanData() }
    }
}
