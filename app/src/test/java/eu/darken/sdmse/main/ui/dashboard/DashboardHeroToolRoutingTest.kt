package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.areas.DataAreaManager
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.debug.DebugCardProvider
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSessionManager
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.review.ReviewTool
import eu.darken.sdmse.common.updater.UpdateService
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.ui.CorpseFinderListRoute
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.ui.AffectedFilesRoute
import eu.darken.sdmse.stats.ui.ReportsRoute
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant
import java.util.UUID

/**
 * Routing of the dashboard freed-hero per-tool chips: a "will be freed" chip opens the live list, a
 * "freed" chip opens that tool's report (or the reports list when no report is resolvable).
 */
internal class DashboardHeroToolRoutingTest : BaseTest() {

    private fun TestScope.harness(statsRepo: StatsRepo): DashboardViewModel {
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(TaskSubmitter.State())
        }
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply { every { state } returns emptyFlow() }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply { every { state } returns emptyFlow() }
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply { every { state } returns emptyFlow() }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply { every { state } returns emptyFlow() }
        val squeezer = mockk<Squeezer>(relaxed = true).apply { every { state } returns emptyFlow() }
        val appControl = mockk<AppControl>(relaxed = true).apply { every { state } returns emptyFlow() }
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns emptyFlow()
            every { progress } returns emptyFlow()
        }
        val schedulerManager = mockk<SchedulerManager>(relaxed = true).apply { every { state } returns emptyFlow() }
        val setupManager = mockk<SetupManager>(relaxed = true).apply { every { state } returns emptyFlow() }
        val areaManager = mockk<DataAreaManager>(relaxed = true).apply { every { latestState } returns emptyFlow() }
        val sessionManager = mockk<DebugLogSessionManager>(relaxed = true).apply { every { sessions } returns emptyFlow() }
        val motdRepo = mockk<MotdRepo>(relaxed = true).apply { every { motd } returns emptyFlow() }
        val reviewTool = mockk<ReviewTool>(relaxed = true).apply { every { state } returns emptyFlow() }
        val anniversaryProvider = mockk<AnniversaryProvider>(relaxed = true).apply { every { item } returns emptyFlow() }
        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSessionsWithStats() } returns emptyFlow()
            every { progress } returns emptyFlow()
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns emptyFlow()
            every { isSettled } returns flowOf(true)
        }
        val updateService = mockk<UpdateService>(relaxed = true).apply { every { availableUpdate } returns emptyFlow() }
        val debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
            every { create(any(), any(), any(), any()) } returns emptyFlow()
        }
        val statsSettings = mockk<StatsSettings>(relaxed = true).apply {
            every { retentionReports } returns mockDuration()
            every { retentionPaths } returns mockDuration()
            every { retentionSnapshots } returns mockDuration()
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { dashboardCardConfig } returns mockk(relaxed = true) {
                every { flow } returns emptyFlow()
            }
            // Only CorpseFinder participates in mainAction so cleanup stamping can run without
            // the Pro-gated branches needing live upgrade state.
            every { oneClickCorpseFinderEnabled } returns mockBool(true)
            every { oneClickSystemCleanerEnabled } returns mockBool(false)
            every { oneClickAppCleanerEnabled } returns mockBool(false)
            every { oneClickDeduplicatorEnabled } returns mockBool(false)
        }
        val spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
            every { getAllHistory(any()) } returns emptyFlow()
        }

        return DashboardViewModel(
            context = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            areaManager = areaManager,
            taskManager = taskManager,
            setupManager = setupManager,
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            appControl = appControl,
            analyzer = analyzer,
            debugCardProvider = debugCardProvider,
            deduplicator = deduplicator,
            squeezer = squeezer,
            swiper = swiper,
            upgradeRepo = upgradeRepo,
            generalSettings = generalSettings,
            webpageTool = mockk<WebpageTool>(relaxed = true),
            schedulerManager = schedulerManager,
            updateService = updateService,
            sessionManager = sessionManager,
            motdRepo = motdRepo,
            releaseManager = mockk<ReleaseManager>(relaxed = true),
            reviewTool = reviewTool,
            anniversaryProvider = anniversaryProvider,
            statsRepo = statsRepo,
            statsSettings = statsSettings,
            spaceHistoryRepo = spaceHistoryRepo,
            deviceDetective = mockk(relaxed = true),
        )
    }

    private fun mockDuration(): DataStoreValue<java.time.Duration> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(java.time.Duration.ZERO)
    }

    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    private class CollectedNav(val list: MutableList<NavEvent>, private val job: Job) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectNav(vm: DashboardViewModel): CollectedNav {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { vm.navEvents.collect { list.add(it) } }
        return CollectedNav(list, job)
    }

    @Test
    fun `freeable chip opens the live tool list`() = runTest2 {
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply { every { state } returns emptyFlow() }
        val vm = harness(statsRepo)
        val nav = collectNav(vm)

        vm.onHeroToolClick(HeroSummary.Mode.FREEABLE, SDMTool.Type.CORPSEFINDER)
        advanceUntilIdle()

        nav.list shouldBe listOf(NavEvent.GoTo(CorpseFinderListRoute))
        nav.cancel()
    }

    @Test
    fun `freed chip opens that tool's report when one exists`() = runTest2 {
        val id = UUID.randomUUID()
        val report = mockk<Report>(relaxed = true)
        every { report.status } returns Report.Status.SUCCESS
        every { report.reportId } returns id
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply {
            every { state } returns emptyFlow()
            coEvery { getReportForToolSince(SDMTool.Type.CORPSEFINDER, any()) } returns report
        }
        val vm = harness(statsRepo)
        val nav = collectNav(vm)

        vm.onHeroToolClick(HeroSummary.Mode.FREED, SDMTool.Type.CORPSEFINDER)
        advanceUntilIdle()

        nav.list shouldBe listOf<NavEvent>(NavEvent.GoTo(AffectedFilesRoute(id)))
        nav.cancel()
    }

    @Test
    fun `freed chip falls back to the reports list when no report is resolved`() = runTest2 {
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply {
            every { state } returns emptyFlow()
            coEvery { getReportForToolSince(any(), any()) } returns null
        }
        val vm = harness(statsRepo)
        val nav = collectNav(vm)

        vm.onHeroToolClick(HeroSummary.Mode.FREED, SDMTool.Type.SYSTEMCLEANER)
        advanceUntilIdle()

        nav.list shouldBe listOf<NavEvent>(NavEvent.GoTo(ReportsRoute))
        nav.cancel()
    }

    @Test
    fun `freed chip resolves the report against this cleanup's start time`() = runTest2 {
        val sinceSlot = slot<Instant>()
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply {
            every { state } returns emptyFlow()
            coEvery { getReportForToolSince(any(), capture(sinceSlot)) } returns null
        }
        val vm = harness(statsRepo)
        val nav = collectNav(vm)

        val beforeCleanup = Instant.now()
        vm.mainAction(BottomBarState.Action.ONECLICK)
        advanceUntilIdle()

        vm.onHeroToolClick(HeroSummary.Mode.FREED, SDMTool.Type.CORPSEFINDER)
        advanceUntilIdle()

        // The lookup must use this cleanup's batch start, not EPOCH or a stale earlier stamp.
        (sinceSlot.captured >= beforeCleanup) shouldBe true
        nav.list shouldBe listOf<NavEvent>(NavEvent.GoTo(ReportsRoute))
        nav.cancel()
    }

    @Test
    fun `freed chip falls back to the reports list when the latest report failed`() = runTest2 {
        val report = mockk<Report>(relaxed = true)
        every { report.status } returns Report.Status.FAILURE
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply {
            every { state } returns emptyFlow()
            coEvery { getReportForToolSince(any(), any()) } returns report
        }
        val vm = harness(statsRepo)
        val nav = collectNav(vm)

        vm.onHeroToolClick(HeroSummary.Mode.FREED, SDMTool.Type.CORPSEFINDER)
        advanceUntilIdle()

        nav.list shouldBe listOf<NavEvent>(NavEvent.GoTo(ReportsRoute))
        nav.cancel()
    }
}
