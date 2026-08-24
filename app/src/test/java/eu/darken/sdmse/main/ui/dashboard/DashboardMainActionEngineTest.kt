package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerOneClickTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerOneClickTask
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Direct contract tests for [DashboardMainActionEngine]: discarding clears both the tools' data and
 * the task manager's completed results, cleanups stamp [DashboardMainActionEngine.freedResultSince]
 * so freed-hero chips resolve to *this* cleanup's reports, and the hero only auto-expands as the
 * outcome of a one-tap main action that actually produced something.
 */
internal class DashboardMainActionEngineTest : BaseTest() {

    private class Harness(
        val engine: DashboardMainActionEngine,
        val engineScope: CoroutineScope,
        val taskManager: TaskManager,
        val taskState: MutableStateFlow<TaskSubmitter.State>,
        val corpseFinder: CorpseFinder,
        val corpseState: MutableStateFlow<CorpseFinder.State>,
        val systemCleaner: SystemCleaner,
        val appCleaner: AppCleaner,
        val deduplicator: Deduplicator,
        /** The auto-show setting, live: flip it to stage a user toggling it while the engine runs. */
        val heroAutoShow: MutableStateFlow<Boolean>,
        val submittedTasks: MutableList<SDMTool.Task>,
        /** Branch failures the scope's handler caught — in production these reach `errorEvents`. */
        val branchErrors: List<Throwable>,
        /** State-flow failures the engine reported — in production these reach `errorEvents`. */
        val stateErrors: List<Throwable>,
    ) {
        fun barState(): BottomBarState = runBlocking {
            engine.bottomBarState(listIsReady = MutableStateFlow(true)).first()!!
        }
    }

    private fun mockBool(value: Boolean): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(value)
    }

    /** Backed by the caller's flow so a test can change the setting after the engine was built. */
    private fun mockBool(source: Flow<Boolean>): DataStoreValue<Boolean> = mockk(relaxed = true) {
        every { flow } returns source
    }

    private fun upgradeInfoMock(isPro: Boolean): UpgradeRepo.Info = mockk(relaxed = true) {
        every { this@mockk.isPro } returns isPro
        every { isSettled } returns true
        every { error } returns null
    }

    private fun corpseToolState(data: CorpseFinder.Data?): CorpseFinder.State = mockk(relaxed = true) {
        every { this@mockk.data } returns data
    }

    /** Real [CorpseFinder.Data] so `hasData` and the hero's tool slice behave like production. */
    private fun corpseData(count: Int = 1): CorpseFinder.Data = CorpseFinder.Data(
        corpses = (0 until count).map { mockk<Corpse>(relaxed = true) },
    )

    private fun completedScanState(at: Instant): TaskSubmitter.State = TaskSubmitter.State(
        tasks = listOf(
            TaskSubmitter.ManagedTask(
                id = "scan-$at",
                toolType = SDMTool.Type.CORPSEFINDER,
                task = mockk(relaxed = true),
                completedAt = at,
                result = CorpseFinderScanTask.Success(itemCount = 1, recoverableSpace = 1L),
            ),
        ),
    )

    /**
     * A completed *delete* on record for [toolType]. [task] is the instance the task manager hands back;
     * pass one the engine submitted to represent its own work, or leave it a fresh mock to represent a
     * cleanup run somewhere else.
     */
    private fun completedDeleteState(
        toolType: SDMTool.Type = SDMTool.Type.CORPSEFINDER,
        // A real instance, not a mock: CorpseFinderDeleteTask is a data class, so this is `==` to the
        // one a cleanup submits and only differs by identity. That is what pins the `===` check.
        task: SDMTool.Task = CorpseFinderDeleteTask(),
    ): TaskSubmitter.State = TaskSubmitter.State(
        tasks = listOf(
            TaskSubmitter.ManagedTask(
                id = "delete-${System.identityHashCode(task)}",
                toolType = toolType,
                task = task,
                completedAt = Instant.now(),
                result = CorpseFinderDeleteTask.Success(affectedSpace = 1L, affectedPaths = emptySet()),
            ),
        ),
    )

    private fun harness(
        taskState: TaskSubmitter.State = TaskSubmitter.State(),
        corpseFinderOneClick: Boolean = true,
        otherToolsOneClick: Boolean = false,
        appCleanerOneClick: Boolean = otherToolsOneClick,
        deduplicatorOneClick: Boolean = otherToolsOneClick,
        corpseData: CorpseFinder.Data? = null,
        appData: AppCleaner.Data? = null,
        dedupeData: Deduplicator.Data? = null,
        isPro: Boolean = false,
        /** Initial value of the "show summary automatically" setting; see [Harness.heroAutoShow]. */
        heroAutoShow: Boolean = true,
        /**
         * What `isProForUi()` sees, which the branches consult instead of the combine's upgrade
         * flow. Defaults to [isPro]; set it apart to stage the fail-open mismatch between the two.
         */
        repoIsPro: Boolean = isPro,
        failingTaskType: Class<out SDMTool.Task>? = null,
        /** Runs on the task a [failingTaskType] submit is about to reject; stages what the task manager recorded. */
        onFailedSubmit: (SDMTool.Task) -> Unit = {},
        onUpgradeRequired: () -> Unit = {},
        gate: CompletableDeferred<Unit>? = null,
        /** Replaces CorpseFinder's state flow; for tests that need a non-StateFlow source. */
        corpseStateSource: Flow<CorpseFinder.State>? = null,
        /** Replaces the auto-show setting's flow; for tests that need a failing source. */
        heroAutoShowSource: Flow<Boolean>? = null,
        /** Replaces the engine scope; for tests that need virtual time instead of eager execution. */
        scope: CoroutineScope? = null,
    ): Harness {
        val taskStateFlow = MutableStateFlow(taskState)
        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns taskStateFlow
        }
        val corpseStateFlow = MutableStateFlow(corpseToolState(corpseData))
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { state } returns (corpseStateSource ?: corpseStateFlow)
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns null })
        }
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns appData })
        }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { state } returns MutableStateFlow(mockk(relaxed = true) { every { data } returns dedupeData })
        }
        val heroAutoShowFlow = MutableStateFlow(heroAutoShow)
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { oneClickCorpseFinderEnabled } returns mockBool(corpseFinderOneClick)
            every { oneClickSystemCleanerEnabled } returns mockBool(otherToolsOneClick)
            every { oneClickAppCleanerEnabled } returns mockBool(appCleanerOneClick)
            every { oneClickDeduplicatorEnabled } returns mockBool(deduplicatorOneClick)
            every { enableDashboardOneClick } returns mockBool(false)
            every { dashboardHeroAutoShow } returns mockBool(heroAutoShowSource ?: heroAutoShowFlow)
        }
        val submittedTasks = mutableListOf<SDMTool.Task>()
        val branchErrors = mutableListOf<Throwable>()
        val stateErrors = mutableListOf<Throwable>()
        // Production-like scope: supervised like vmScope, eager like TestDispatcherProvider's
        // Unconfined; cancelled per test so the engine's internal collectors don't leak. The
        // handler mirrors vmScope's — without it a branch failure escapes to the JVM's default
        // handler, which kotlinx-coroutines-test then blames on whichever test runs next.
        val engineScope = scope ?: CoroutineScope(
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
                every { upgradeInfo } returns flowOf(upgradeInfoMock(repoIsPro))
            },
            upgradeInfo = flowOf(upgradeInfoMock(isPro)),
            submitTask = { task ->
                submittedTasks.add(task)
                // Mirrors TaskManager.submit rethrowing a recorded task error.
                if (failingTaskType?.isInstance(task) == true) {
                    onFailedSubmit(task)
                    throw IllegalStateException("task failed")
                }
                // Stands in for the tool's resource lock, holding a branch in flight.
                gate?.await()
                mockk(relaxed = true)
            },
            onUpgradeRequired = onUpgradeRequired,
            onStateError = { stateErrors.add(it) },
        )
        return Harness(
            engine,
            engineScope,
            taskManager,
            taskStateFlow,
            corpseFinder,
            corpseStateFlow,
            systemCleaner,
            appCleaner,
            deduplicator,
            heroAutoShowFlow,
            submittedTasks,
            branchErrors,
            stateErrors,
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
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!

            hero.mode shouldBe HeroSummary.Mode.NOTHING_FREED
            hero.totalSize shouldBe 0L
            hero.itemCount shouldBe 0
            hero.tools.shouldBeEmpty()
            // A NOTHING_FREED card IS a hero, so this one-tap cleanup auto-expands it.
            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    /**
     * Stages the task manager entry a tool leaves behind when it fails after freeing something:
     * both [result] and error on the very task instance the engine submitted.
     */
    private fun partiallyFailedState(task: SDMTool.Task, result: SDMTool.Task.Result?): TaskSubmitter.State =
        TaskSubmitter.State(
            tasks = listOf(
                TaskSubmitter.ManagedTask(
                    id = "delete-partial",
                    toolType = SDMTool.Type.CORPSEFINDER,
                    task = task,
                    completedAt = Instant.now(),
                    result = result,
                    error = IOException("screen went off"),
                ),
            ),
        )

    @Test
    fun `a cleanup that failed after freeing something still credits what it freed`() {
        val harnessRef = AtomicReference<Harness?>()
        val h = harness(
            corpseData = corpseData(),
            failingTaskType = CorpseFinderDeleteTask::class.java,
            onFailedSubmit = { task ->
                harnessRef.get()!!.taskState.value = partiallyFailedState(
                    task,
                    CorpseFinderDeleteTask.Success(affectedSpace = 512L, affectedPaths = emptySet()),
                )
            },
        )
        harnessRef.set(h)

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!
            hero.mode shouldBe HeroSummary.Mode.FREED
            hero.totalSize shouldBe 512L
            // The failure still travels on to the error handling; only the bytes are salvaged.
            h.branchErrors.size shouldBe 1
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup that failed with nothing on record credits nothing`() {
        val harnessRef = AtomicReference<Harness?>()
        val h = harness(
            corpseData = corpseData(),
            failingTaskType = CorpseFinderDeleteTask::class.java,
            onFailedSubmit = { task -> harnessRef.get()!!.taskState.value = partiallyFailedState(task, result = null) },
        )
        harnessRef.set(h)

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            h.barState().heroSummary?.mode shouldNotBe HeroSummary.Mode.FREED
            h.branchErrors.size shouldBe 1
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

            h.submittedTasks.shouldBeEmpty()
            h.barState().heroSummary shouldBe null
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
    fun `a second tracked run is ignored while the first is still in flight`() {
        // Scans open the batch counters too, so a second run of any kind would reset them under the
        // branches still on the first: both counters would hit 0 after any four of the eight
        // branches returned, settling the hero while tasks are still running.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.engine.mainAction(BottomBarState.Action.SCAN)

            h.submittedTasks.size shouldBe 1
        } finally {
            gate.complete(Unit)
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a rapid double tap on scan starts a single run`() {
        // Until the task manager reports non-idle the FAB still resolves to SCAN, so both taps of a
        // double tap reach mainAction.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.submittedTasks.size shouldBe 1

            // Once the batch settles, the button works again.
            gate.complete(Unit)
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.submittedTasks.size shouldBe 2
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a failed branch suppresses the nothing-freed card`() {
        // One branch frees nothing and another throws: the user already gets an error dialog, so
        // claiming the cleanup "finished" on top of it would contradict it. The findings survived
        // the failure, so the freeable card legitimately stays — it just must not be the
        // NOTHING_FREED one.
        val h = harness(
            corpseData = corpseData(),
            otherToolsOneClick = true,
            failingTaskType = SystemCleanerOneClickTask::class.java,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.FREEABLE
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
        val appData = mockk<AppCleaner.Data>(relaxed = true) {
            every { junks } returns setOf(mockk(relaxed = true))
        }
        var upgradeRequired = 0
        val h = harness(
            corpseData = corpseData(),
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
    fun `a DELETE armed by another tool skips AppCleaner when its data is unclearable-only`() {
        // Residue whose clearing permanently failed must not be retried by DELETE: it would add
        // a guaranteed "0 deleted" AppCleaner run to every cleanup another tool armed.
        val appData = mockk<AppCleaner.Data>(relaxed = true) {
            every { junks } returns setOf(mockk(relaxed = true) { every { isUnclearable } returns true })
        }
        val h = harness(
            corpseData = corpseData(),
            appData = appData,
            appCleanerOneClick = true,
            isPro = true,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            h.submittedTasks.filterIsInstance<AppCleanerProcessingTask>().shouldBeEmpty()
            h.submittedTasks.filterIsInstance<CorpseFinderDeleteTask>().size shouldBe 1
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `one-tap still rescans AppCleaner despite unclearable-only data`() {
        // Deliberate: ONECLICK means "scan and clean now". Stale unclearable residue must not
        // suppress discovery of junk that accumulated since; the fresh scan resets it either way.
        val appData = mockk<AppCleaner.Data>(relaxed = true) {
            every { junks } returns setOf(mockk(relaxed = true) { every { isUnclearable } returns true })
        }
        val h = harness(appData = appData, appCleanerOneClick = true, isPro = true)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)

            h.submittedTasks.filterIsInstance<AppCleanerOneClickTask>().size shouldBe 1
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a one-tap scan that finds data expands the hero`() {
        // The gate holds the branches in flight so the findings land while the batch is still open,
        // mirroring a real scan where the tool's data fills before its task returns.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.engine.isHeroExpanded.value shouldBe false

            h.corpseState.value = corpseToolState(corpseData())
            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `the settled snapshot re-reads the tool states`() {
        // Regression for the settle-edge race: the batch phase is applied by re-deriving the hero,
        // so the settled snapshot reads every source *after* the branches decremented, instead of
        // pairing the settled counter with whatever a long-lived per-source collector happened to
        // have picked up. This source only reveals its value at (re)subscription — standing in for
        // one whose update the previous subscription hadn't seen yet — so a settle edge that does
        // not re-read leaves the hero collapsed.
        val gate = CompletableDeferred<Unit>()
        val corpseHolder = AtomicReference(corpseToolState(null))
        val h = harness(
            gate = gate,
            corpseStateSource = flow {
                emit(corpseHolder.get())
                awaitCancellation()
            },
        )

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)

            // The scan's findings, published where only a re-subscription can pick them up.
            corpseHolder.set(corpseToolState(corpseData()))
            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a transient derivation failure falls back and then recovers`() = runTest2 {
        // The share is eager and permanently subscribed, so nothing would ever resubscribe it: a
        // terminating operator here would cost the dashboard its bottom bar — the app's primary
        // action — for the ViewModel's lifetime, over one failed settings read.
        val attempts = AtomicInteger(0)
        val h = harness(
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            corpseStateSource = flow {
                if (attempts.getAndIncrement() == 0) throw IllegalStateException("upstream blip")
                emit(corpseToolState(corpseData()))
                awaitCancellation()
            },
        )

        try {
            runCurrent()

            // The outage is visible as the bar's not-ready state, and reported once.
            h.engine.bottomBarState(listIsReady = MutableStateFlow(true)).first() shouldBe null
            h.stateErrors.map { it.message } shouldBe listOf("upstream blip")

            // Past the first backoff the derivation resubscribes and the bar comes back.
            advanceTimeBy(2 * 1_000L)
            runCurrent()

            h.engine.bottomBarState(listIsReady = MutableStateFlow(true)).first().shouldNotBeNull()
            h.stateErrors.size shouldBe 1
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a transient auto-show settings failure still collapses the hero after recovery`() = runTest2 {
        // Without a retry the collapse-on-disable collector dies on the first failed settings read and
        // disabling auto-show would never close an open card again for the ViewModel's lifetime.
        // The outage covers every subscription while [outage] is set, so this does not depend on which
        // consumer of the setting subscribes first (the hero derivation reads the same flow).
        val outage = AtomicBoolean(true)
        val autoShow = MutableStateFlow(true)
        val h = harness(
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            heroAutoShowSource = flow {
                if (outage.get()) throw IOException("auto-show blip")
                emitAll(autoShow)
            },
        )

        try {
            runCurrent()

            // Both consumers of the setting reported their own outage; nothing else yet.
            h.stateErrors.map { it.message } shouldBe listOf("auto-show blip", "auto-show blip")

            // A second failed attempt one backoff later must not report again — once per outage,
            // not once per attempt.
            advanceTimeBy(1_500L)
            runCurrent()
            h.stateErrors.size shouldBe 2

            // Past the second backoff the collector resubscribes; DataStoreValue.flow re-emits the
            // current value on resubscription, so the collector is live again.
            outage.set(false)
            advanceTimeBy(3_000L)
            runCurrent()

            h.engine.expandHero()
            h.engine.isHeroExpanded.value shouldBe true

            autoShow.value = false
            runCurrent()

            h.engine.isHeroExpanded.value shouldBe false
            h.stateErrors.size shouldBe 2
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a one-tap scan that finds nothing does not expand the hero`() {
        val h = harness()

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)

            h.barState().heroSummary shouldBe null
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a fruitless one-tap scan does not arm a later foreign scan`() {
        // The leak this design closes: a one-tap scan that produced no hero must still consume its
        // arm, or the next card-triggered scan inherits it and pops the hero open by itself.
        val h = harness()

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.engine.isHeroExpanded.value shouldBe false

            // A tool card's own scan finding something — no mainAction() involved.
            h.corpseState.value = corpseToolState(corpseData())

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a one-tap scan does not expand the hero when auto-show is off`() {
        // The setting only takes the auto-opening away: the run still produces a summary and the bar
        // still carries it, so the compact size chip stays the indicator and one tap opens the card.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate, heroAutoShow = false)

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)

            h.corpseState.value = corpseToolState(corpseData())
            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false

            // The arm was consumed (DISARM, not IGNORE): switching the setting back on must not let
            // a later card-triggered scan inherit the arm and pop the hero open by itself.
            h.heroAutoShow.value = true
            h.corpseState.value = corpseToolState(corpseData(count = 2))

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `the chip still expands the hero when auto-show is off`() {
        // On-demand expansion is untouched by the setting; only the automatic path is gated.
        val h = harness(corpseData = corpseData(), heroAutoShow = false)

        try {
            h.barState().heroSummary.shouldNotBeNull()

            h.engine.expandHero()

            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a manually opened hero survives a one-tap cleanup with auto-show off`() {
        // Sticky expand: a card the user opened themselves may re-render with the outcome of an
        // action they started. The setting suppresses opening, it does not force closing.
        val h = harness(corpseData = corpseData(), heroAutoShow = false)

        try {
            h.engine.expandHero()

            h.engine.mainAction(BottomBarState.Action.DELETE)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `disabling auto-show collapses an open hero and clears the arm`() {
        // The user should not come back from settings to the exact card they just disabled.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)
            h.engine.expandHero()
            h.engine.isHeroExpanded.value shouldBe true

            h.heroAutoShow.value = false

            h.engine.isHeroExpanded.value shouldBe false

            // The arm went with it: re-enabling and letting the in-flight run settle must not
            // re-open the card the disable just closed.
            h.heroAutoShow.value = true
            h.corpseState.value = corpseToolState(corpseData())
            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `the settling snapshot decides, not the setting value at arm time`() {
        // The setting is a combine input, so the resolution judges the value the snapshot it is
        // looking at carries — here the user switched auto-show back on while the scan was running.
        val gate = CompletableDeferred<Unit>()
        val h = harness(gate = gate, heroAutoShow = false)

        try {
            h.engine.mainAction(BottomBarState.Action.SCAN)

            h.heroAutoShow.value = true
            h.corpseState.value = corpseToolState(corpseData())
            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe true
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a card-triggered scan leaves the hero collapsed`() {
        // The reported defect: results the user didn't ask for with the one-tap button must not
        // throw the hero card over the dashboard.
        val h = harness()

        try {
            h.corpseState.value = corpseToolState(corpseData())

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a card-triggered scan does not re-expand a dismissed hero`() {
        val h = harness(corpseData = corpseData())

        try {
            h.engine.expandHero()
            h.engine.dismissHero()

            h.corpseState.value = corpseToolState(corpseData(count = 2))

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `cancelling via the main action does not arm auto-expand`() {
        // WORKING/WORKING_CANCELABLE taps stop the current run; they start nothing and must not
        // leave an arm behind for whatever produces the next hero.
        val h = harness()

        try {
            h.engine.mainAction(BottomBarState.Action.WORKING_CANCELABLE)
            h.engine.mainAction(BottomBarState.Action.WORKING)

            h.corpseState.value = corpseToolState(corpseData())

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `dismissing mid-flight is respected when the result arrives`() {
        val gate = CompletableDeferred<Unit>()
        val h = harness(corpseData = corpseData(), gate = gate)

        try {
            h.engine.mainAction(BottomBarState.Action.ONECLICK)
            h.engine.dismissHero()

            gate.complete(Unit)

            h.barState().heroSummary.shouldNotBeNull()
            h.engine.isHeroExpanded.value shouldBe false
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup run outside the main action clears the stale freed summary`() {
        // The hero's freed outcome used to survive any number of deletes made from a tool's own
        // screen, the scheduler, or the one-tap shortcut, because none of those submit a scan and
        // only a scan invalidated it. It would then report an older run's total over data that had
        // changed underneath it.
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)
            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED

            // A delete this engine never submitted is now the CorpseFinder result on record.
            h.taskState.value = completedDeleteState()

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.FREEABLE
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanups own task on record keeps its freed summary`() {
        // The counterpart to the test above, and the reason the check is by identity rather than by
        // time or by task type: the task classes are data classes, so an external CorpseFinderDeleteTask
        // is `==` to the one this cleanup submitted. Only the instance tells them apart.
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)
            val ourTask = h.submittedTasks.single { it is CorpseFinderDeleteTask }

            h.taskState.value = completedDeleteState(task = ourTask)

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup that leaves data behind reports it alongside what it freed`() {
        // The freed total alone reads as "job done" when it isn't. corpseData() survives the relaxed
        // delete here, which is exactly the shape of junk that resisted removal.
        val h = harness(corpseData = corpseData(count = 3))

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!
            hero.residueCount shouldBe 3
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `residue ignores tools the cleanup never submitted to`() {
        // AppCleaner is switched off for this run, so its leftovers were neither freed nor failed to
        // free. Counting them would make an otherwise complete run look like it fell short.
        val h = harness(
            corpseData = corpseData(count = 2),
            appData = AppCleaner.Data(
                junks = listOf(mockk(relaxed = true) { every { size } returns 999L; every { itemCount } returns 7 }),
            ),
            appCleanerOneClick = false,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!
            hero.residueCount shouldBe 2
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup on a tool this run never touched still clears the freed summary`() {
        // The batch only submits to CorpseFinder here. An in-tool AppCleaner deletion afterwards
        // changes storage just as much, so scoping the check to the tools the batch happened to
        // touch would let the old total sit there unchallenged.
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)
            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED

            h.taskState.value = completedDeleteState(toolType = SDMTool.Type.APPCLEANER)

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.FREEABLE
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a task from a tool the cleanup did not touch leaves the freed summary alone`() {
        // The task manager carries every tool's work, including read-only jobs like the Analyzer's
        // storage scan. Counting those would discard a valid cleanup outcome the moment the user
        // opened the storage overview — an everyday action right after cleaning.
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)
            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED

            h.taskState.value = completedDeleteState(toolType = SDMTool.Type.ANALYZER)

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a newer scan still clears a stale freed summary`() {
        val h = harness(corpseData = corpseData())

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)
            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.NOTHING_FREED

            // A newer scan supersedes the last cleanup's outcome; the bar goes back to "freeable".
            h.taskState.value = completedScanState(Instant.now())

            h.barState().heroSummary?.mode shouldBe HeroSummary.Mode.FREEABLE
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a non-Pro Deduplicator-only scan produces a locked-only hero`() {
        // The case the old DELETE-only hero gate swallowed: without Pro the Deduplicator never arms
        // DELETE, so the main action resolves to SCAN and buildHeroSummary was never reached — the
        // findings were invisible with no card at all. A builder unit test cannot catch that.
        val lockedSpace = 42L * 1024 * 1024
        val dedupeData = Deduplicator.Data(
            clusters = setOf(
                mockk<Duplicate.Cluster>(relaxed = true) {
                    every { redundantSize } returns lockedSpace
                    every { redundantCount } returns 4
                },
            ),
        )
        val h = harness(
            corpseFinderOneClick = false,
            deduplicatorOneClick = true,
            dedupeData = dedupeData,
            isPro = false,
        )

        try {
            val bar = h.barState()

            bar.actionState shouldBe BottomBarState.Action.SCAN
            val hero = bar.heroSummary!!
            hero.mode shouldBe HeroSummary.Mode.LOCKED_ONLY
            hero.totalSize shouldBe 0L
            hero.lockedSize shouldBe lockedSpace
            hero.lockedTools.map { it.type } shouldBe listOf(SDMTool.Type.DEDUPLICATOR)
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a zero-result cleanup still reports what stayed locked`() {
        // The cleanup ran on the free tool and freed nothing, while AppCleaner's findings sat there
        // unclaimed behind Pro — exactly the moment worth surfacing the upsell.
        val h = harness(
            corpseData = corpseData(),
            appData = AppCleaner.Data(
                junks = listOf(mockk(relaxed = true) { every { size } returns 999L; every { itemCount } returns 7 }),
            ),
            appCleanerOneClick = true,
            isPro = false,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!
            hero.mode shouldBe HeroSummary.Mode.NOTHING_FREED
            hero.lockedTools.map { it.type } shouldBe listOf(SDMTool.Type.APPCLEANER)
            hero.lockedSize shouldBe 999L
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `a tool the cleanup did submit to is residue, never also locked`() {
        // isProForUi() fails open to true, so a cleanup can submit a Pro-gated tool while the
        // combine's upgrade flow still reports non-Pro. Without the submitted-set filter the same
        // bytes would be counted twice under contradictory framing: "999 B left" next to
        // "unlock 999 B more with Pro".
        val h = harness(
            corpseFinderOneClick = false,
            appData = AppCleaner.Data(
                junks = listOf(mockk(relaxed = true) { every { size } returns 999L; every { itemCount } returns 7 }),
            ),
            appCleanerOneClick = true,
            isPro = false,
            repoIsPro = true,
        )

        try {
            h.engine.mainAction(BottomBarState.Action.DELETE)

            val hero = h.barState().heroSummary!!
            hero.residueCount shouldBe 7
            hero.lockedTools.shouldBeEmpty()
        } finally {
            h.engineScope.cancel()
        }
    }

    @Test
    fun `expandHero and dismissHero toggle the flag`() = withHarness { h ->
        h.engine.isHeroExpanded.value shouldBe false

        h.engine.expandHero()
        h.engine.isHeroExpanded.value shouldBe true

        h.engine.dismissHero()
        h.engine.isHeroExpanded.value shouldBe false
    }

    /**
     * Deduplicator-only post-delete setup. Bespoke rather than [harness] because this path needs both
     * deduplicator data and a real delete result, neither of which [harness] models.
     */
    private class DedupeDeleteSetup(
        val engine: DashboardMainActionEngine,
        val engineScope: CoroutineScope,
        val dedupState: MutableStateFlow<Deduplicator.State>,
    ) {
        fun hero(): HeroSummary = runBlocking {
            engine.bottomBarState(listIsReady = MutableStateFlow(true))
                .first { it?.heroSummary != null }!!
                .heroSummary!!
        }
    }

    private fun dedupeDeleteSetup(heroAutoShow: Boolean = true): DedupeDeleteSetup {
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
        // Real Data with a populated cluster, NOT a relaxed mock: `hasData` reads `clusters.isNotEmpty()`,
        // and a relaxed mock answers that with an empty set — which makes the main action resolve to SCAN
        // and no FREEABLE hero gets built at all, so a residue test against a mock would pass vacuously.
        val dedupData = Deduplicator.Data(
            clusters = setOf(
                mockk<Duplicate.Cluster>(relaxed = true) {
                    // Deliberately different from DEDUPE_FREED_SPACE so a headline sourced from the
                    // residue instead of the cleanup is distinguishable, not just a mode mismatch.
                    every { redundantSize } returns DEDUPE_RESIDUE_SPACE
                    every { redundantCount } returns 1
                },
            ),
        )
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
            every { dashboardHeroAutoShow } returns mockBool(heroAutoShow)
        }
        val deleteResult = DeduplicatorDeleteTask.Success(
            affectedSpace = DEDUPE_FREED_SPACE,
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
            onStateError = {},
        )
        return DedupeDeleteSetup(engine = engine, engineScope = engineScope, dedupState = dedupState)
    }

    @Test
    fun `freed hero includes the deduplicator removable-file count`() {
        // Regression for the post-delete path: a deduplicator-only cleanup must report the deleted
        // file count, not 0. The FREED itemCount used to exclude the deduplicator entirely.
        val setup = dedupeDeleteSetup()

        try {
            setup.engine.mainAction(BottomBarState.Action.DELETE)
            // A delete that clears everything prunes the now-empty data.
            setup.dedupState.value = Deduplicator.State(data = null, progress = null)

            val hero = setup.hero()

            hero.mode shouldBe HeroSummary.Mode.FREED
            hero.totalSize shouldBe DEDUPE_FREED_SPACE
            hero.itemCount shouldBe 2
            hero.tools.single { it.type == SDMTool.Type.DEDUPLICATOR }.count shouldBe 2
        } finally {
            setup.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup that freed something still reports it when residue survives`() {
        // Regression for "the one-click freed 1 GB but the card said 5.8 MB can be freed": some junk
        // can never be cleared — a locked system app's cache fails on every run — so a successful
        // cleanup routinely leaves data behind. That residue must not rebuild into a FREEABLE hero
        // that outranks the FREED one and makes the run read as if it found almost nothing.
        val setup = dedupeDeleteSetup()

        try {
            setup.engine.mainAction(BottomBarState.Action.DELETE)
            // Deliberately no pruning: the data survives the delete, as it does whenever anything was
            // un-clearable. The main action therefore still resolves to DELETE and a FREEABLE hero is
            // built from the leftovers — it just must not outrank the FREED one.

            val hero = setup.hero()

            hero.mode shouldBe HeroSummary.Mode.FREED
            // The cleanup's total, not the surviving cluster's DEDUPE_RESIDUE_SPACE.
            hero.totalSize shouldBe DEDUPE_FREED_SPACE
        } finally {
            setup.engineScope.cancel()
        }
    }

    @Test
    fun `a cleanup that freed data stays collapsed when auto-show is off`() {
        // The ordinary post-scan cleanup path: the FREED outcome is still built and still reaches the
        // bar's compact chip, it just does not throw the card open.
        val setup = dedupeDeleteSetup(heroAutoShow = false)

        try {
            setup.engine.mainAction(BottomBarState.Action.DELETE)
            setup.dedupState.value = Deduplicator.State(data = null, progress = null)

            setup.hero().mode shouldBe HeroSummary.Mode.FREED
            setup.engine.isHeroExpanded.value shouldBe false
        } finally {
            setup.engineScope.cancel()
        }
    }

    companion object {
        private const val DEDUPE_FREED_SPACE = 51L * 1024 * 1024
        private const val DEDUPE_RESIDUE_SPACE = 5L * 1024 * 1024
    }
}
