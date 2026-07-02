package eu.darken.sdmse.deduplicator.ui.list

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.previews.PreviewRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.ui.LayoutMode
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.deduplicator.core.DeduplicatorSettings
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.core.scanner.checksum.ChecksumDuplicate
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorDeleteTask
import eu.darken.sdmse.deduplicator.ui.DeduplicatorDetailsRoute
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumDuplicate
import eu.darken.sdmse.deduplicator.ui.preview.previewChecksumGroup
import eu.darken.sdmse.deduplicator.ui.preview.previewCluster
import eu.darken.sdmse.exclusion.core.types.ExclusionId
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

class DeduplicatorListViewModelTest : BaseTest() {

    private fun cluster(
        id: String,
        dupeSizes: List<Long> = listOf(100L, 100L),
        groupId: String = "$id-group",
        keeperOf: String? = null,
        favoriteOf: String? = null,
    ): Duplicate.Cluster {
        val duplicates = dupeSizes.mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", id, "dup$idx"),
                size = size,
                hashSeed = "$id-$idx",
            )
        }.toSet()
        val keeper = duplicates.firstOrNull { it.lookup.path.endsWith(keeperOf ?: "") && keeperOf != null }
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id(groupId),
            keeperIdentifier = keeper?.identifier,
        )
        return previewCluster(
            identifier = Duplicate.Cluster.Id(id),
            groups = setOf(group),
            favoriteGroupIdentifier = if (favoriteOf == "self") group.identifier else null,
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
        val layoutMode: DataStoreValue<LayoutMode>,
        val allowDeleteAll: DataStoreValue<Boolean>,
    )

    private class Harness(
        val vm: DeduplicatorListViewModel,
        val deduplicator: Deduplicator,
        val settings: DeduplicatorSettings,
        val taskSubmitter: TaskSubmitter,
        val stateFlow: MutableStateFlow<Deduplicator.State>,
        val progressFlow: MutableStateFlow<Progress.Data?>,
        val taskStateFlow: MutableStateFlow<TaskSubmitter.State>,
        val layoutModeFlow: MutableStateFlow<LayoutMode>,
        val allowDeleteAllFlow: MutableStateFlow<Boolean>,
        val values: Values,
    )

    private class CollectedEvents<T>(
        val list: MutableList<T>,
        private val job: Job,
    ) {
        fun cancel() = job.cancel()
    }

    private fun CoroutineScope.collectEvents(vm: DeduplicatorListViewModel): CollectedEvents<DeduplicatorListViewModel.Event> {
        val list = mutableListOf<DeduplicatorListViewModel.Event>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.events.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun CoroutineScope.collectNavEvents(vm: DeduplicatorListViewModel): CollectedEvents<NavEvent> {
        val list = mutableListOf<NavEvent>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.navEvents.collect { list.add(it) }
        }
        return CollectedEvents(list, job)
    }

    private fun harness(
        clusters: Set<Duplicate.Cluster>? = null,
        progress: Progress.Data? = null,
        layoutMode: LayoutMode = LayoutMode.GRID,
        allowDeleteAll: Boolean = false,
        isPro: Boolean = true,
    ): Harness {
        val data = clusters?.let { Deduplicator.Data(clusters = it) }
        val stateFlow = MutableStateFlow(Deduplicator.State(data = data, progress = progress))
        val progressFlow = MutableStateFlow(progress)
        val taskStateFlow = MutableStateFlow(TaskSubmitter.State(tasks = emptySet()))
        val layoutModeFlow = MutableStateFlow(layoutMode)
        val allowDeleteAllFlow = MutableStateFlow(allowDeleteAll)

        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { this@apply.state } returns stateFlow
            every { this@apply.progress } returns progressFlow
        }
        val values = Values(
            // NOTE: `update` is a stub that does NOT propagate back into layoutModeFlow. If a
            // test wires the update to actually mutate the flow, the combine() in state re-emits
            // and the stateIn collector stays hot, which makes runTest2 flag uncompleted
            // coroutines. For toggleLayoutMode we capture the transform lambda directly.
            layoutMode = rwDataStoreValue(layoutMode, layoutModeFlow),
            allowDeleteAll = rwDataStoreValue(allowDeleteAll, allowDeleteAllFlow),
        )
        val settings = mockk<DeduplicatorSettings>().apply {
            every { this@apply.layoutMode } returns values.layoutMode
            every { this@apply.allowDeleteAll } returns values.allowDeleteAll
        }
        val taskSubmitter = mockk<TaskSubmitter>(relaxed = true).apply {
            every { state } returns taskStateFlow
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(upgradeInfo(isPro = isPro))
            every { isSettled } returns flowOf(true)
        }
        val vm = DeduplicatorListViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            deduplicator = deduplicator,
            settings = settings,
            taskSubmitter = taskSubmitter,
            upgradeRepo = upgradeRepo,
        )
        return Harness(
            vm = vm,
            deduplicator = deduplicator,
            settings = settings,
            taskSubmitter = taskSubmitter,
            stateFlow = stateFlow,
            progressFlow = progressFlow,
            taskStateFlow = taskStateFlow,
            layoutModeFlow = layoutModeFlow,
            allowDeleteAllFlow = allowDeleteAllFlow,
            values = values,
        )
    }

    // ─────────────────────────── state composition ───────────────────────────

    @Test
    fun `state stays null when Deduplicator data is null`() = runTest2 {
        val h = harness(clusters = null)
        // combine() requires data != null (filterNotNull). With no data, the StateFlow stays at
        // its safeStateIn initial value (null).
        h.vm.state.first() shouldBe null
    }

    @Test
    fun `state rows is empty when clusters is empty`() = runTest2 {
        val h = harness(clusters = emptySet())
        val state = h.vm.state.filterNotNull().first()
        state.rows shouldBe emptyList()
    }

    @Test
    fun `state rows are sorted by cluster averageSize descending`() = runTest2 {
        val small = cluster("small", dupeSizes = listOf(100L, 100L))
        val large = cluster("large", dupeSizes = listOf(10_000L, 10_000L))
        val medium = cluster("medium", dupeSizes = listOf(1_000L, 1_000L))
        val h = harness(clusters = setOf(small, large, medium))

        val state = h.vm.state.filterNotNull().first()
        state.rows.map { it.cluster.identifier.value } shouldBe listOf("large", "medium", "small")
    }

    @Test
    fun `state threads progress, layoutMode and allowDeleteAll from inputs`() = runTest2 {
        val progress = Progress.Data()
        val c = cluster("a")
        val h = harness(
            clusters = setOf(c),
            progress = progress,
            layoutMode = LayoutMode.LINEAR,
            allowDeleteAll = true,
        )

        val state = h.vm.state.filterNotNull().first()
        state.progress shouldBe progress
        state.layoutMode shouldBe LayoutMode.LINEAR
        state.allowDeleteAll shouldBe true
    }

    @Test
    fun `state deleteTargetIds includes ALL duplicates when group has no keeper and is not favorite`() = runTest2 {
        // No keeper + not the favorite group → every duplicate is a delete target.
        val c = cluster("c", dupeSizes = listOf(100L, 100L, 100L))
        val h = harness(clusters = setOf(c))

        val state = h.vm.state.filterNotNull().first()
        val row = state.rows.single()
        row.deleteTargetIds.size shouldBe 3
        row.deleteTargetIds shouldBe c.groups.flatMap { it.duplicates }.map { it.identifier }.toSet()
    }

    @Test
    fun `state deleteTargetIds excludes ONLY the keeper for the favorite group`() = runTest2 {
        // Pattern: when the cluster's favorite group has a keeper, the keeper is preserved and
        // every other duplicate in that group is a target. A regression that excluded the
        // *whole* favorite group from the target set would flunk this — there's still data to
        // delete.
        val duplicates = listOf(100L, 100L, 100L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "c", "dup$idx"),
                size = size,
                hashSeed = "c-$idx",
            )
        }.toSet()
        val keeper = duplicates.first()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("c-group"),
            keeperIdentifier = keeper.identifier,
        )
        val c = previewCluster(
            identifier = Duplicate.Cluster.Id("c"),
            groups = setOf(group),
            favoriteGroupIdentifier = group.identifier,
        )
        val h = harness(clusters = setOf(c))

        val state = h.vm.state.filterNotNull().first()
        val row = state.rows.single()
        row.deleteTargetIds shouldBe (duplicates - keeper).map { it.identifier }.toSet()
    }

    @Test
    fun `state freeableSize sums only the delete-target duplicates, not the whole cluster`() = runTest2 {
        // The favorite group keeps its first copy (size 100); freeableSize must equal the two
        // non-keeper copies (200 + 300), NOT the raw cluster total. This delete-target model backs
        // the grid "X freeable" line and the strengthened cluster-delete dialog.
        val duplicates = listOf(100L, 200L, 300L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "f", "dup$idx"),
                size = size,
                hashSeed = "f-$idx",
            )
        }.toSet()
        val keeper = duplicates.first()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("f-group"),
            keeperIdentifier = keeper.identifier,
        )
        val c = previewCluster(
            identifier = Duplicate.Cluster.Id("f"),
            groups = setOf(group),
            favoriteGroupIdentifier = group.identifier,
        )
        val h = harness(clusters = setOf(c))

        val row = h.vm.state.filterNotNull().first().rows.single()
        row.deleteTargetIds shouldBe (duplicates - keeper).map { it.identifier }.toSet()
        row.freeableSize shouldBe 500L
    }

    @Test
    fun `state freeableSize is zero when the favorite group has no keeper metadata`() = runTest2 {
        // Codex review #5: redundantSize could over-report when keeper metadata is missing. The
        // delete-target model instead yields NO targets for a keeper-less favorite group, so a
        // single-group cluster reports 0 freeable rather than the raw cluster size.
        val duplicates = listOf(100L, 200L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "g", "dup$idx"),
                size = size,
                hashSeed = "g-$idx",
            )
        }.toSet()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("g-group"),
            keeperIdentifier = null,
        )
        val c = previewCluster(
            identifier = Duplicate.Cluster.Id("g"),
            groups = setOf(group),
            favoriteGroupIdentifier = group.identifier,
        )
        val h = harness(clusters = setOf(c))

        val row = h.vm.state.filterNotNull().first().rows.single()
        row.deleteTargetIds.size shouldBe 0
        row.freeableSize shouldBe 0L
    }

    @Test
    fun `state deleteTargetIds includes all duplicates of a non-favorite group EVEN if that group has a keeper`() = runTest2 {
        // Per the production logic, the keeper-preservation branch ONLY applies to the favorite
        // group. A keeper set on a non-favorite group is irrelevant to deleteTargetIds — the
        // whole non-favorite group is targeted. A regression that respected keeperIdentifier
        // for non-favorite groups would shrink this set.
        val favoriteGroupDupes = listOf(100L, 100L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "c", "fav$idx"),
                size = size,
                hashSeed = "fav-$idx",
            )
        }.toSet()
        val favoriteGroup = ChecksumDuplicate.Group(
            duplicates = favoriteGroupDupes,
            identifier = Duplicate.Group.Id("fav"),
            keeperIdentifier = favoriteGroupDupes.first().identifier,
        )
        val secondaryGroupDupes = listOf(100L, 100L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "c", "sec$idx"),
                size = size,
                hashSeed = "sec-$idx",
            )
        }.toSet()
        val secondaryGroup = ChecksumDuplicate.Group(
            duplicates = secondaryGroupDupes,
            identifier = Duplicate.Group.Id("sec"),
            keeperIdentifier = secondaryGroupDupes.first().identifier, // keeper set, but irrelevant
        )
        val c = previewCluster(
            identifier = Duplicate.Cluster.Id("c"),
            groups = setOf(favoriteGroup, secondaryGroup),
            favoriteGroupIdentifier = favoriteGroup.identifier,
        )
        val h = harness(clusters = setOf(c))

        val state = h.vm.state.filterNotNull().first()
        val row = state.rows.single()
        // Favorite group: keeper is preserved → 1 dupe targeted.
        // Secondary group: ALL dupes targeted (keeper preservation does NOT apply here).
        row.deleteTargetIds shouldBe
            (favoriteGroupDupes - favoriteGroupDupes.first()).map { it.identifier }.toSet() +
            secondaryGroupDupes.map { it.identifier }.toSet()
        row.deleteTargetIds.size shouldBe 3
    }

    @Test
    fun `state deleteTargetIds is empty for favorite group with no keeper`() = runTest2 {
        // Favorite group + no keeper means nothing to delete in that group. A regression that
        // fell back to "delete everything" here would risk deleting the last copy.
        val duplicates = listOf(100L, 100L).mapIndexed { idx, size ->
            previewChecksumDuplicate(
                pathSegments = arrayOf("storage", "c", "dup$idx"),
                size = size,
                hashSeed = "c-$idx",
            )
        }.toSet()
        val group = ChecksumDuplicate.Group(
            duplicates = duplicates,
            identifier = Duplicate.Group.Id("c-group"),
            keeperIdentifier = null,
        )
        val c = previewCluster(
            identifier = Duplicate.Cluster.Id("c"),
            groups = setOf(group),
            favoriteGroupIdentifier = group.identifier,
        )
        val h = harness(clusters = setOf(c))

        val state = h.vm.state.filterNotNull().first()
        state.rows.single().deleteTargetIds shouldBe emptySet()
    }

    // ─────────────────────────── navUp on drain ───────────────────────────

    @Test
    fun `init navigates up when data drains from non-empty to empty`() = runTest2 {
        val c = cluster("a")
        val h = harness(clusters = setOf(c))
        h.vm.state.filterNotNull().first() // prime init flow

        h.stateFlow.value = Deduplicator.State(
            data = Deduplicator.Data(clusters = emptySet()),
            progress = null,
        )
        advanceUntilIdle()

        h.vm.navEvents.first() shouldBe NavEvent.Up
    }

    @Test
    fun `init fires navUp exactly once on the non-empty to null to empty refresh-drain path`() = runTest2 {
        // The full refresh sequence: non-empty (initial) → null (performScan starts) → empty
        // (scan finished, nothing left). The drop(1) consumes the initial non-empty, mapNotNull
        // skips the null, and the empty emission fires navUp. take(1) ensures it fires once.
        val c = cluster("a")
        val h = harness(clusters = setOf(c))
        val nav = collectNavEvents(h.vm)
        h.vm.state.filterNotNull().first()

        h.stateFlow.value = Deduplicator.State(data = null, progress = Progress.Data())
        advanceUntilIdle()
        h.stateFlow.value = Deduplicator.State(data = Deduplicator.Data(clusters = emptySet()), progress = null)
        advanceUntilIdle()

        nav.list shouldBe listOf(NavEvent.Up)
        nav.cancel()
    }

    @Test
    fun `init does NOT navigate up when data transitions from non-empty to null`() = runTest2 {
        // Regression: data going to `null` indicates a fresh scan started (Deduplicator sets
        // internalData = null at the top of performScan). That's a loading state, not an
        // "everything was excluded" state — must not navigate the user away mid-scan. The fix
        // mirrors the CorpseFinder pattern: `mapNotNull { it.data }` before drop(1) skips null
        // transitions so only "non-empty → empty" triggers navUp.
        val c = cluster("a")
        val h = harness(clusters = setOf(c))
        val nav = collectNavEvents(h.vm)
        h.vm.state.filterNotNull().first()

        h.stateFlow.value = Deduplicator.State(data = null, progress = Progress.Data())
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }

    // ─────────────────────────── navigation actions ───────────────────────────

    @Test
    fun `showDetails navigates to DeduplicatorDetailsRoute`() = runTest2 {
        val h = harness(clusters = setOf(cluster("a")))
        val nav = collectNavEvents(h.vm)

        h.vm.showDetails(Duplicate.Cluster.Id("a"))
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe DeduplicatorDetailsRoute(identifier = Duplicate.Cluster.Id("a"))
        nav.cancel()
    }

    @Test
    fun `previewCluster with empty cluster paths is a no-op`() = runTest2 {
        // Construct a degenerate cluster with no duplicates in its groups (legal at the type
        // level even if production never produces one). VM must NOT emit a PreviewRoute for it.
        val emptyGroup = ChecksumDuplicate.Group(
            duplicates = emptySet(),
            identifier = Duplicate.Group.Id("empty"),
            keeperIdentifier = null,
        )
        val degenerate = previewCluster(
            identifier = Duplicate.Cluster.Id("empty"),
            groups = setOf(emptyGroup),
        )
        val h = harness(clusters = setOf(cluster("normal")))
        val nav = collectNavEvents(h.vm)

        h.vm.previewCluster(degenerate)
        advanceUntilIdle()

        nav.list shouldBe emptyList()
        nav.cancel()
    }

    @Test
    fun `previewCluster with non-empty paths navigates to PreviewRoute`() = runTest2 {
        val c = cluster("c")
        val h = harness(clusters = setOf(c))
        val nav = collectNavEvents(h.vm)

        h.vm.previewCluster(c)
        advanceUntilIdle()

        val event = nav.list.single()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination.shouldBeInstanceOf<PreviewRoute>()
        nav.cancel()
    }

    // ─────────────────────────── deletion: clusters ───────────────────────────

    @Test
    fun `deleteClusters with empty collection is a no-op`() = runTest2 {
        val h = harness(clusters = setOf(cluster("a")))
        val events = collectEvents(h.vm)

        h.vm.deleteClusters(emptyList(), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `deleteClusters distinctBy identifier collapses duplicates before emitting confirmation`() = runTest2 {
        // Two references to the same cluster (by identifier) should collapse to one in the
        // ConfirmDeletion event. A regression that didn't dedup would emit two clusters.
        val c = cluster("a")
        val h = harness(clusters = setOf(c))

        h.vm.deleteClusters(listOf(c, c), confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ConfirmDeletion>()
        event.clusters.toList().map { it.identifier } shouldBe listOf(c.identifier)
    }

    @Test
    fun `deleteClusters unconfirmed emits ConfirmDeletion threaded with current allowDeleteAll`() = runTest2 {
        val c = cluster("a")
        val h = harness(clusters = setOf(c), allowDeleteAll = true)

        h.vm.deleteClusters(listOf(c), confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ConfirmDeletion>()
        event.allowDeleteAll shouldBe true
        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteClusters confirmed without pro navigates to upgrade and does NOT submit`() = runTest2 {
        val c = cluster("a")
        val h = harness(clusters = setOf(c), isPro = false)
        val nav = collectNavEvents(h.vm)

        h.vm.deleteClusters(listOf(c), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
        nav.list.size shouldBe 1
        nav.list.single().shouldBeInstanceOf<NavEvent.GoTo>()
        nav.cancel()
    }

    @Test
    fun `deleteClusters confirmed with pro submits TargetMode_Clusters`() = runTest2 {
        val c = cluster("a")
        val h = harness(clusters = setOf(c), isPro = true)

        h.vm.deleteClusters(listOf(c), confirmed = true, deleteAll = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Clusters(
                        targets = setOf(c.identifier),
                        deleteAll = true,
                    ),
                ),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── deletion: single duplicate ───────────────────────────

    @Test
    fun `deleteDuplicate unconfirmed emits ConfirmDupeDeletion with the cluster id`() = runTest2 {
        val c = cluster("a")
        val dupe = c.groups.first().duplicates.first()
        val h = harness(clusters = setOf(c))

        h.vm.deleteDuplicate(c, dupe, confirmed = false)
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ConfirmDupeDeletion>()
        event.duplicates.toList() shouldBe listOf(dupe)
        event.detailsClusterId shouldBe c.identifier
    }

    @Test
    fun `deleteDuplicate confirmed with pro submits a single-target Duplicates task`() = runTest2 {
        val c = cluster("a")
        val dupe = c.groups.first().duplicates.first()
        val h = harness(clusters = setOf(c), isPro = true)

        h.vm.deleteDuplicate(c, dupe, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(
                    mode = DeduplicatorDeleteTask.TargetMode.Duplicates(targets = setOf(dupe.identifier)),
                ),
            )
        }
        // Belt-and-braces: a regression that submits the targeted task PLUS an extra wrong one
        // would still satisfy the specific match above.
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── deletion: multi-duplicate ───────────────────────────

    @Test
    fun `deleteDuplicates with empty set is a no-op`() = runTest2 {
        val h = harness(clusters = setOf(cluster("a")))

        h.vm.deleteDuplicates(emptySet(), confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.taskSubmitter.submit(any()) }
    }

    @Test
    fun `deleteDuplicates unconfirmed emits ConfirmDupeDeletion with detailsClusterId only when all dupes share a cluster`() = runTest2 {
        val a = cluster("a")
        val b = cluster("b")
        val h = harness(clusters = setOf(a, b))

        // Two dupes from cluster `a` only → detailsClusterId should be a.identifier.
        val aDupes = a.groups.first().duplicates.take(2).toSet()
        h.vm.deleteDuplicates(aDupes.map { it.identifier }.toSet(), confirmed = false)
        advanceUntilIdle()

        val sameClusterEvent = h.vm.events.first()
        sameClusterEvent.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ConfirmDupeDeletion>()
        sameClusterEvent.detailsClusterId shouldBe a.identifier

        // Mixed cluster selection → detailsClusterId is null (single details target can't be picked).
        val mixed = setOf(
            a.groups.first().duplicates.first().identifier,
            b.groups.first().duplicates.first().identifier,
        )
        h.vm.deleteDuplicates(mixed, confirmed = false)
        advanceUntilIdle()

        val mixedEvent = h.vm.events.first()
        mixedEvent.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ConfirmDupeDeletion>()
        mixedEvent.detailsClusterId shouldBe null
    }

    @Test
    fun `deleteDuplicates confirmed with pro submits all requested ids`() = runTest2 {
        val c = cluster("c", dupeSizes = listOf(100L, 100L, 100L))
        val ids = c.groups.first().duplicates.map { it.identifier }.toSet()
        val h = harness(clusters = setOf(c), isPro = true)

        h.vm.deleteDuplicates(ids, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            h.taskSubmitter.submit(
                DeduplicatorDeleteTask(mode = DeduplicatorDeleteTask.TargetMode.Duplicates(targets = ids)),
            )
        }
        coVerify(exactly = 1) { h.taskSubmitter.submit(any()) }
    }

    // ─────────────────────────── exclusions ───────────────────────────

    @Test
    fun `excludeClusters with empty collection does not call deduplicator exclude`() = runTest2 {
        val h = harness(clusters = setOf(cluster("a")))
        val events = collectEvents(h.vm)

        h.vm.excludeClusters(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.deduplicator.exclude(any<Set<Duplicate.Cluster.Id>>()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `excludeClusters distinctBy identifier collapses duplicates before calling exclude`() = runTest2 {
        val c = cluster("a", dupeSizes = listOf(100L, 100L))
        val h = harness(clusters = setOf(c))
        coEvery { h.deduplicator.exclude(any<Set<Duplicate.Cluster.Id>>()) } returns Deduplicator.ExclusionUndo(
            exclusionIds = setOf<ExclusionId>(),
            previousData = Deduplicator.Data(clusters = setOf(c)),
            postExcludeData = Deduplicator.Data(clusters = emptySet()),
        )

        h.vm.excludeClusters(listOf(c, c))
        advanceUntilIdle()

        coVerify(exactly = 1) { h.deduplicator.exclude(setOf(c.identifier)) }
    }

    @Test
    fun `excludeClusters event count reflects saved-exclusion count, not requested cluster size`() = runTest2 {
        // Mirrors the CorpseFinder fix (commit d9901e6dc): the snackbar count must reflect what
        // ExclusionManager.save() ACTUALLY persisted, not the number of clusters/files we asked
        // for. When duplicate paths coalesce on save, requested != saved, and the user would
        // otherwise see an inflated "N exclusions created" snackbar. The VM reads
        // `undo.exclusionIds.size` to stay honest.
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(100L, 100L, 100L))
        val h = harness(clusters = setOf(a, b))
        // Two clusters requested but only ONE saved exclusion (coalesced). ExclusionId is a
        // typealias for String, so literals work directly.
        coEvery { h.deduplicator.exclude(any<Set<Duplicate.Cluster.Id>>()) } returns Deduplicator.ExclusionUndo(
            exclusionIds = setOf<ExclusionId>("only-one-saved"),
            previousData = Deduplicator.Data(clusters = setOf(a, b)),
            postExcludeData = Deduplicator.Data(clusters = emptySet()),
        )

        h.vm.excludeClusters(listOf(a, b))
        advanceUntilIdle()

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ExclusionsCreated>()
        // Emits undo.exclusionIds.size = 1, NOT sanitized.sumOf { it.count } = 5.
        event.count shouldBe 1
    }

    @Test
    fun `excludeDuplicates with empty set does not call exclude or emit`() = runTest2 {
        val h = harness(clusters = setOf(cluster("a")))
        val events = collectEvents(h.vm)

        h.vm.excludeDuplicates(emptySet())
        advanceUntilIdle()

        coVerify(exactly = 0) { h.deduplicator.exclude(any<Duplicate.Cluster.Id>(), any()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    @Test
    fun `excludeDuplicates calls exclude per cluster and emits ExclusionsCreated with the total path count`() = runTest2 {
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val b = cluster("b", dupeSizes = listOf(100L, 100L, 100L))
        val h = harness(clusters = setOf(a, b))
        coEvery { h.deduplicator.exclude(any<Duplicate.Cluster.Id>(), any()) } returns Unit

        // Select 1 dupe from a and 2 from b → 3 paths total → 2 exclude() calls (one per cluster).
        val aDupe = a.groups.first().duplicates.first()
        val bDupes = b.groups.first().duplicates.take(2)
        val selectedIds = (listOf(aDupe) + bDupes).map { it.identifier }.toSet()

        h.vm.excludeDuplicates(selectedIds)
        advanceUntilIdle()

        coVerify(exactly = 1) { h.deduplicator.exclude(a.identifier, listOf(aDupe.path)) }
        coVerify(exactly = 1) { h.deduplicator.exclude(b.identifier, bDupes.map { it.path }) }

        val event = h.vm.events.first()
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.ExclusionsCreated>()
        event.count shouldBe 3
    }

    @Test
    fun `excludeDuplicates with ids matching no live duplicates does not emit ExclusionsCreated`() = runTest2 {
        // A stale set of ids (e.g. from a snapshot before a previous deletion) should not call
        // exclude() at all and must not emit a "0 created" snackbar.
        val a = cluster("a", dupeSizes = listOf(100L, 100L))
        val h = harness(clusters = setOf(a))
        val events = collectEvents(h.vm)

        h.vm.excludeDuplicates(setOf(Duplicate.Id("stale-id")))
        advanceUntilIdle()

        coVerify(exactly = 0) { h.deduplicator.exclude(any<Duplicate.Cluster.Id>(), any()) }
        events.list shouldBe emptyList()
        events.cancel()
    }

    // ─────────────────────────── layout mode ───────────────────────────

    @Test
    fun `toggleLayoutMode from LINEAR writes GRID`() = runTest2 {
        // Capture the transform lambda and invoke it on the seeded value. We don't propagate
        // the write back through the mock (that would re-trigger combine() and leave the
        // stateIn collector hot, blocking runTest2 from completing).
        val h = harness(layoutMode = LayoutMode.LINEAR)

        h.vm.toggleLayoutMode()
        advanceUntilIdle()

        val captured = slot<(LayoutMode) -> LayoutMode?>()
        coVerify(exactly = 1) { h.values.layoutMode.update(capture(captured)) }
        captured.captured(LayoutMode.LINEAR) shouldBe LayoutMode.GRID
    }

    @Test
    fun `toggleLayoutMode from GRID writes LINEAR`() = runTest2 {
        val h = harness(layoutMode = LayoutMode.GRID)

        h.vm.toggleLayoutMode()
        advanceUntilIdle()

        val captured = slot<(LayoutMode) -> LayoutMode?>()
        coVerify(exactly = 1) { h.values.layoutMode.update(capture(captured)) }
        captured.captured(LayoutMode.GRID) shouldBe LayoutMode.LINEAR
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
        // Set the stale task before subscription drains.
        h.taskStateFlow.value = TaskSubmitter.State(tasks = setOf(staleTask))
        h.vm.state.filterNotNull().first()
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
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.TaskResult>()
        event.result shouldBe freshResult
    }

    @Test
    fun `init suppresses TaskResult for single-duplicate delete tasks but still forwards subsequent multi-target results`() = runTest2 {
        // Per `isSingleDuplicateDelete`, a Duplicates(targets.size == 1) task has its own UX
        // (no snackbar from the list). Posting one as completed must NOT emit Event.TaskResult.
        // Then post a multi-target task to prove the collector is still alive and only the
        // single-target one was filtered (not the entire flow).
        val c = cluster("a", dupeSizes = listOf(100L, 100L, 100L))
        val dupes = c.groups.first().duplicates.toList()
        val singleDupeId = dupes[0].identifier
        val h = harness(clusters = setOf(c))
        val events = collectEvents(h.vm)
        h.vm.state.filterNotNull().first()

        val singleDel = DeduplicatorDeleteTask(
            mode = DeduplicatorDeleteTask.TargetMode.Duplicates(targets = setOf(singleDupeId)),
        )
        val singleTask = TaskSubmitter.ManagedTask(
            id = "single",
            toolType = SDMTool.Type.DEDUPLICATOR,
            task = singleDel,
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
        event.shouldBeInstanceOf<DeduplicatorListViewModel.Event.TaskResult>()
        event.result shouldBe multiResult
        events.cancel()
    }
}
