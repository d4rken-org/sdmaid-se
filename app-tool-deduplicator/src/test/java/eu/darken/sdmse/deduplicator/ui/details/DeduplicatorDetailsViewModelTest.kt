package eu.darken.sdmse.deduplicator.ui.details

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import eu.darken.sdmse.common.ViewIntentTool
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.details.cluster.DirectoryGroup
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.exclusion.ui.ExclusionsListRoute
import eu.darken.sdmse.main.core.SDMTool
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

class DeduplicatorDetailsViewModelTest : BaseTest() {

    private fun cluster(
        id: String,
        dupeSizes: List<Long> = listOf(100L, 100L),
        groupId: String = "$id-group",
    ): Duplicate.Cluster {
        val duplicates = dupeSizes.mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", id, "dup$idx"),
                size = size,
                hashSeed = "$id-$idx",
            )
        }.toSet()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id(groupId),
        )
        return previewCluster(
            identifier = Duplicate.Cluster.Id(id),
            groups = setOf(group),
        )
    }

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private fun upgradeInfo(isPro: Boolean): UpgradeRepo.Info = mockk<UpgradeRepo.Info>().apply {
        every { this@apply.isPro } returns isPro
    }

    private class Values(
        val isDirectoryViewEnabled: DataStoreValue<Boolean>,
        val allowDeleteAll: DataStoreValue<Boolean>,
    )

    private class Harness(
        val vm: DeduplicatorDetailsViewModel,
        val deduplicator: Deduplicator,
        val taskSubmitter: TaskSubmitter,
        val viewIntentTool: ViewIntentTool,
        val stateFlow: MutableStateFlow<Deduplicator.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
        val values: Values,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        private val job: Job,
    ) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectEvents(vm: DeduplicatorDetailsViewModel): CollectedEvents<DeduplicatorDetailsViewModel.Event> {
        val list = mutableListOf<DeduplicatorDetailsViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(vm: DeduplicatorDetailsViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        clusters: Set<Duplicate.Cluster>? = null,
        progress: Progress.Data? = null,
        isDirectoryViewEnabled: Boolean = false,
        allowDeleteAll: Boolean = false,
        isPro: Boolean = true,
        savedHandle: SavedStateHandle = SavedStateHandle(),
    ): Harness {
        val data = clusters?.let { Deduplicator.Data(clusters = it) }
        val stateFlow = MutableStateFlow(Deduplicator.State(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))

        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val values = Values(
            isDirectoryViewEnabled = rwDataStoreValue(isDirectoryViewEnabled),
            allowDeleteAll = rwDataStoreValue(allowDeleteAll),
        )
        val settings = mockk<DeduplicatorSettings>().apply {
            every { this@apply.isDirectoryViewEnabled } returns values.isDirectoryViewEnabled
            every { this@apply.allowDeleteAll } returns values.allowDeleteAll
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { state } returns taskStateFlow
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
            every { isSettled } returns flowOf(true)
        }
        val viewIntentTool = mockk<ViewIntentTool>(relaxed = true)
        val vm = DeduplicatorDetailsViewModel(
            handle = savedHandle,
            dispatcherProvider = TestDispatcherProvider(),
            deduplicator = deduplicator,
            taskSubmitter = taskSubmitter,
            settings = settings,
            upgradeRepo = upgradeRepo,
            viewIntentTool = viewIntentTool,
        )
        return Harness(vm, deduplicator, taskSubmitter, viewIntentTool, stateFlow, progressFlow, taskStateFlow, values)
    }

    // ─────────────────────────── route binding & state target ───────────────────────────

    @Test
    fun `bindRoute is idempotent — second call does not override the route`() = runTest2 {
        val a = cluster("a")
        val b = cluster("b")
        val h = harness(clusters = setOf(a, b))

        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = b.identifier))

        h.vm.state.filterNotNull().first().target shouldBe a.identifier
    }

    @Test
    fun `bindRoute is idempotent — second call does not reset currentTarget set via updatePage`() = runTest2 {
        // Pattern: user navigates to A, swipes to B (updatePage), config-change re-renders the
        // host which calls bindRoute again with the original route. The second bindRoute must
        // NOT reset currentTarget — the user's swipe position has to survive.
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(200L, 200L))
        val c = cluster("c", dupeSizes = listOf(50L, 50L))
        val h = harness(clusters = setOf(a, b, c))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first().target shouldBe a.identifier

        h.vm.updatePage(b.identifier)
        h.progressFlow.value = Progress.Data() // force a re-emit so currentTarget=b is observed
        h.vm.state.filterNotNull().first().target shouldBe b.identifier

        // Second bindRoute on the original route (a) must be a no-op — target stays on b.
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.progressFlow.value = null
        h.vm.state.filterNotNull().first().target shouldBe b.identifier
    }

    @Test
    fun `state items are sorted by averageSize descending`() = runTest2 {
        val small = cluster("small", dupeSizes = listOf(100L, 100L))
        val large = cluster("large", dupeSizes = listOf(10_000L, 10_000L))
        val medium = cluster("medium", dupeSizes = listOf(1_000L, 1_000L))
        val h = harness(clusters = setOf(small, large, medium))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = large.identifier))

        val items = h.vm.state.filterNotNull().first().items
        items.map { it.identifier.value } shouldBe listOf("large", "medium", "small")
    }

    @Test
    fun `intra-cluster deletion refreshes items - deleted duplicates disappear`() = runTest2 {
        // Regression guard: the items pipeline used to dedup on the SET OF CLUSTER IDS, so a
        // prune() after deleting duplicates inside a surviving cluster (same cluster ids, fewer
        // duplicates) was suppressed — the pager kept showing the deleted file with stale sizes
        // until a full rescan. The reference-dedup must let the new Data instance through.
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first().items.single().count shouldBe 3

        // Same cluster id, one duplicate pruned — exactly what prune() publishes after a
        // partial delete.
        val pruned = cluster("a", dupeSizes = listOf(100L, 100L))
        h.stateFlow.value = Deduplicator.State(data = Deduplicator.Data(clusters = setOf(pruned)), progress = null)

        h.vm.state.filterNotNull().first().items.single().count shouldBe 2
    }

    @Test
    fun `state target initially matches route identifier`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(200L, 200L))
        val h = harness(clusters = setOf(a, b))

        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.state.filterNotNull().first().target shouldBe a.identifier
    }

    @Test
    fun `state target switches after updatePage`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(200L, 200L))
        val h = harness(clusters = setOf(a, b))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.updatePage(b.identifier)
        advanceUntilIdle()

        // updatePage mutates currentTarget but doesn't republish — re-emit the same data so the
        // combine() recomputes target.
        h.stateFlow.value = Deduplicator.State(data = Deduplicator.Data(clusters = setOf(a, b)), progress = null)
        h.vm.state.filterNotNull().first().target shouldBe b.identifier
    }

    @Test
    fun `state target falls back via lastPosition when requested target is removed`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(200L, 200L))
        val c = cluster("c", dupeSizes = listOf(50L, 50L))
        val h = harness(clusters = setOf(a, b, c))

        // Sorted desc by averageSize: [b, a, c]. Bind to a → its index is 1 → lastPosition = 1.
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first().target shouldBe a.identifier

        // Remove `a`. Sorted desc: [b, c]. lastPosition=1 → falls back to index min(1, 1)=1 → c.
        h.stateFlow.value = Deduplicator.State(data = Deduplicator.Data(clusters = setOf(b, c)), progress = null)
        h.vm.state.filterNotNull().first().target shouldBe c.identifier
    }

    @Test
    fun `state threads progress, isDirectoryView and allowDeleteAll from inputs`() = runTest2 {
        val progress = Progress.Data()
        val a = cluster("a")
        val h = harness(
            clusters = setOf(a),
            progress = progress,
            isDirectoryViewEnabled = true,
            allowDeleteAll = true,
        )
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        val state = h.vm.state.filterNotNull().first()
        state.progress shouldBe progress
        state.isDirectoryView shouldBe true
        state.allowDeleteAll shouldBe true
    }

    // ─────────────────────────── directory view & collapse ───────────────────────────

    @Test
    fun `toggleDirectoryView writes the inverted value to settings`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a), isDirectoryViewEnabled = false)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first()

        h.vm.toggleDirectoryView()
        advanceUntilIdle()

        // The transform receives `false` (current) and returns `true`.
        coVerify(exactly = 1) {
            h.values.isDirectoryViewEnabled.update(match { it.invoke(false) == true })
        }
    }

    @Test
    fun `toggleDirectoryCollapse adds and then removes the dirId per cluster`() = runTest2 {
        val a = cluster("a")
        val b = cluster("b")
        val h = harness(clusters = setOf(a, b))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        val dir1 = DirectoryGroup.Id("dir1")
        val dir2 = DirectoryGroup.Id("dir2")

        // Toggle dir1 in cluster a → present.
        h.vm.toggleDirectoryCollapse(a.identifier, dir1)
        h.vm.state.filterNotNull().first().collapsedDirs[a.identifier] shouldBe setOf(dir1)

        // Toggle dir2 in cluster a → both present.
        h.vm.toggleDirectoryCollapse(a.identifier, dir2)
        h.vm.state.filterNotNull().first().collapsedDirs[a.identifier] shouldBe setOf(dir1, dir2)

        // Toggle dir1 in cluster a again → removed; dir2 still present.
        h.vm.toggleDirectoryCollapse(a.identifier, dir1)
        h.vm.state.filterNotNull().first().collapsedDirs[a.identifier] shouldBe setOf(dir2)

        // Toggling dir1 in cluster b should not affect cluster a's state.
        h.vm.toggleDirectoryCollapse(b.identifier, dir1)
        val state = h.vm.state.filterNotNull().first()
        state.collapsedDirs[a.identifier] shouldBe setOf(dir2)
        state.collapsedDirs[b.identifier] shouldBe setOf(dir1)
    }

    @Test
    fun `state prunes collapsedDirs entries for clusters that no longer exist`() = runTest2 {
        val a = cluster("a")
        val b = cluster("b")
        val h = harness(clusters = setOf(a, b))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        // Collapse a directory in both clusters.
        h.vm.toggleDirectoryCollapse(a.identifier, DirectoryGroup.Id("dir-a"))
        h.vm.toggleDirectoryCollapse(b.identifier, DirectoryGroup.Id("dir-b"))
        h.vm.state.filterNotNull().first().collapsedDirs.keys shouldBe setOf(a.identifier, b.identifier)

        // Remove cluster b. Its collapsed-dir entry must be pruned.
        h.stateFlow.value = Deduplicator.State(data = Deduplicator.Data(clusters = setOf(a)), progress = null)
        h.vm.state.filterNotNull().first().collapsedDirs.keys shouldBe setOf(a.identifier)
    }

    // ─────────────────────────── navigation actions ───────────────────────────

    @Test
    fun `previewCluster with empty cluster paths is a no-op`() = runTest2 {
        val emptyGroup = ChecksumDuplicate.Group(
            duplicates = emptySet(),
            identifier = Duplicate.Group.Id("empty"),
        )
        val degenerate = previewCluster(
            identifier = Duplicate.Cluster.Id("empty"),
            groups = setOf(emptyGroup),
        )
        val h = harness(clusters = setOf(cluster("a")))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = Duplicate.Cluster.Id("a")))
        val nav = collectNavEvents(h.vm)

        h.vm.previewCluster(degenerate)
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }

    @Test
    fun `previewCluster with paths navigates to PreviewRoute with all duplicate paths`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.previewCluster(a)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination.shouldBeInstanceOf<PreviewRoute>()
        route.options.paths shouldBe a.groups.flatMap { g -> g.duplicates.map { it.path } }
        nav.cancel()
    }

    @Test
    fun `previewGroup clamps an over-range position to lastIndex`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val group = a.groups.single()
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.previewGroup(group, position = 999)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination.shouldBeInstanceOf<PreviewRoute>()
        route.options.position shouldBe group.duplicates.size - 1
        nav.cancel()
    }

    @Test
    fun `previewGroup clamps a negative position to zero`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val group = a.groups.single()
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.previewGroup(group, position = -5)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination.shouldBeInstanceOf<PreviewRoute>()
        route.options.position shouldBe 0
        nav.cancel()
    }

    @Test
    fun `previewGroup with an empty group is a no-op`() = runTest2 {
        val emptyGroup = ChecksumDuplicate.Group(
            duplicates = emptySet(),
            identifier = Duplicate.Group.Id("empty"),
        )
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.previewGroup(emptyGroup)
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }

    @Test
    fun `previewDuplicate navigates to PreviewRoute with just that duplicate's path`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val dupe = a.groups.first().duplicates.first()
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.previewDuplicate(dupe)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        val route = event.destination.shouldBeInstanceOf<PreviewRoute>()
        route.options.paths shouldBe listOf(dupe.path)
        nav.cancel()
    }

    @Test
    fun `onShowExclusions navigates to ExclusionsListRoute exactly once`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.onShowExclusions()
        advanceUntilIdle()

        // single() instead of last() catches a regression that emits an extra nav event.
        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe ExclusionsListRoute
        nav.cancel()
    }

    // ─────────────────────────── openDuplicate ───────────────────────────

    @Test
    fun `openDuplicate emits OpenDuplicate event with the resolved intent`() = runTest2 {
        val a = cluster("a")
        val dupe = a.groups.first().duplicates.first()
        val h = harness(clusters = setOf(a))
        val intent = mockk<Intent>()
        coEvery { h.viewIntentTool.create(dupe.lookup) } returns intent
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.openDuplicate(dupe)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.OpenDuplicate>()
        event.intent shouldBe intent
    }

    @Test
    fun `openDuplicate suppresses the event when the intent tool returns null`() = runTest2 {
        val a = cluster("a")
        val dupe = a.groups.first().duplicates.first()
        val h = harness(clusters = setOf(a))
        coEvery { h.viewIntentTool.create(dupe.lookup) } returns null
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val events = collectEvents(h.vm)

        h.vm.openDuplicate(dupe)
        advanceUntilIdle()

        events.list shouldBe emptyList()
        events.cancel()
    }

    // ─────────────────────────── deleteCluster ───────────────────────────

    @Test
    fun `deleteCluster unconfirmed emits ConfirmDeletion with the cluster target and does NOT submit`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a), allowDeleteAll = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteCluster(a.identifier, confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.ConfirmDeletion>()
        event.clusterId shouldBe a.identifier
        val target = event.target.shouldBeInstanceOf<DeduplicatorDetailsViewModel.DeleteTarget.ClusterTarget>()
        target.id shouldBe a.identifier
        val mode = event.mode.shouldBeInstanceOf<PreviewDeletionMode.Clusters>()
        mode.allowDeleteAll shouldBe true
        mode.clusters.map { it.identifier } shouldBe listOf(a.identifier)
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteCluster with stale id is a no-op`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val events = collectEvents(h.vm)

        h.vm.deleteCluster(Duplicate.Cluster.Id("does-not-exist"), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `deleteCluster confirmed without pro navigates to UpgradeRoute and does NOT submit`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a), isPro = false)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.deleteCluster(a.identifier, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe UpgradeRoute()
        nav.cancel()
    }

    @Test
    fun `deleteCluster confirmed with pro submits TargetMode_Clusters`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a), isPro = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteCluster(a.identifier, confirmed = true, deleteAll = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Clusters(
                        targets = setOf(a.identifier),
                        deleteAll = true,
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── deleteGroup ───────────────────────────

    @Test
    fun `deleteGroup unconfirmed emits ConfirmDeletion with GroupTarget and does NOT submit`() = runTest2 {
        val a = cluster("a")
        val groupId = a.groups.first().identifier
        val h = harness(clusters = setOf(a), allowDeleteAll = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteGroup(a.identifier, groupId, confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.ConfirmDeletion>()
        event.clusterId shouldBe a.identifier
        val target = event.target.shouldBeInstanceOf<DeduplicatorDetailsViewModel.DeleteTarget.GroupTarget>()
        target.id shouldBe groupId
        val mode = event.mode.shouldBeInstanceOf<PreviewDeletionMode.Groups>()
        mode.allowDeleteAll shouldBe true
        mode.groups.map { it.identifier } shouldBe listOf(groupId)
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteGroup confirmed without pro navigates to UpgradeRoute and does NOT submit`() = runTest2 {
        val a = cluster("a")
        val groupId = a.groups.first().identifier
        val h = harness(clusters = setOf(a), isPro = false)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.deleteGroup(a.identifier, groupId, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe UpgradeRoute()
        nav.cancel()
    }

    @Test
    fun `deleteGroup with stale group id is a no-op`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteGroup(a.identifier, Duplicate.Group.Id("does-not-exist"), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteGroup confirmed with pro submits TargetMode_Groups`() = runTest2 {
        val a = cluster("a")
        val groupId = a.groups.first().identifier
        val h = harness(clusters = setOf(a), isPro = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteGroup(a.identifier, groupId, confirmed = true, deleteAll = false)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Groups(
                        targets = setOf(groupId),
                        deleteAll = false,
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── deleteDuplicates ───────────────────────────

    @Test
    fun `deleteDuplicates unconfirmed emits ConfirmDeletion with DuplicateTargets and does NOT submit`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val dupes = a.groups.first().duplicates.toList().take(2)
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteDuplicates(a.identifier, ids = dupes.map { it.identifier }, confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.ConfirmDeletion>()
        event.clusterId shouldBe a.identifier
        val target = event.target.shouldBeInstanceOf<DeduplicatorDetailsViewModel.DeleteTarget.DuplicateTargets>()
        target.ids shouldBe dupes.map { it.identifier }.toSet()
        val mode = event.mode.shouldBeInstanceOf<PreviewDeletionMode.Duplicates>()
        mode.duplicates.map { it.identifier } shouldBe dupes.map { it.identifier }
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteDuplicates confirmed without pro navigates to UpgradeRoute and does NOT submit`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val dupe = a.groups.first().duplicates.first()
        val h = harness(clusters = setOf(a), isPro = false)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)

        h.vm.deleteDuplicates(a.identifier, ids = setOf(dupe.identifier), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe UpgradeRoute()
        nav.cancel()
    }

    @Test
    fun `deleteDuplicates filters stale ids AND duplicate ids belonging to other clusters`() = runTest2 {
        // Production scopes the live-duplicate lookup to the named cluster only. A regression
        // that searched ALL clusters' duplicates would silently include the cross-cluster id.
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val b = cluster("b", dupeSizes = listOf(50L, 50L))
        val aLive = a.groups.first().duplicates.toList().take(2)
        val bDupe = b.groups.first().duplicates.first()
        val staleId = Duplicate.Id("not-in-any-cluster")
        val h = harness(clusters = setOf(a, b), isPro = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteDuplicates(
            a.identifier,
            ids = aLive.map { it.identifier } + bDupe.identifier + staleId,
            confirmed = true,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Duplicates(
                        targets = aLive.map { it.identifier }.toSet(),
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteDuplicates with all-stale ids is a no-op`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a), isPro = true)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.deleteDuplicates(a.identifier, ids = setOf(Duplicate.Id("stale")), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── exclusions ───────────────────────────

    @Test
    fun `excludeCluster emits ExclusionsCreated with the saved-exclusion count and restoreTarget`() = runTest2 {
        val a = cluster("a")
        val undo = Deduplicator.ExclusionUndo(
            exclusionIds = setOf<ExclusionId>("excl-1", "excl-2"),
            previousData = Deduplicator.Data(clusters = setOf(a)),
            postExcludeData = Deduplicator.Data(clusters = emptySet()),
        )
        val h = harness(clusters = setOf(a))
        coEvery { h.deduplicator.exclude(setOf(a.identifier)) } returns undo
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.excludeCluster(a.identifier)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 2
        event.undo shouldBe undo
        event.restoreTarget shouldBe a.identifier
    }

    @Test
    fun `excludeCluster with stale id is a no-op`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val events = collectEvents(h.vm)

        h.vm.excludeCluster(Duplicate.Cluster.Id("stale"))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.deduplicator.exclude(any<Set<Duplicate.Cluster.Id>>()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `excludeDuplicates calls the per-cluster exclude with live paths only`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val live = a.groups.first().duplicates.toList().take(2)
        val staleId = Duplicate.Id("stale")
        val h = harness(clusters = setOf(a))
        coEvery { h.deduplicator.exclude(any<Duplicate.Cluster.Id>(), any()) } returns Unit
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))

        h.vm.excludeDuplicates(a.identifier, ids = live.map { it.identifier } + staleId)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.deduplicator.exclude(a.identifier, live.map { it.path }) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.SelectionExclusionsCreated>()
        event.count shouldBe 2
    }

    @Test
    fun `excludeDuplicates with all-stale ids does not call exclude or emit`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val events = collectEvents(h.vm)

        h.vm.excludeDuplicates(a.identifier, ids = setOf(Duplicate.Id("stale")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.deduplicator.exclude(any<Duplicate.Cluster.Id>(), any()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `onUndoExclude restores target and calls undoExclude`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(200L, 200L))
        val data = Deduplicator.Data(clusters = setOf(a, b))
        val h = harness(clusters = setOf(a, b))
        // Bind to `a` — sorted desc by size is [b, a] but bindRoute identifier wins because
        // `a` exists in the list.
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first().target shouldBe a.identifier

        val undo = Deduplicator.ExclusionUndo(
            exclusionIds = setOf<ExclusionId>("excl-1"),
            previousData = data,
            postExcludeData = data,
        )

        h.vm.onUndoExclude(undo, restoreTarget = b.identifier)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.deduplicator.undoExclude(undo) }

        // currentTarget was set to b inside the launched coroutine, but the state's combine()
        // only re-runs when one of its inputs emits. The mock doesn't actually persist anything,
        // and the deduplicator.state input dedups by Data reference, so re-pushing the same
        // instance would be dropped. Force a re-emit through `deduplicator.progress` (a different
        // combine input) so the next state value reflects the restored target. Progress changes
        // don't leave residual UI state behind.
        h.progressFlow.value = Progress.Data()
        h.vm.state.filterNotNull().first().target shouldBe b.identifier
    }

    // ─────────────────────────── auto-navUp on drain ───────────────────────────

    @Test
    fun `init navigates up when data drains from non-empty to empty`() = runTest2 {
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first()

        h.stateFlow.value = Deduplicator.State(
            data = Deduplicator.Data(clusters = emptySet()),
            progress = null,
        )
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init does NOT navigate up when data transitions from non-empty to null`() = runTest2 {
        // The autoNavUpOnEmpty flow uses `mapNotNull { it.data }` to skip the loading-state
        // null transition that performScan publishes at the start of a refresh.
        val a = cluster("a")
        val h = harness(clusters = setOf(a))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        val nav = collectNavEvents(h.vm)
        h.vm.state.filterNotNull().first()

        h.stateFlow.value = Deduplicator.State(data = null, progress = Progress.Data())
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }

    // ─────────────────────────── task result dispatch ───────────────────────────

    @Test
    fun `init does not emit TaskResult for tasks completed before VM init`() = runTest2 {
        val staleResult = DeduplicatorDeleteTask.Success(affectedSpace = 0L, affectedPaths = emptySet())
        val staleTask = TaskSubmitter.ManagedTask(
            id = "stale",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = DeduplicatorDeleteTask(),
            completedAt = Instant.now().minusSeconds(60),
            result = staleResult,
        )
        val h = harness(clusters = setOf(cluster("a")))
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(staleTask))
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = Duplicate.Cluster.Id("a")))
        advanceUntilIdle()

        // Now post a fresh task; only this one should fire as Event.TaskResult.
        val freshResult = DeduplicatorDeleteTask.Success(affectedSpace = 100L, affectedPaths = emptySet())
        val freshTask = TaskSubmitter.ManagedTask(
            id = "fresh",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = DeduplicatorDeleteTask(),
            completedAt = Instant.now().plusSeconds(60),
            result = freshResult,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(staleTask, freshTask))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.TaskResult>()
        event.result shouldBe freshResult
    }

    @Test
    fun `init does NOT re-emit TaskResult for the same task id observed twice`() = runTest2 {
        // The `uniqueTaskResults` helper dedups by task.id. Re-emitting the same ManagedTask
        // (e.g. because TaskSubmitter.State was rebuilt with the same task) must not fire a
        // second event.
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val dupes = a.groups.first().duplicates.toList()
        val h = harness(clusters = setOf(a))
        val events = collectEvents(h.vm)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first()

        val result = DeduplicatorDeleteTask.Success(affectedSpace = 100L, affectedPaths = emptySet())
        val task = TaskSubmitter.ManagedTask(
            id = "task-1",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = DeduplicatorDeleteTask(
                mode = DeduplicatorDeleteTask.TargetMode.Duplicates(
                    targets = setOf(dupes[0].identifier, dupes[1].identifier),
                ),
            ),
            completedAt = Instant.now().plusSeconds(60),
            result = result,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task))
        advanceUntilIdle()
        events.list.size shouldBe 1

        // Re-emit the SAME task id. Should NOT fire a second TaskResult event.
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task.copy(notifyOnFinish = false)))
        advanceUntilIdle()
        events.list.size shouldBe 1

        // A fresh task id should still get through, proving the collector is alive.
        val task2 = task.copy(
            id = "task-2",
            completedAt = Instant.now().plusSeconds(120),
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(task, task2))
        advanceUntilIdle()
        events.list.size shouldBe 2
        events.cancel()
    }

    @Test
    fun `init suppresses TaskResult for single-duplicate delete tasks but forwards multi-target results`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val dupes = a.groups.first().duplicates.toList()
        val singleDupeId = dupes[0].identifier
        val h = harness(clusters = setOf(a))
        val events = collectEvents(h.vm)
        h.vm.bindRoute(DeduplicatorDetailsRoute(identifier = a.identifier))
        h.vm.state.filterNotNull().first()

        val singleTask = TaskSubmitter.ManagedTask(
            id = "single",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = DeduplicatorDeleteTask(
                mode = DeduplicatorDeleteTask.TargetMode.Duplicates(targets = setOf(singleDupeId)),
            ),
            completedAt = Instant.now().plusSeconds(60),
            result = DeduplicatorDeleteTask.Success(affectedSpace = 0L, affectedPaths = emptySet()),
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(singleTask))
        advanceUntilIdle()
        events.list shouldBe emptyList()

        val multiResult = DeduplicatorDeleteTask.Success(affectedSpace = 100L, affectedPaths = emptySet())
        val multiTask = TaskSubmitter.ManagedTask(
            id = "multi",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = DeduplicatorDeleteTask(
                mode = DeduplicatorDeleteTask.TargetMode.Duplicates(
                    targets = setOf(dupes[0].identifier, dupes[1].identifier),
                ),
            ),
            completedAt = Instant.now().plusSeconds(120),
            result = multiResult,
        )
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(singleTask, multiTask))
        advanceUntilIdle()

        events.list.size shouldBe 1
        val event = events.list.single()
        event.shouldBeInstanceOf<DeduplicatorDetailsViewModel.Event.TaskResult>()
        event.result shouldBe multiResult
        events.cancel()
    }
}
