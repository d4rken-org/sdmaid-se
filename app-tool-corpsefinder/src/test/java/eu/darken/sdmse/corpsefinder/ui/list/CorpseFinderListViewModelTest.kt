package eu.darken.sdmse.corpsefinder.ui.list

import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.corpsefinder.core.Corpse
import eu.darken.sdmse.corpsefinder.core.CorpseFinder
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderDeleteTask
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.corpsefinder.ui.preview.previewCorpse
import eu.darken.sdmse.corpsefinder.ui.preview.previewLocalPathLookup
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class CorpseFinderListViewModelTest : BaseTest() {

    private fun corpse(name: String, size: Long): Corpse = previewCorpse(
        lookup = previewLocalPathLookup(
            pathSegments = arrayOf("storage", "emulated", "0", "Android", "data", name),
            size = size,
        ),
        content = emptyList(),
    )

    private fun cfState(
        data: CorpseFinder.Data? = null,
        progress: Progress.Data? = null,
    ): CorpseFinder.State = CorpseFinder.State(
        data = data,
        progress = progress,
        isFilterPrivateDataAvailable = false,
        isFilterDalvikCacheAvailable = false,
        isFilterArtProfilesAvailable = false,
        isFilterAppLibrariesAvailable = false,
        isFilterAppSourcesAvailable = false,
        isFilterPrivateAppSourcesAvailable = false,
        isFilterEncryptedAppResourcesAvailable = false,
    )

    private class Harness(
        val vm: CorpseFinderListViewModel,
        val corpseFinder: CorpseFinder,
        val taskSubmitter: TaskSubmitter,
        val dataFlow: MutableStateFlow<CorpseFinder.Data?>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
        /** Builds another ViewModel against the same mocks, i.e. a second entry into the screen. */
        val newVm: () -> CorpseFinderListViewModel,
    )

    private fun managedTask(
        id: String = "task-1",
        complete: Boolean = false,
        cancelled: Boolean = false,
        error: Throwable? = null,
        toolType: SDMTool.Type = SDMTool.Type.CORPSEFINDER,
    ): TaskSubmitter.ManagedTask = TaskSubmitter.ManagedTask(
        id = id,
        toolType = toolType,
        task = mockk(relaxed = true),
        queuedAt = Instant.now(),
        startedAt = Instant.now(),
        cancelledAt = if (cancelled) Instant.now() else null,
        completedAt = if (complete) Instant.now() else null,
        error = error,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        val job: Job,
    ) {
        fun cancel() {
            job.cancel()
        }
    }

    private fun CoroutineScope.collectEvents(
        vm: CorpseFinderListViewModel,
    ): CollectedEvents<CorpseFinderListViewModel.Event> {
        val list = mutableListOf<CorpseFinderListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        data: CorpseFinder.Data? = null,
        progress: Progress.Data? = null,
        tasks: Collection<TaskSubmitter.ManagedTask> = emptySet(),
    ): Harness {
        val dataFlow = MutableStateFlow(data)
        val progressFlow = MutableStateFlow(progress)
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            // Mirror production wiring: CorpseFinder.state is a combine over data and progress,
            // so a progress tick reaches the VM as a NEW State instance carrying the SAME Data
            // instance. Stubbing state and progress as independent flows would make the
            // rows-instance guard below pass vacuously.
            every { this@apply.state } returns combine(dataFlow, progressFlow) { d, p ->
                cfState(data = d, progress = p)
            }
            every { this@apply.progress } returns progressFlow
        }
        // A real flow, not the relaxed mock's empty one: the entry logic waits on
        // taskSubmitter.state.first { ... }, which against an empty flow dies with
        // NoSuchElementException inside vmScope - silently, leaving the whole path untested.
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = tasks))
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { this@apply.state } returns taskStateFlow
            // Explicit "accepted": the entry loop only exits on a non-null result, so leaving this
            // to the relaxed mock would make the loop's exit condition an implementation detail of
            // MockK. Tests that need a decline re-stub this.
            coEvery { submitIfToolIdle(any()) } returns mockk<SDMTool.Task.Result>(relaxed = true)
        }
        val newVm = {
            CorpseFinderListViewModel(
                dispatcherProvider = TestDispatcherProvider(),
                corpseFinder = corpseFinder,
                taskSubmitter = taskSubmitter,
            )
        }
        return Harness(newVm(), corpseFinder, taskSubmitter, dataFlow, progressFlow, taskStateFlow, newVm)
    }

    @Test
    fun `state rows is null when Data is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first().rows shouldBe null
    }

    @Test
    fun `state rows is empty when Data corpses is empty`() = runTest2 {
        val h = harness(data = CorpseFinder.Data(corpses = emptyList()))
        h.vm.state.first().rows shouldBe emptyList()
    }

    @Test
    fun `state rows are sorted by size descending`() = runTest2 {
        val small = corpse(name = "small", size = 100)
        val large = corpse(name = "large", size = 10_000)
        val medium = corpse(name = "medium", size = 1_000)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(small, large, medium)))

        val rows = h.vm.state.first().rows!!
        rows.map { it.corpse.lookup.lookedUp } shouldBe listOf(
            large.lookup.lookedUp,
            medium.lookup.lookedUp,
            small.lookup.lookedUp,
        )
    }

    @Test
    fun `state progress passes through`() = runTest2 {
        val progress = Progress.Data()
        val h = harness(
            data = CorpseFinder.Data(corpses = emptyList()),
            progress = progress,
        )
        h.vm.state.first().progress shouldBe progress
    }

    @Test
    fun `progress-only emission preserves the rows instance (no re-sort)`() = runTest2 {
        // P0 perf guard: progress is decoupled from row production, so a progress tick must not
        // re-run the sort/map pipeline. Checking only the published rows instance is NOT enough:
        // StateFlow's structural-equality conflation can preserve the old instance even while the
        // pipeline wastefully re-executed. The iteration counter observes the executions directly
        // (the init navUp watcher only calls isEmpty() and never iterates).
        val corpses = IterationCountingList(listOf(corpse("a", 100), corpse("b", 200)))
        val h = harness(data = CorpseFinder.Data(corpses = corpses))
        // Keep the WhileSubscribed state alive so the upstream chain actually runs.
        val job = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.state.collect { } }
        advanceUntilIdle()

        val rowsBefore = h.vm.state.value.rows
        rowsBefore.shouldNotBeNull()
        val iterationsBefore = corpses.iterations.get()

        // Emit a progress-only change; data is untouched.
        val tick = Progress.Data(extra = "tick")
        h.progressFlow.value = tick
        advanceUntilIdle()

        h.vm.state.value.progress shouldBe tick
        // The row pipeline did not touch the corpse list again...
        corpses.iterations.get() shouldBe iterationsBefore
        // ...and the published rows are still the exact same instance.
        (h.vm.state.value.rows === rowsBefore) shouldBe true
        job.cancel()
    }

    /** Counts iterations so tests can observe whether the sort/map pipeline (re-)executed. */
    private class IterationCountingList<T>(
        private val backing: List<T>,
    ) : List<T> by backing {
        val iterations = java.util.concurrent.atomic.AtomicInteger()
        override fun iterator(): Iterator<T> {
            iterations.incrementAndGet()
            return backing.iterator()
        }
    }

    @Test
    fun `onRowClick emits ConfirmDeletion with single id`() = runTest2 {
        val c = corpse("only", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))

        val row = h.vm.state.first().rows!!.single()
        h.vm.onRowClick(row)

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(c.identifier)
    }

    @Test
    fun `onDeleteSelected with empty set does nothing`() = runTest2 {
        val h = harness(data = CorpseFinder.Data(corpses = listOf(corpse("a", 1))))
        val collected = collectEvents(h.vm)

        h.vm.onDeleteSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        // A regression that emitted ConfirmDeletion(emptySet()) would still pass the
        // no-submit check above. The event-collector catches that case too.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onDeleteSelected non-empty emits ConfirmDeletion`() = runTest2 {
        val c = corpse("a", 1)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))

        h.vm.onDeleteSelected(setOf(c.identifier))

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(c.identifier)
    }

    @Test
    fun `onDeleteConfirmed submits task with valid ids only`() = runTest2 {
        val live = corpse("live", 100)
        val stale = corpse("stale", 200)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))

        val deleteSuccess = CorpseFinderDeleteTask.Success(
            affectedSpace = 100L,
            affectedPaths = setOf(live.lookup.lookedUp),
        )
        coEvery { h.taskSubmitter.submit(any()) } returns deleteSuccess

        h.vm.onDeleteConfirmed(setOf(live.identifier, stale.identifier))
        advanceUntilIdle()

        // The targeted call AND no other submit() calls — a regression that submitted a
        // valid task plus a stale one would still pass the specific match.
        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                CorpseFinderDeleteTask(targetCorpses = setOf(live.identifier)),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.TaskResult>()
        event.result shouldBe deleteSuccess
    }

    @Test
    fun `onDeleteConfirmed with all-stale ids does not submit`() = runTest2 {
        val live = corpse("live", 100)
        val stale1 = LocalPath.build("storage", "stale1")
        val stale2 = LocalPath.build("storage", "stale2")
        val h = harness(data = CorpseFinder.Data(corpses = listOf(live)))

        h.vm.onDeleteConfirmed(setOf(stale1, stale2))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onExcludeSelected with empty set does nothing`() = runTest2 {
        val c = corpse("a", 1)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val collected = collectEvents(h.vm)

        h.vm.onExcludeSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.corpseFinder.exclude(any()) }
        // Catches a regression that emitted ExclusionsCreated(count=0) with no exclude() call.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onExcludeSelected filters stale ids and calls corpseFinder exclude`() = runTest2 {
        val live = corpse("live", 100)
        val staleId = LocalPath.build("storage", "stale")
        val data = CorpseFinder.Data(corpses = listOf(live))
        val h = harness(data = data)

        coEvery { h.corpseFinder.exclude(any()) } returns CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("exclusion-id-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(live.identifier, staleId))
        advanceUntilIdle()

        // Specific match + no extra exclude() calls.
        coVerify(exactly = 1) { h.corpseFinder.exclude(setOf(live.identifier)) }
        coVerify(exactly = 1) { h.corpseFinder.exclude(any()) }
    }

    @Test
    fun `onExcludeSelected event count reflects saved-exclusion count not validIds size`() = runTest2 {
        // Regression test for what used to be FIXME(corpsefinder-list-exclusion-count): the VM
        // now reads `undo.exclusionIds.size` from corpseFinder.exclude(), mirroring the details
        // VM. When ExclusionManager.save() coalesces duplicates (or otherwise saves fewer
        // exclusions than requested), the snackbar count must match what was actually saved.
        val a = corpse("a", 100)
        val b = corpse("b", 200)
        val data = CorpseFinder.Data(corpses = listOf(a, b))
        val h = harness(data = data)

        // Two ids selected, but only one exclusion actually saved (coalesced duplicate).
        coEvery { h.corpseFinder.exclude(any()) } returns CorpseFinder.ExclusionUndo(
            exclusionIds = setOf("only-one-saved"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(a.identifier, b.identifier))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.corpseFinder.exclude(setOf(a.identifier, b.identifier)) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<CorpseFinderListViewModel.Event.ExclusionsCreated>()
        // Fixed: emits undo.exclusionIds.size = 1, not validIds.size = 2.
        event.count shouldBe 1
    }

    @Test
    fun `init navigates up when Data drains from non-empty to empty`() = runTest2 {
        val c = corpse("a", 100)
        val initial = CorpseFinder.Data(corpses = listOf(c))
        val h = harness(data = initial)

        // drop(1) skips the initial replay; the second emission with empty data triggers navUp.
        h.dataFlow.value = CorpseFinder.Data(corpses = emptyList())
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does not navigate up when Data transitions from non-empty to null`() = runTest2 {
        // Regression: data going to `null` indicates a fresh scan started (CorpseFinder sets
        // internalData = null at the top of performScan). That's a loading state, not an
        // "everything was excluded" state. navUp must NOT fire during loading.
        val c = corpse("a", 100)
        val h = harness(data = CorpseFinder.Data(corpses = listOf(c)))
        val collected = collectNavEvents(h.vm)

        h.dataFlow.value = null
        advanceUntilIdle()

        // No navUp emitted — the tightened filter (data != null && data.corpses.isEmpty())
        // rejects the null transition.
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    private fun CoroutineScope.collectNavEvents(
        vm: CorpseFinderListViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectErrors(
        vm: CorpseFinderListViewModel,
    ): CollectedEvents<Throwable> {
        val list = mutableListOf<Throwable>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.errorEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    // ──────────────────────────── entry / cold scan ────────────────────────────

    @Test
    fun `constructing the ViewModel does not push an error into errorEvents`() = runTest2 {
        // Guards the harness itself: with a relaxed TaskSubmitter mock the entry logic's
        // first { ... } dies on an empty flow and every entry test below would pass vacuously.
        val h = harness(data = null)
        val errors = collectErrors(h.vm)
        advanceUntilIdle()

        errors.list shouldBe emptyList()
        errors.cancel()
    }

    @Test
    fun `cold entry without data submits exactly one scan`() = runTest2 {
        val h = harness(data = null)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(CorpseFinderScanTask()) }
        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(any()) }
        // The entry scan must go through the atomic API, never plain submit().
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `entry with data present does not scan`() = runTest2 {
        val h = harness(data = CorpseFinder.Data(corpses = listOf(corpse("a", 100))))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
    }

    @Test
    fun `entry during a task that completes with data does not scan`() = runTest2 {
        val h = harness(data = null, tasks = setOf(managedTask()))
        advanceUntilIdle()
        // Still waiting on the in-flight task, so nothing submitted yet.
        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }

        h.dataFlow.value = CorpseFinder.Data(corpses = listOf(corpse("a", 100)))
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask(complete = true)))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
    }

    @Test
    fun `entry during a task that completes without data scans`() = runTest2 {
        // The uninstall watcher's task completes normally but never assigns data. Bailing out on it
        // would strand the screen on the loading placeholder.
        val h = harness(data = null, tasks = setOf(managedTask()))
        advanceUntilIdle()

        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask(complete = true)))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(CorpseFinderScanTask()) }
    }

    @Test
    fun `entry during a task that gets cancelled navigates up instead of scanning`() = runTest2 {
        val h = harness(data = null, tasks = setOf(managedTask()))
        val nav = collectNavEvents(h.vm)
        advanceUntilIdle()

        h.taskStateFlow.value = TaskSubmitter.State(
            tasks = setOf(managedTask(complete = true, cancelled = true)),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
        nav.list shouldBe listOf(NavEvent.Up)
        nav.cancel()
    }

    @Test
    fun `entry during a task that fails navigates up instead of scanning`() = runTest2 {
        val h = harness(data = null, tasks = setOf(managedTask()))
        val nav = collectNavEvents(h.vm)
        advanceUntilIdle()

        h.taskStateFlow.value = TaskSubmitter.State(
            tasks = setOf(managedTask(complete = true, error = IllegalStateException("nope"))),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
        nav.list shouldBe listOf(NavEvent.Up)
        nav.cancel()
    }

    @Test
    fun `two entries racing the same registration produce exactly one scan`() = runTest2 {
        // Mirrors TaskManager.submitIfToolIdle: registration only happens while the tool has no
        // incomplete task, and both callers get here before either registered. A ViewModel that
        // used plain submit() would produce two full scans, the second wiping the first's results.
        val h = harness(data = null)
        val registered = mutableListOf<Any>()
        coEvery { h.taskSubmitter.submitIfToolIdle(any()) } coAnswers {
            if (registered.isEmpty()) {
                registered.add(firstArg())
                mockk<SDMTool.Task.Result>(relaxed = true)
            } else {
                // Declined, because the winner's task is in flight by the time we get here.
                h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask()))
                null
            }
        }

        h.newVm()
        h.newVm()
        advanceUntilIdle()

        registered.size shouldBe 1
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `an entry scan that fails surfaces the error and shows the empty state`() = runTest2 {
        val h = harness(data = null)
        val boom = IllegalStateException("scan blew up")
        coEvery { h.taskSubmitter.submitIfToolIdle(any()) } throws boom

        val vm = h.newVm()
        val errors = collectErrors(vm)
        val nav = collectNavEvents(vm)
        val stateJob = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        errors.list shouldBe listOf(boom)
        // navUp would dispose this screen's host before it can render the error dialog, so the
        // user would land on the Dashboard with no indication that anything went wrong.
        nav.list shouldBe emptyList()
        // Nothing is going to deliver data anymore: show the empty state, not a forever placeholder.
        vm.state.value.rows shouldBe emptyList()

        stateJob.cancel()
        errors.cancel()
        nav.cancel()
    }

    @Test
    fun `an entry scan that is cancelled navigates up`() = runTest2 {
        val h = harness(data = null)
        coEvery { h.taskSubmitter.submitIfToolIdle(any()) } throws CancellationException("user cancelled")

        val vm = h.newVm()
        val nav = collectNavEvents(vm)
        val errors = collectErrors(vm)
        advanceUntilIdle()

        nav.list shouldBe listOf(NavEvent.Up)
        errors.list shouldBe emptyList()
        nav.cancel()
        errors.cancel()
    }

    @Test
    fun `ViewModel teardown during the entry scan does not navigate up`() = runTest2 {
        val h = harness(data = null)
        coEvery { h.taskSubmitter.submitIfToolIdle(any()) } coAnswers {
            // What teardown looks like from inside the submit: our own job is cancelled. That must
            // rethrow instead of being read as "the user cancelled the scan" and navigating.
            currentCoroutineContext().cancel()
            throw CancellationException("ViewModel cleared")
        }

        val vm = h.newVm()
        val nav = collectNavEvents(vm)
        val stateJob = launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        // Teardown is not a scan failure, so no empty state is faked either.
        vm.state.value.rows shouldBe null

        stateJob.cancel()
        nav.cancel()
    }

    @Test
    fun `a declined entry submit is retried after the competing task completes without data`() = runTest2 {
        val h = harness(data = null)
        val submitted = mutableListOf<Any>()
        coEvery { h.taskSubmitter.submitIfToolIdle(any()) } coAnswers {
            submitted.add(firstArg())
            if (submitted.size == 1) {
                // Declined: a competing task (the uninstall watcher) registered inside the race
                // window and is now in flight.
                h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask(id = "competing")))
                null
            } else {
                mockk<SDMTool.Task.Result>(relaxed = true)
            }
        }

        h.newVm()
        advanceUntilIdle()
        submitted.size shouldBe 1

        // The competing task completes normally but never assigns data. Treating the decline as
        // "someone else will deliver" would leave the screen loading forever.
        h.taskStateFlow.value = TaskSubmitter.State(
            tasks = setOf(managedTask(id = "competing", complete = true)),
        )
        advanceUntilIdle()

        submitted shouldBe listOf(CorpseFinderScanTask(), CorpseFinderScanTask())
    }

    // ─────────────────────────────── navUp guards ───────────────────────────────

    @Test
    fun `a cold scan that finds nothing does not navigate up`() = runTest2 {
        val h = harness(data = null)
        val nav = collectNavEvents(h.vm)
        advanceUntilIdle()

        // The scan lands its (empty) result...
        h.dataFlow.value = CorpseFinder.Data(corpses = emptyList())
        advanceUntilIdle()
        // ...a progress tick re-emits the same Data through the tool's combine...
        h.progressFlow.value = Progress.Data(extra = "tick")
        advanceUntilIdle()
        // ...and the scan writes lastResult, which changes Data but not the corpses (E2). Deduping
        // on Data instead of on the corpse collection would let this slip past drop(1).
        h.dataFlow.value = CorpseFinder.Data(
            corpses = emptyList(),
            lastResult = CorpseFinderDeleteTask.Success(affectedSpace = 0L, affectedPaths = emptySet()),
        )
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }
}
