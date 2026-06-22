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
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    ): Harness {
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(taskState)
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
            debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
                every { create(any(), any(), any(), any()) } returns emptyFlow()
            },
            deduplicator = deduplicator,
            squeezer = squeezer,
            swiper = mockk<Swiper>(relaxed = true).apply {
                every { getSessionsWithStats() } returns emptyFlow()
                every { progress } returns emptyFlow()
            },
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply { every { upgradeInfo } returns emptyFlow() },
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
            spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
                every { getAllHistory(any()) } returns emptyFlow()
            },
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
    fun `discardResults revives a previously dismissed hero state`() = runTest2 {
        val h = harness()
        h.vm.dismissHero()
        h.vm.isHeroDismissed.value shouldBe true

        h.vm.discardResults()
        advanceUntilIdle()

        // Clean slate for the next scan cycle: no leftover dismissal (and thus no restore chip).
        h.vm.isHeroDismissed.value shouldBe false
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
}
