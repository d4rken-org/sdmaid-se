package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

/**
 * Direct contract tests for [DashboardMainActionEngine]: discarding clears both the tools' data and
 * the task manager's completed results, and cleanups stamp [DashboardMainActionEngine.freedResultSince]
 * so freed-hero chips resolve to *this* cleanup's reports.
 */
internal class DashboardMainActionEngineTest : BaseTest() {

    private class Harness(
        val engine: DashboardMainActionEngine,
        val engineScope: CoroutineScope,
        val taskManager: TaskManager,
        val corpseFinder: CorpseFinder,
        val systemCleaner: SystemCleaner,
        val appCleaner: AppCleaner,
        val deduplicator: Deduplicator,
        val submittedTasks: MutableList<SDMTool.Task>,
    )

    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    private fun harness(
        taskState: TaskSubmitter.State = TaskSubmitter.State(),
        corpseFinderOneClick: Boolean = true,
        otherToolsOneClick: Boolean = false,
    ): Harness {
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(taskState)
        }
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { oneClickCorpseFinderEnabled } returns mockBool(corpseFinderOneClick)
            every { oneClickSystemCleanerEnabled } returns mockBool(otherToolsOneClick)
            every { oneClickAppCleanerEnabled } returns mockBool(otherToolsOneClick)
            every { oneClickDeduplicatorEnabled } returns mockBool(otherToolsOneClick)
            every { enableDashboardOneClick } returns mockBool(false)
        }
        val submittedTasks = mutableListOf<SDMTool.Task>()
        // Production-like scope: supervised like vmScope, eager like TestDispatcherProvider's
        // Unconfined; cancelled per test so the engine's internal collectors don't leak.
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = DashboardMainActionEngine(
            scope = engineScope,
            taskManager = taskManager,
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            deduplicator = deduplicator,
            generalSettings = generalSettings,
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply { every { isSettled } returns flowOf(true) },
            upgradeInfo = flowOf(null),
            submitTask = { task ->
                submittedTasks.add(task)
                mockk(relaxed = true)
            },
            onUpgradeRequired = {},
        )
        return Harness(engine, engineScope, taskManager, corpseFinder, systemCleaner, appCleaner, deduplicator, submittedTasks)
    }

    private inline fun withHarness(taskState: TaskSubmitter.State = TaskSubmitter.State(), block: (Harness) -> Unit) {
        val h = harness(taskState = taskState)
        try {
            block(h)
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `discardResults clears tool data and forgets completed tasks`() = withHarness { h ->
        h.engine.discardResults()

        coVerify(exactly = 1) { h.corpseFinder.discardScanData() }
        coVerify(exactly = 1) { h.systemCleaner.discardScanData() }
        coVerify(exactly = 1) { h.appCleaner.discardScanData() }
        coVerify(exactly = 1) { h.deduplicator.discardScanData() }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.CORPSEFINDER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.SYSTEMCLEANER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.APPCLEANER) }
        coVerify(exactly = 1) { h.taskManager.forgetCompleted(SDMTool.Type.DEDUPLICATOR) }
    }

    @Test
    fun `discardResults aborts while tasks are running`() =
        withHarness(taskState = mockk(relaxed = true) { every { isIdle } returns false }) { h ->
            h.engine.discardResults()

            coVerify(exactly = 0) { h.corpseFinder.discardScanData() }
            coVerify(exactly = 0) { h.taskManager.forgetCompleted(any()) }
        }

    @Test
    fun `cleanup actions stamp freedResultSince for this batch`() = withHarness { h ->
        h.engine.freedResultSince shouldBe Instant.EPOCH

        val beforeCleanup = Instant.now()
        h.engine.mainAction(BottomBarState.Action.ONECLICK)

        (h.engine.freedResultSince >= beforeCleanup).shouldBeTrue()
        h.submittedTasks.size shouldBe 1
    }

    @Test
    fun `scans do not restamp freedResultSince`() = withHarness { h ->
        h.engine.mainAction(BottomBarState.Action.SCAN)

        h.engine.freedResultSince shouldBe Instant.EPOCH
        h.submittedTasks.size shouldBe 1
    }

    @Test
    fun `freed hero includes the deduplicator removable-file count`() {
        // Regression for the post-delete path: a deduplicator-only cleanup must report the deleted
        // file count, not 0. The FREED itemCount used to exclude the deduplicator entirely.
        val proInfo = mockk<UpgradeRepo.Info>(relaxed = true) { every { isPro } returns true }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true) {
            every { upgradeInfo } returns MutableStateFlow(proInfo)
            every { isSettled } returns flowOf(true)
        }
        val taskManager = mockk<TaskManager>(relaxed = true) {
            every { state } returns MutableStateFlow(TaskSubmitter.State())
        }
        val corpseFinder = mockk<CorpseFinder>(relaxed = true) {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true) {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val appCleaner = mockk<AppCleaner>(relaxed = true) {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val dedupData = mockk<Deduplicator.Data>(relaxed = true)
        val dedupState = MutableStateFlow(Deduplicator.State(data = dedupData, progress = null))
        val deduplicator = mockk<Deduplicator>(relaxed = true) {
            every { state } returns dedupState
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true) {
            every { oneClickCorpseFinderEnabled } returns mockBool(false)
            every { oneClickSystemCleanerEnabled } returns mockBool(false)
            every { oneClickAppCleanerEnabled } returns mockBool(false)
            every { oneClickDeduplicatorEnabled } returns mockBool(true)
            every { enableDashboardOneClick } returns mockBool(false)
        }
        val deleteResult = DeduplicatorDeleteTask.Success(
            affectedSpace = 51L * 1024 * 1024,
            affectedPaths = setOf(mockk<APath>(relaxed = true), mockk<APath>(relaxed = true)),
        )

        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = DashboardMainActionEngine(
            scope = engineScope,
            taskManager = taskManager,
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            deduplicator = deduplicator,
            generalSettings = generalSettings,
            upgradeRepo = upgradeRepo,
            upgradeInfo = MutableStateFlow(proInfo),
            submitTask = { task -> if (task is DeduplicatorDeleteTask) deleteResult else mockk(relaxed = true) },
            onUpgradeRequired = {},
        )

        try {
            engine.mainAction(BottomBarState.Action.DELETE)
            // The real delete prunes the now-empty data; mirror that so the FREED hero surfaces
            // instead of a fresh FREEABLE one.
            dedupState.value = Deduplicator.State(data = null, progress = null)

            val hero = runBlocking {
                engine.bottomBarState(
                    listIsReady = MutableStateFlow(true),
                    oneClickOptionsState = MutableStateFlow(OneClickOptionsState(deduplicatorEnabled = true)),
                ).first { it.heroSummary != null }.heroSummary!!
            }

            hero.mode shouldBe HeroSummary.Mode.FREED
            hero.totalSize shouldBe 51L * 1024 * 1024
            hero.itemCount shouldBe 2
            hero.tools.single { it.type == SDMTool.Type.DEDUPLICATOR }.count shouldBe 2
        } finally {
            engineScope.cancel()
        }
    }
}
