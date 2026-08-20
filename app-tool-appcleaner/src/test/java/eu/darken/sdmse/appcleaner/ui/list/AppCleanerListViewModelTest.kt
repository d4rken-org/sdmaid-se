package eu.darken.sdmse.appcleaner.ui.list

import android.content.Context
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class AppCleanerListViewModelTest : BaseTest() {

    private fun installId(pkgName: String): InstallId =
        InstallId(pkgId = Pkg.Id(name = pkgName), userHandle = UserHandle2(handleId = 0))

    private fun junk(pkgName: String, sizeHint: Long = 0L): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
        // expendables/inaccessibleCache come from the preview defaults; the `size` field is lazy
        // off those. Providing a custom `sizeHint` would mean injecting bespoke matches which is
        // unnecessary for the assertions below (they compare sizes as relative, not exact).
    )

    private fun appState(
        data: AppCleaner.Data? = null,
        progress: Progress.Data? = null,
    ): AppCleaner.State = AppCleaner.State(
        data = data,
        progress = progress,
        isOtherUsersAvailable = false,
        isRunningAppsDetectionAvailable = false,
        isInaccessibleCacheAvailable = false,
        isAcsRequired = false,
    )

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
        every { isSettled } returns true
    }

    private class Harness(
        val vm: AppCleanerListViewModel,
        val appCleaner: AppCleaner,
        val taskSubmitter: TaskSubmitter,
        val upgradeRepo: UpgradeRepo,
        val stateFlow: MutableStateFlow<AppCleaner.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
        /** Builds another ViewModel against the same mocks, i.e. a second entry into the screen. */
        val newVm: () -> AppCleanerListViewModel,
    )

    private fun managedTask(
        id: String = "task-1",
        complete: Boolean = false,
        cancelled: Boolean = false,
        error: Throwable? = null,
        toolType: SDMTool.Type = SDMTool.Type.APPCLEANER,
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
        fun cancel() { job.cancel() }
    }

    private fun CoroutineScope.collectEvents(
        vm: AppCleanerListViewModel,
    ): CollectedEvents<AppCleanerListViewModel.Event> {
        val list = mutableListOf<AppCleanerListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(
        vm: AppCleanerListViewModel,
    ): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        data: AppCleaner.Data? = null,
        progress: Progress.Data? = null,
        isPro: Boolean = true,
        tasks: Collection<TaskSubmitter.ManagedTask> = emptySet(),
    ): Harness {
        val stateFlow = MutableStateFlow(appState(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
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
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
        }
        val context = mockk<Context>(relaxed = true)
        val newVm = {
            AppCleanerListViewModel(
                dispatcherProvider = TestDispatcherProvider(),
                context = context,
                appCleaner = appCleaner,
                taskSubmitter = taskSubmitter,
                upgradeRepo = upgradeRepo,
            )
        }
        return Harness(
            newVm(), appCleaner, taskSubmitter, upgradeRepo, stateFlow, progressFlow, taskStateFlow, newVm,
        )
    }

    @Test
    fun `state rows is null when Data is null`() = runTest2 {
        val h = harness(data = null)
        h.vm.state.first().rows shouldBe null
    }

    @Test
    fun `state rows is empty when Data junks is empty`() = runTest2 {
        val h = harness(data = AppCleaner.Data(junks = emptyList()))
        h.vm.state.first().rows shouldBe emptyList()
    }

    @Test
    fun `state rows include every junk identifier when populated`() = runTest2 {
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val h = harness(data = AppCleaner.Data(junks = listOf(a, b)))

        val rows = h.vm.state.first().rows!!
        rows.map { it.identifier }.toSet() shouldBe setOf(a.identifier, b.identifier)
    }

    @Test
    fun `state progress passes through`() = runTest2 {
        val progress = Progress.Data()
        val h = harness(
            data = AppCleaner.Data(junks = emptyList()),
            progress = progress,
        )
        h.vm.state.first().progress shouldBe progress
    }

    @Test
    fun `state totalCount reflects junks size`() = runTest2 {
        val h = harness(
            data = AppCleaner.Data(junks = listOf(junk("com.example.a"), junk("com.example.b"))),
        )
        h.vm.state.first().totalCount shouldBe 2
    }

    @Test
    fun `onRowClick emits ConfirmDeletion with single id`() = runTest2 {
        val a = junk("com.example.only")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))

        val row = h.vm.state.first().rows!!.single()
        h.vm.onRowClick(row)

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppCleanerListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(a.identifier)
    }

    @Test
    fun `onDeleteSelected with empty set does nothing`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        val collected = collectEvents(h.vm)

        h.vm.onDeleteSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onDeleteSelected non-empty emits ConfirmDeletion`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))

        h.vm.onDeleteSelected(setOf(a.identifier))

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppCleanerListViewModel.Event.ConfirmDeletion>()
        event.ids shouldBe setOf(a.identifier)
    }

    @Test
    fun `onRowClick navs to upgrade and does not confirm when not pro`() = runTest2 {
        val a = junk("com.example.only")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)), isPro = false)
        val navCollected = collectNavEvents(h.vm)
        val collected = collectEvents(h.vm)

        val row = h.vm.state.first().rows!!.single()
        h.vm.onRowClick(row)
        advanceUntilIdle()

        navCollected.list.any { it is NavEvent.GoTo } shouldBe true
        collected.list shouldBe emptyList()
        navCollected.cancel()
        collected.cancel()
    }

    @Test
    fun `onDeleteSelected navs to upgrade and does not confirm when not pro`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)), isPro = false)
        val navCollected = collectNavEvents(h.vm)
        val collected = collectEvents(h.vm)

        h.vm.onDeleteSelected(setOf(a.identifier))
        advanceUntilIdle()

        navCollected.list.any { it is NavEvent.GoTo } shouldBe true
        collected.list shouldBe emptyList()
        navCollected.cancel()
        collected.cancel()
    }

    @Test
    fun `onDeleteConfirmed submits task with valid ids only`() = runTest2 {
        val live = junk("com.example.live")
        val stale = installId("com.example.stale")
        val h = harness(data = AppCleaner.Data(junks = listOf(live)))

        val successResult = AppCleanerProcessingTask.Success(
            affectedSpace = 100L,
            affectedPaths = emptySet(),
        )
        coEvery { h.taskSubmitter.submit(any()) } returns successResult

        h.vm.onDeleteConfirmed(setOf(live.identifier, stale))
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(AppCleanerProcessingTask(targetPkgs = setOf(live.identifier)))
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppCleanerListViewModel.Event.TaskResult>()
        event.result shouldBe successResult
    }

    @Test
    fun `onDeleteConfirmed with all-stale ids does not submit`() = runTest2 {
        val live = junk("com.example.live")
        val h = harness(data = AppCleaner.Data(junks = listOf(live)))

        h.vm.onDeleteConfirmed(setOf(installId("com.stale1"), installId("com.stale2")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onDeleteConfirmed navs to upgrade when not pro`() = runTest2 {
        val live = junk("com.example.live")
        val h = harness(data = AppCleaner.Data(junks = listOf(live)), isPro = false)
        val navCollected = collectNavEvents(h.vm)

        h.vm.onDeleteConfirmed(setOf(live.identifier))
        advanceUntilIdle()

        // Nav to UpgradeRoute — observed as a GoTo nav event with the upgrade destination.
        navCollected.list.any { it is NavEvent.GoTo } shouldBe true
        // Task must not be submitted before user upgrades.
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        navCollected.cancel()
    }

    @Test
    fun `onExcludeSelected with empty set does nothing`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        val collected = collectEvents(h.vm)

        h.vm.onExcludeSelected(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.appCleaner.exclude(any<Set<InstallId>>()) }
        collected.list shouldBe emptyList()
        collected.cancel()
    }

    @Test
    fun `onExcludeSelected filters stale ids and calls appCleaner exclude`() = runTest2 {
        val live = junk("com.example.live")
        val stale = installId("com.example.stale")
        val data = AppCleaner.Data(junks = listOf(live))
        val h = harness(data = data)

        coEvery { h.appCleaner.exclude(any<Set<InstallId>>()) } returns AppCleaner.ExclusionUndo(
            exclusionIds = setOf("excl-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(live.identifier, stale))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.appCleaner.exclude(setOf(live.identifier)) }
        coVerify(exactly = 1) { h.appCleaner.exclude(any<Set<InstallId>>()) }
    }

    @Test
    fun `onExcludeSelected event count reflects saved-exclusion count not validIds size`() = runTest2 {
        // Regression test: ExclusionsCreated.count must come from undo.exclusionIds.size — the
        // count of exclusions actually saved — not from the number of ids submitted. When
        // ExclusionManager.save() coalesces duplicates, the snackbar count must match what was
        // actually saved. Mirrors the analogous CorpseFinder fix.
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val data = AppCleaner.Data(junks = listOf(a, b))
        val h = harness(data = data)

        // Two ids in, but only ONE saved exclusion (coalesced duplicate).
        coEvery { h.appCleaner.exclude(any<Set<InstallId>>()) } returns AppCleaner.ExclusionUndo(
            exclusionIds = setOf("only-one-saved"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onExcludeSelected(setOf(a.identifier, b.identifier))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.appCleaner.exclude(setOf(a.identifier, b.identifier)) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppCleanerListViewModel.Event.ExclusionsCreated>()
        // Fixed: emits undo.exclusionIds.size = 1, not validIds.size = 2.
        event.count shouldBe 1
    }

    @Test
    fun `init navigates up when Data drains from non-empty to empty`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))

        h.stateFlow.value = appState(data = AppCleaner.Data(junks = emptyList()))
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does not navigate up when Data transitions from non-empty to null`() = runTest2 {
        // Regression: data going to `null` indicates `performScan` just started (AppCleaner sets
        // internalData = null at the top of performScan). That's a loading state, not a "drained"
        // state — navUp must NOT fire during loading. The tightened filter
        // `it?.junks?.isEmpty() == true` rejects null and only fires on a non-null empty Data.
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        val navCollected = collectNavEvents(h.vm)

        h.stateFlow.value = appState(data = null)
        advanceUntilIdle()

        // The tightened filter (`data != null && data.junks.isEmpty()`) rejects the null
        // transition — no navUp emitted while a refresh is in progress.
        navCollected.list shouldBe emptyList()
        navCollected.cancel()
    }

    // ──────────────────────────── entry / cold scan ────────────────────────────

    private fun CoroutineScope.collectErrors(
        vm: AppCleanerListViewModel,
    ): CollectedEvents<Throwable> {
        val list = mutableListOf<Throwable>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.errorEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

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

        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(AppCleanerScanTask()) }
        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(any()) }
        // The entry scan must go through the atomic API, never plain submit().
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `entry with data present does not scan`() = runTest2 {
        val h = harness(data = AppCleaner.Data(junks = listOf(junk("com.example.a"))))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
    }

    @Test
    fun `entry during a task that completes with data does not scan`() = runTest2 {
        val h = harness(data = null, tasks = setOf(managedTask()))
        advanceUntilIdle()
        // Still waiting on the in-flight task, so nothing submitted yet.
        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }

        h.stateFlow.value = appState(data = AppCleaner.Data(junks = listOf(junk("com.example.a"))))
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask(complete = true)))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submitIfToolIdle(any()) }
    }

    @Test
    fun `entry during a task that completes without data scans`() = runTest2 {
        // A task can complete normally without ever assigning data. Bailing out on it would strand
        // the screen on the loading placeholder.
        val h = harness(data = null, tasks = setOf(managedTask()))
        advanceUntilIdle()

        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(managedTask(complete = true)))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.taskSubmitter.submitIfToolIdle(AppCleanerScanTask()) }
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
                // Declined: a competing task registered inside the race window and is now in flight.
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

        submitted shouldBe listOf(AppCleanerScanTask(), AppCleanerScanTask())
    }

    @Test
    fun `a cold scan that finds nothing does not navigate up`() = runTest2 {
        val h = harness(data = null)
        val nav = collectNavEvents(h.vm)
        advanceUntilIdle()

        // The scan lands its (empty) result...
        h.stateFlow.value = appState(data = AppCleaner.Data(junks = emptyList()))
        advanceUntilIdle()
        // ...and a progress tick re-emits the same Data through the tool's combine. Without the
        // dedupe that second identical emission would get past drop(1) and navigate away.
        h.stateFlow.value = appState(
            data = AppCleaner.Data(junks = emptyList()),
            progress = Progress.Data(extra = "tick"),
        )
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }
}
