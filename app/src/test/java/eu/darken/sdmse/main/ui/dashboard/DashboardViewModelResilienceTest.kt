package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.appcleaner.core.AppCleaner
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
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.core.SqueezerSettings
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

/**
 * Regression guard for the dashboard "stuck on the loading spinner" bug: the [DashboardViewModel.listState]
 * combine must produce a non-null state even when an individual source flow never emits (e.g. a slow
 * Room/DataStore/filesystem source stalls on a warm ViewModel recreation). Each combine input is given an
 * immediate fallback so no single stalled source can wedge the whole dashboard.
 */
internal class DashboardViewModelResilienceTest : BaseTest() {

    /** A flow that never emits and never completes — simulates a source stalled on resubscribe. */
    private fun <T> stalled(): Flow<T> = flow { awaitCancellation() }

    /** Build a DashboardViewModel with every source stubbed to a safe collectable flow.
     *  [stall] optionally replaces one wrapped source with a never-emitting flow. */
    private fun TestScope.harness(stall: String? = null): DashboardViewModel {
        fun <T> srcOr(name: String, default: Flow<T>): Flow<T> = if (stall == name) stalled() else default

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
            every { data } returns srcOr("analyzer", emptyFlow())
            every { progress } returns emptyFlow()
        }
        val schedulerManager = mockk<SchedulerManager>(relaxed = true).apply { every { state } returns emptyFlow() }
        val setupManager = mockk<SetupManager>(relaxed = true).apply { every { state } returns srcOr("setup", emptyFlow()) }
        val areaManager = mockk<DataAreaManager>(relaxed = true).apply { every { latestState } returns srcOr("dataArea", emptyFlow()) }
        val sessionManager = mockk<DebugLogSessionManager>(relaxed = true).apply { every { sessions } returns srcOr("sessions", emptyFlow()) }
        val motdRepo = mockk<MotdRepo>(relaxed = true).apply { every { motd } returns srcOr("motd", emptyFlow()) }
        val reviewTool = mockk<ReviewTool>(relaxed = true).apply { every { state } returns srcOr("review", emptyFlow()) }
        val anniversaryProvider = mockk<AnniversaryProvider>(relaxed = true).apply { every { item } returns srcOr("anniversary", emptyFlow()) }
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply { every { state } returns srcOr("stats", emptyFlow()) }
        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSessionsWithStats() } returns srcOr("swiper", emptyFlow())
            every { progress } returns emptyFlow()
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply { every { upgradeInfo } returns emptyFlow() }
        val updateService = mockk<UpdateService>(relaxed = true).apply { every { availableUpdate } returns emptyFlow() }
        val debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
            every { create(any(), any(), any(), any()) } returns srcOr("debug", emptyFlow())
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
        }
        val spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
            every { getAllHistory(any()) } returns emptyFlow()
        }

        val vm = DashboardViewModel(
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
            squeezerSettings = mockk<SqueezerSettings>(relaxed = true),
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
        )

        // Keep listState subscribed for the test scope's lifetime (safeStateIn uses WhileSubscribed).
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.listState.collect { } }
        return vm
    }

    private fun mockDuration(): DataStoreValue<java.time.Duration> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(java.time.Duration.ZERO)
    }

    @Test
    fun `listState emits when all sources behave`() = runTest2 {
        val vm = harness()
        advanceUntilIdle()
        vm.listState.value.shouldNotBeNull()
    }

    @Test
    fun `listState emits even when a single source never emits`() = runTest2 {
        // One stalled source must not wedge the whole dashboard.
        for (stall in listOf("sessions", "motd", "stats", "swiper", "analyzer", "anniversary", "setup", "dataArea", "review", "debug")) {
            val vm = harness(stall = stall)
            advanceUntilIdle()
            withClue(stall) { vm.listState.value.shouldNotBeNull() }
        }
    }
}
