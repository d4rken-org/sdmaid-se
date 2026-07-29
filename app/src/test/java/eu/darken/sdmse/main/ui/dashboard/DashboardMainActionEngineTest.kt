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
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
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
        /** Branch failures the scope's handler caught — in production these reach `errorEvents`. */
        val branchErrors: List<Throwable>,
    )

    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    private fun upgradeInfoMock(isPro: Boolean): UpgradeRepo.Info = mockk(relaxed = true) {
        every { this@mockk.isPro } returns isPro
        every { isSettled } returns true
        every { error } returns null
    }

    private fun harness(
        taskState: TaskSubmitter.State = TaskSubmitter.State(),
        corpseFinderOneClick: Boolean = true,
        otherToolsOneClick: Boolean = false,
        appCleanerOneClick: Boolean = otherToolsOneClick,
        corpseData: CorpseFinder.Data? = null,
        appData: AppCleaner.Data? = null,
        isPro: Boolean = false,
        failingTaskType: Class<out SDMTool.Task>? = null,
        onUpgradeRequired: () -> Unit = {},
        gate: CompletableDeferred<Unit>? = null,
    ): Harness {
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns MutableStateFlow(taskState)
        }
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns corpseData })
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns appData })
        }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { oneClickCorpseFinderEnabled } returns mockBool(corpseFinderOneClick)
            every { oneClickSystemCleanerEnabled } returns mockBool(otherToolsOneClick)
            every { oneClickAppCleanerEnabled } returns mockBool(appCleanerOneClick)
            every { oneClickDeduplicatorEnabled } returns mockBool(otherToolsOneClick)
            every { enableDashboardOneClick } returns mockBool(false)
        }
        val submittedTasks = mutableListOf<SDMTool.Task>()
        val branchErrors = mutableListOf<Throwable>()
        // Production-like scope: supervised like vmScope, eager like TestDispatcherProvider's
        // Unconfined; cancelled per test so the engine's internal collectors don't leak. The
        // handler mirrors vmScope's — without it a branch failure escapes to the JVM's default
        // handler, which kotlinx-coroutines-test then blames on whichever test runs next.
        val engineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, e -> branchErrors.add(e) },
        )
        val engine = DashboardMainActionEngine(
            scope = engineScope,
            taskManager = taskManager,
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            deduplicator = deduplicator,
            generalSettings = generalSettings,
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true) {
                // isProForUi() reads isPro and isSettled off the same Info; an unsettled mock would
                // make it wait out its timeout and then fail open to Pro.
                every { upgradeInfo } returns flowOf(upgradeInfoMock(isPro))
            },
            upgradeInfo = flowOf(upgradeInfoMock(isPro)),
            submitTask = { task ->
                submittedTasks.add(task)
                // Mirrors TaskManager.submit rethrowing a recorded task error.
                if (failingTaskType?.isInstance(task) == true) throw IllegalStateException("task failed")
                // Stands in for the tool's resource lock, holding a branch in flight.
                gate?.await()
                mockk(relaxed = true)
            },
            onUpgradeRequired = onUpgradeRequired,
        )
        return Harness(
            engine,
            engineScope,
            taskManager,
            corpseFinder,
            systemCleaner,
            appCleaner,
            deduplicator,
            submittedTasks,
            branchErrors,
        )
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
    fun `a cleanup that frees nothing says so instead of re-showing the freeable card`() {
        // Regression for the "I press the button and nothing happens" report: a zero-result cleanup
        // leaves the tools' data intact, so the identical FREEABLE hero rebuilds and buries the
        // fact that the deletion ran at all. The relaxed task result is neither AffectedSpace nor
        // AffectedCount, which is exactly the "freed 0" shape.
        val corpseData = mockk<CorpseFinder.Data>(relaxed = true) {
            every { corpses } returns setOf(mockk(relaxed = true))
        }
        val h = harness(corpseData = corpseData)

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = runBlocking {
                h.engine.bottomBarState(
                    listIsReady = MutableStateFlow(true),
                    oneClickOptionsState = MutableStateFlow(OneClickOptionsState()),
                ).first().heroSummary!!
            }

            hero.mode shouldBe HeroSummary.Mode.NOTHING_FREED
            hero.totalSize shouldBe 0L
            hero.itemCount shouldBe 0
            hero.tools.shouldBeEmpty()
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup where every tool opted out shows no hero at all`() {
        // Nothing ran, so there is no outcome to report — distinct from "ran and freed nothing".
        val h = harness(corpseFinderOneClick = false, otherToolsOneClick = false)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)

            val state = runBlocking {
                h.engine.bottomBarState(
                    listIsReady = MutableStateFlow(true),
                    oneClickOptionsState = MutableStateFlow(OneClickOptionsState()),
                ).first()
            }

            h.submittedTasks.shouldBeEmpty()
            state.heroSummary shouldBe null
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a second cleanup is ignored while one is still in flight`() {
        // A second batch would reset the tally under the branches still running on it. The gate
        // stands in for the tool resource lock that keeps the first cleanup's task pending.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.submittedTasks.size shouldBe 1

            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.submittedTasks.size shouldBe 1

            // Once it settles, the button works again.
            gate.complete(Unit)
            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.submittedTasks.size shouldBe 2
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `scans are never blocked by an in-flight cleanup`() {
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.engine.mainAction(BottomBarState.Action.SCAN)

            h.submittedTasks.size shouldBe 2
        } finally {
            gate.complete(Unit)
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a failed branch suppresses the nothing-freed card`() {
        // One branch frees nothing and another throws: the user already gets an error dialog, so
        // claiming the cleanup "finished" on top of it would contradict it. The findings survived
        // the failure, so the freeable card legitimately stays — it just must not be the
        // NOTHING_FREED one.
        val corpseData = mockk<CorpseFinder.Data>(relaxed = true) {
            every { corpses } returns setOf(mockk(relaxed = true))
        }
        val h = harness(
            corpseData = corpseData,
            otherToolsOneClick = true,
            failingTaskType = SystemCleanerOneClickTask::class.java,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)

            val state = runBlocking {
                h.engine.bottomBarState(
                    listIsReady = MutableStateFlow(true),
                    oneClickOptionsState = MutableStateFlow(OneClickOptionsState()),
                ).first()
            }

            state.heroSummary?.mode shouldBe HeroSummary.Mode.FREEABLE
            // The failure isn't swallowed — in production this is the dialog the user sees.
            h.branchErrors.map { it.message } shouldBe listOf("task failed")
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a non-Pro AppCleaner upsell fires even when an opted-out free tool has data`() {
        // The upsell guard used to test raw CorpseFinder/SystemCleaner data, so an opted-out tool
        // with findings silently blocked it — the DELETE branch then did nothing whatsoever.
        val corpseData = mockk<CorpseFinder.Data>(relaxed = true) {
            every { corpses } returns setOf(mockk(relaxed = true))
        }
        val appData = mockk<AppCleaner.Data>(relaxed = true) {
            every { junks } returns setOf(mockk(relaxed = true))
        }
        var upgradeRequired = 0
        val h = harness(
            corpseData = corpseData,
            appData = appData,
            corpseFinderOneClick = false,
            appCleanerOneClick = true,
            isPro = false,
            onUpgradeRequired = { upgradeRequired++ },
        )

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            upgradeRequired shouldBe 1
            h.submittedTasks.shouldBeEmpty()
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `freed hero includes the deduplicator removable-file count`() {
        // Regression for the post-delete path: a deduplicator-only cleanup must report the deleted
        // file count, not 0. The FREED itemCount used to exclude the deduplicator entirely.
        val proInfo = mockk<UpgradeRepo.Info>(relaxed = true) {
            every { isPro } returns true
            every { isSettled } returns true
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true) {
            every { upgradeInfo } returns MutableStateFlow(proInfo)
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
