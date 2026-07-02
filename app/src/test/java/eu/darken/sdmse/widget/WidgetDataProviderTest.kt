package eu.darken.sdmse.widget

import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.shortcuts.OneTapRunGuard
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.widget.WidgetRenderState.Data.StorageEntry.Kind
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue
import java.time.Instant

class WidgetDataProviderTest : BaseTest() {

    private val spaceTracker = mockk<SpaceTracker>()
    private val statsSettings = mockk<StatsSettings>()
    private val taskSubmitter = mockk<TaskSubmitter>()
    private val oneTapRunGuard = OneTapRunGuard()

    // The provider shares its render chain in the app scope (shareLatest). Tests pass the TestScope
    // itself so the sharing machinery runs under advanceUntilIdle — which is also why every test
    // constructing the provider uses runTest2(autoCancel = true): the sharing coroutine never
    // completes on its own.
    private fun TestScope.provider() =
        WidgetDataProvider(this, spaceTracker, statsSettings, taskSubmitter, oneTapRunGuard)

    private fun snapshot(free: Long, capacity: Long) = SpaceTracker.StorageSnapshot(
        storageId = "s",
        spaceFree = free,
        spaceCapacity = capacity,
    )

    private fun activeTaskState() = TaskSubmitter.State(
        tasks = listOf(
            TaskSubmitter.ManagedTask(
                id = "t",
                toolType = SDMTool.Type.CORPSEFINDER,
                task = mockk(),
                startedAt = Instant.now(),
            ),
        ),
    )

    private fun cancellingTaskState() = TaskSubmitter.State(
        tasks = listOf(
            TaskSubmitter.ManagedTask(
                id = "t",
                toolType = SDMTool.Type.CORPSEFINDER,
                task = mockk(),
                startedAt = Instant.now(),
                cancelledAt = Instant.now(), // cancelling: cancelled but not yet completed
            ),
        ),
    )

    private fun stub(
        primary: SpaceTracker.StorageSnapshot?,
        secondary: List<SpaceTracker.StorageSnapshot> = emptyList(),
        freed: Long = 0L,
        taskState: TaskSubmitter.State = TaskSubmitter.State(),
    ) {
        coEvery { spaceTracker.readPrimaryStorage() } returns primary
        coEvery { spaceTracker.readSecondaryStorages() } returns secondary
        every { statsSettings.totalSpaceFreed } returns mockDataStoreValue(freed)
        every { taskSubmitter.state } returns MutableStateFlow(taskState)
    }

    @Test
    fun `maps primary storage and freed into Data`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500), freed = 42L)

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.size shouldBe 1
        state.storages[0].kind shouldBe Kind.INTERNAL
        state.storages[0].usedBytes shouldBe 400
        state.storages[0].totalBytes shouldBe 500
        state.freedBytes shouldBe 42
    }

    @Test
    fun `includes secondary storages after the primary`() = runTest2(autoCancel = true) {
        stub(
            primary = snapshot(free = 100, capacity = 500),
            secondary = listOf(snapshot(free = 20, capacity = 200)),
        )

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.size shouldBe 2
        state.storages[0].kind shouldBe Kind.INTERNAL
        state.storages[1].kind shouldBe Kind.EXTERNAL
        state.storages[1].usedBytes shouldBe 180
        state.storages[1].totalBytes shouldBe 200
    }

    @Test
    fun `caps the number of storages`() = runTest2(autoCancel = true) {
        stub(
            primary = snapshot(free = 1, capacity = 100),
            secondary = (1..5).map { snapshot(free = 1, capacity = 100) },
        )

        provider().snapshot().let {
            it.shouldBeInstanceOf<WidgetRenderState.Data>()
            it.storages.size shouldBe 3
        }
    }

    @Test
    fun `no readable storage is Unavailable`() = runTest2(autoCancel = true) {
        stub(primary = null, secondary = emptyList())

        provider().snapshot() shouldBe WidgetRenderState.Unavailable
    }

    @Test
    fun `zero-capacity volumes are dropped`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 0, capacity = 0), secondary = listOf(snapshot(free = 0, capacity = 0)))

        provider().snapshot() shouldBe WidgetRenderState.Unavailable
    }

    @Test
    fun `free exceeding capacity is clamped, not negative`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 800, capacity = 500))

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.storages.single().usedBytes shouldBe 0
        state.storages.single().totalBytes shouldBe 500
    }

    @Test
    fun `negative freed is coerced to zero`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500), freed = -10L)

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.freedBytes shouldBe 0
    }

    @Test
    fun `usedRatio is a clamped fraction`() {
        WidgetRenderState.Data.StorageEntry(Kind.INTERNAL, usedBytes = 250, totalBytes = 1000).usedRatio shouldBe 0.25f
        WidgetRenderState.Data.StorageEntry(Kind.INTERNAL, usedBytes = 0, totalBytes = 0).usedRatio shouldBe 0f
    }

    @Test
    fun `isWorking is false when idle and the guard is not running`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500))

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.isWorking shouldBe false
        state.isCancellable shouldBe false
    }

    @Test
    fun `an active task is working and cancellable`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500), taskState = activeTaskState())

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.isWorking shouldBe true
        state.isCancellable shouldBe true
    }

    @Test
    fun `a cancelling task is still working but no longer cancellable`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500), taskState = cancellingTaskState())

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.isWorking shouldBe true
        state.isCancellable shouldBe false
    }

    @Test
    fun `a running OneTap guard is working and cancellable even if TaskManager reports idle`() = runTest2(autoCancel = true) {
        stub(primary = snapshot(free = 100, capacity = 500)) // TaskManager idle
        oneTapRunGuard.tryStart(Job())

        val state = provider().snapshot()

        state.shouldBeInstanceOf<WidgetRenderState.Data>()
        state.isWorking shouldBe true
        state.isCancellable shouldBe true
    }

    private class Harness(val provider: WidgetDataProvider, val states: List<WidgetRenderState>)

    private fun TestScope.collectStates(
        freedFlow: MutableStateFlow<Long>,
        taskFlow: MutableStateFlow<TaskSubmitter.State>,
    ): Harness {
        coEvery { spaceTracker.readPrimaryStorage() } returns snapshot(free = 100, capacity = 500)
        coEvery { spaceTracker.readSecondaryStorages() } returns emptyList()
        every { statsSettings.totalSpaceFreed } returns mockk { every { flow } returns freedFlow }
        every { taskSubmitter.state } returns taskFlow

        val provider = provider()
        val states = mutableListOf<WidgetRenderState>()
        // Foreground launch on purpose: advanceUntilIdle() only runs foreground scheduler tasks,
        // a backgroundScope collector would never execute. The never-completing collect is reaped
        // by runTest2(autoCancel = true) on each test.
        launch { provider.renderState.collect { states.add(it) } }
        return Harness(provider, states)
    }

    @Test
    fun `freed bumps are latched while working and released once the run settles`() = runTest2(autoCancel = true) {
        val freedFlow = MutableStateFlow(100L)
        val taskFlow = MutableStateFlow(TaskSubmitter.State())
        val harness = collectStates(freedFlow, taskFlow)
        val states = harness.states
        advanceUntilIdle()

        taskFlow.value = activeTaskState()
        advanceUntilIdle()
        // Per-tool increments land while the run is in progress — the widget must not tick.
        freedFlow.value = 200L
        advanceUntilIdle()
        freedFlow.value = 250L
        advanceUntilIdle()

        val duringRun = states.last()
        duringRun.shouldBeInstanceOf<WidgetRenderState.Data>()
        duringRun.isWorking shouldBe true
        duringRun.freedBytes shouldBe 100L
        states.filterIsInstance<WidgetRenderState.Data>().none { it.freedBytes == 200L } shouldBe true

        taskFlow.value = TaskSubmitter.State()
        advanceUntilIdle()

        val settled = states.last()
        settled.shouldBeInstanceOf<WidgetRenderState.Data>()
        settled.isWorking shouldBe false
        settled.freedBytes shouldBe 250L
    }

    @Test
    fun `a late stats write right after idle still comes through`() = runTest2(autoCancel = true) {
        // Task-idle is not guaranteed to be stats-settled: the last tool's increment can land a
        // beat after the run flips idle. It must not stay latched forever.
        val freedFlow = MutableStateFlow(100L)
        val taskFlow = MutableStateFlow(activeTaskState())
        val harness = collectStates(freedFlow, taskFlow)
        val states = harness.states
        advanceUntilIdle()

        taskFlow.value = TaskSubmitter.State()
        advanceUntilIdle()
        freedFlow.value = 300L
        advanceUntilIdle()

        val latest = states.last()
        latest.shouldBeInstanceOf<WidgetRenderState.Data>()
        latest.freedBytes shouldBe 300L
    }

    @Test
    fun `freed changes while idle pass through immediately`() = runTest2(autoCancel = true) {
        val freedFlow = MutableStateFlow(100L)
        val taskFlow = MutableStateFlow(TaskSubmitter.State())
        val harness = collectStates(freedFlow, taskFlow)
        val states = harness.states
        advanceUntilIdle()

        freedFlow.value = 150L
        advanceUntilIdle()

        val latest = states.last()
        latest.shouldBeInstanceOf<WidgetRenderState.Data>()
        latest.freedBytes shouldBe 150L
    }

    @Test
    fun `a subscriber joining mid-run sees the latched value, not a partial one`() = runTest2(autoCancel = true) {
        // The latch lives in the shared chain, not per collector: a Glance session created mid-run
        // (widget resize, launcher restart) must agree with the collectors that were already latched.
        val freedFlow = MutableStateFlow(100L)
        val taskFlow = MutableStateFlow(TaskSubmitter.State())
        val harness = collectStates(freedFlow, taskFlow)
        advanceUntilIdle()

        taskFlow.value = activeTaskState()
        advanceUntilIdle()
        freedFlow.value = 200L
        advanceUntilIdle()

        val lateSnapshot = harness.provider.snapshot()
        lateSnapshot.shouldBeInstanceOf<WidgetRenderState.Data>()
        lateSnapshot.isWorking shouldBe true
        lateSnapshot.freedBytes shouldBe 100L
    }
}
