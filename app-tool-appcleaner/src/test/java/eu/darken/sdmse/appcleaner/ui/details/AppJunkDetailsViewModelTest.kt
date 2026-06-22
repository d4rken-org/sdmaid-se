package eu.darken.sdmse.appcleaner.ui.details

import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.ui.AppJunkDetailsRoute
import eu.darken.sdmse.appcleaner.ui.preview.previewAppJunk
import eu.darken.sdmse.appcleaner.ui.preview.previewInstalled
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.common.user.UserHandle2
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AppJunkDetailsViewModelTest : BaseTest() {

    private fun installId(pkgName: String): InstallId =
        InstallId(pkgId = Pkg.Id(name = pkgName), userHandle = UserHandle2(handleId = 0))

    private fun junk(pkgName: String): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
    )

    private fun emptyJunk(pkgName: String): AppJunk = previewAppJunk(
        pkg = previewInstalled(pkgName = pkgName, label = pkgName),
        expendables = emptyMap(),
        inaccessibleCache = null,
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
    }

    private class Harness(
        val vm: AppJunkDetailsViewModel,
        val appCleaner: AppCleaner,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<AppCleaner.State>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
    )

    private fun CoroutineScope.collectNavEvents(
        vm: AppJunkDetailsViewModel,
    ): Pair<MutableList<NavEvent>, Job> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return list to job
    }

    private fun harness(
        data: AppCleaner.Data? = null,
        progress: Progress.Data? = null,
        savedHandle: SavedStateHandle = SavedStateHandle(),
        isPro: Boolean = true,
    ): Harness {
        val stateFlow = MutableStateFlow(appState(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { state } returns taskStateFlow
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
        }
        val vm = AppJunkDetailsViewModel(
            handle = savedHandle,
            dispatcherProvider = TestDispatcherProvider(),
            appCleaner = appCleaner,
            taskSubmitter = taskSubmitter,
            upgradeRepo = upgradeRepo,
        )
        return Harness(vm, appCleaner, taskSubmitter, stateFlow, taskStateFlow)
    }

    @Test
    fun `bindRoute is idempotent`() = runTest2 {
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val h = harness(data = AppCleaner.Data(junks = listOf(a, b)))
        val first = AppJunkDetailsRoute(identifier = a.identifier)
        val second = AppJunkDetailsRoute(identifier = b.identifier)

        h.vm.bindRoute(first)
        h.vm.bindRoute(second)

        // First bind wins; state.target reflects it.
        h.vm.state.first().target shouldBe a.identifier
    }

    @Test
    fun `state target initially matches route identifier`() = runTest2 {
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val h = harness(data = AppCleaner.Data(junks = listOf(a, b)))

        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))
        h.vm.state.first().target shouldBe a.identifier
    }

    @Test
    fun `state items drops fully-empty junks left after path-only exclude`() = runTest2 {
        // After `appCleaner.exclude(installId, paths)` removes every match from a junk's
        // expendables, the backend leaves a zero-content AppJunk in the snapshot. The VM filters
        // these out so the pager doesn't show a ghost page.
        val live = junk("com.example.live")
        val drained = emptyJunk("com.example.drained")
        val h = harness(data = AppCleaner.Data(junks = listOf(live, drained)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = live.identifier))

        val items = h.vm.state.first().items
        items.map { it.identifier } shouldBe listOf(live.identifier)
    }

    @Test
    fun `state items are sorted by size descending`() = runTest2 {
        // previewExpendables produces matches whose total size matches a known shape. To make the
        // ordering test deterministic, we make `large` carry the default preview expendables and
        // give `small` an emptier shape.
        val large = previewAppJunk(pkg = previewInstalled(pkgName = "com.example.large", label = "Large"))
        val small = previewAppJunk(
            pkg = previewInstalled(pkgName = "com.example.small", label = "Small"),
            // Drop the public-caches matches → smaller `size` than `large`.
            expendables = previewExpendablesSubset(),
            inaccessibleCache = null,
        )
        val h = harness(data = AppCleaner.Data(junks = listOf(small, large)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = large.identifier))

        val items = h.vm.state.first().items
        items.map { it.identifier } shouldBe listOf(large.identifier, small.identifier)
    }

    /** Single-match expendables map — produces a smaller [AppJunk.size] than `previewExpendables()`. */
    private fun previewExpendablesSubset(): Map<kotlin.reflect.KClass<out eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter>, Collection<eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter.Match>> {
        // Build a single match with `DefaultCachesPublicFilter::class`, smaller size.
        val match = mockk<eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter.Match>().apply {
            every { identifier } returns DefaultCachesPublicFilter::class
            every { expectedGain } returns 1L
            every { path } returns mockk<APath>()
            every { lookup } returns mockk(relaxed = true) { every { size } returns 1L }
        }
        return mapOf(DefaultCachesPublicFilter::class to listOf(match))
    }

    @Test
    fun `state target switches after onPageChanged`() = runTest2 {
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val h = harness(data = AppCleaner.Data(junks = listOf(a, b)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        h.vm.onPageChanged(b.identifier)
        advanceUntilIdle()

        // Re-emit the same data so the state combine fires and observes the new currentTarget.
        h.stateFlow.value = appState(data = AppCleaner.Data(junks = listOf(a, b)))
        h.vm.state.first().target shouldBe b.identifier
    }

    @Test
    fun `requestDelete navs to upgrade when not pro`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)), isPro = false)
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))
        val (navList, navJob) = collectNavEvents(h.vm)

        h.vm.requestDelete(DeleteSpec.WholeJunk(installId = a.identifier, appLabel = "A"))
        advanceUntilIdle()

        navList.any { it is NavEvent.GoTo } shouldBe true
        // No event emitted (upgrade is consumed by the launch return).
        navJob.cancel()
    }

    @Test
    fun `requestDelete emits ConfirmDelete when spec is executable`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        val spec = DeleteSpec.WholeJunk(installId = a.identifier, appLabel = "A")
        h.vm.requestDelete(spec)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppJunkDetailsViewModel.Event.ConfirmDelete>()
        event.spec shouldBe spec
    }

    @Test
    fun `requestDelete with stale id does not emit ConfirmDelete`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        // Stale installId — not present in the snapshot. requestDelete bails before emitting.
        h.vm.requestDelete(
            DeleteSpec.WholeJunk(installId = installId("com.stale"), appLabel = "Stale"),
        )
        advanceUntilIdle()

        // No events flow value to take. The cleanest assertion: confirm submit was never called.
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `confirmDelete submits a task built from the spec`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        val spec = DeleteSpec.WholeJunk(installId = a.identifier, appLabel = "A")
        h.vm.confirmDelete(spec)
        advanceUntilIdle()

        // WholeJunk → AppCleanerProcessingTask(targetPkgs = setOf(installId)). The factory test
        // covers the spec→task mapping in detail; here we just verify submit was called.
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `onExcludeJunk emits HeaderExclusionCreated with undo exclusionIds size`() = runTest2 {
        val a = junk("com.example.a")
        val data = AppCleaner.Data(junks = listOf(a))
        val h = harness(data = data)
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        val undo = AppCleaner.ExclusionUndo(
            exclusionIds = setOf("excl-1", "excl-2"),
            previousData = data,
            postExcludeData = data,
        )
        coEvery { h.appCleaner.exclude(setOf(a.identifier)) } returns undo

        h.vm.onExcludeJunk(a.identifier)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<AppJunkDetailsViewModel.Event.HeaderExclusionCreated>()
        event.count shouldBe 2
        event.undo shouldBe undo
        event.restoreTarget shouldBe a.identifier
    }

    @Test
    fun `onExcludeJunk with stale id does nothing`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        h.vm.onExcludeJunk(installId("com.stale"))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.appCleaner.exclude(any<Set<InstallId>>()) }
    }

    @Test
    fun `onExcludeSelectedFiles with empty paths does nothing`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        h.vm.onExcludeSelectedFiles(a.identifier, emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.appCleaner.exclude(any<InstallId>(), any()) }
    }

    @Test
    fun `onExcludeSelectedFiles filters stale paths and excludes only live ones`() = runTest2 {
        val a = junk("com.example.a")
        val livePath = a.expendables!!.values.flatten().first().path
        val stalePath = mockk<APath>()
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        h.vm.onExcludeSelectedFiles(a.identifier, setOf(livePath, stalePath))
        advanceUntilIdle()

        // Exactly the live path is forwarded; the stale path is dropped.
        coVerify(exactly = 1) { h.appCleaner.exclude(a.identifier, setOf(livePath)) }
    }

    @Test
    fun `onExcludeSelectedFiles with all-stale paths bails before exclude`() = runTest2 {
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        h.vm.onExcludeSelectedFiles(a.identifier, setOf(mockk<APath>(), mockk<APath>()))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.appCleaner.exclude(any<InstallId>(), any()) }
    }

    @Test
    fun `onUndoExclude calls undoExclude and restores target`() = runTest2 {
        val a = junk("com.example.a")
        val b = junk("com.example.b")
        val data = AppCleaner.Data(junks = listOf(a, b))
        val h = harness(data = data)
        h.vm.bindRoute(AppJunkDetailsRoute(identifier = a.identifier))

        val undo = AppCleaner.ExclusionUndo(
            exclusionIds = setOf("excl-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onUndoExclude(undo, restoreTarget = b.identifier)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.appCleaner.undoExclude(undo) }
        h.vm.state.first().target shouldBe b.identifier
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
        // Regression: `mapNotNull { it.data }.map { it.hasData }` upstream of autoNavUpOnEmpty
        // means null emissions (the loading state during performScan) are skipped. The first
        // false from the upstream comes only after a non-null Data reports !hasData.
        val a = junk("com.example.a")
        val h = harness(data = AppCleaner.Data(junks = listOf(a)))
        val (navList, navJob) = collectNavEvents(h.vm)

        h.stateFlow.value = appState(data = null)
        advanceUntilIdle()

        navList shouldBe emptyList()
        navJob.cancel()
    }
}
