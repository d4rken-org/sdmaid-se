package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
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
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.main.ui.dashboard.cards.AnalyzerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SchedulerDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.SwiperDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Instant

/**
 * Regression guard for the dashboard "stuck on the loading spinner" bug: the [DashboardViewModel.listState]
 * combine must produce a non-null state even when an individual source flow never emits (e.g. a slow
 * Room/DataStore/filesystem source stalls on a warm ViewModel recreation). Each combine input is given an
 * immediate fallback so no single stalled source can wedge the whole dashboard.
 */
internal class DashboardViewModelResilienceTest : BaseTest() {

    /** A flow that never emits and never completes — simulates a source stalled on resubscribe. */
    private fun <T> stalled(): Flow<T> = flow { awaitCancellation() }

    /** Build a DashboardViewModel with every source stubbed to a safe collectable flow.
     *  [stall] optionally replaces one wrapped source with a never-emitting flow. */
    private fun TestScope.harness(
        stall: String? = null,
        history: Flow<List<SpaceSnapshotEntity>> = emptyFlow(),
        // The dashboard gates its first frame on the real card config resolving (it can't know which
        // cards to draw otherwise), so default to a config that actually emits. A never-emitting
        // config legitimately keeps the spinner — see `stalled card config keeps the loading spinner`.
        cardConfig: Flow<DashboardCardConfig> = flowOf(DashboardCardConfig()),
        emittingToolStates: Boolean = false,
        appCleanerState: Flow<AppCleaner.State>? = null,
        corpseFinderState: Flow<CorpseFinder.State>? = null,
        taskManagerState: Flow<TaskSubmitter.State>? = null,
    ): DashboardViewModel {
        fun <T> srcOr(name: String, default: Flow<T>): Flow<T> = if (stall == name) stalled() else default

        val taskManager = mockk<TaskManager>(relaxed = true).apply {
            every { state } returns (taskManagerState ?: MutableStateFlow(TaskSubmitter.State()))
        }
        // The bottom bar combine has no fallbacks; give it live (data-less) tool states when needed.
        val corpseFinder = mockk<CorpseFinder>(relaxed = true).apply {
            every { state } returns when {
                corpseFinderState != null -> corpseFinderState
                emittingToolStates -> MutableStateFlow(mockk<CorpseFinder.State>(relaxed = true) { every { data } returns null })
                else -> emptyFlow()
            }
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { state } returns
                if (emittingToolStates) MutableStateFlow(mockk<SystemCleaner.State>(relaxed = true) { every { data } returns null }) else emptyFlow()
        }
        val appCleaner = mockk<AppCleaner>(relaxed = true).apply {
            every { state } returns when {
                appCleanerState != null -> appCleanerState
                emittingToolStates -> MutableStateFlow(mockk<AppCleaner.State>(relaxed = true) { every { data } returns null })
                else -> emptyFlow()
            }
        }
        val deduplicator = mockk<Deduplicator>(relaxed = true).apply {
            every { state } returns
                if (emittingToolStates) MutableStateFlow(mockk<Deduplicator.State>(relaxed = true) { every { data } returns null }) else emptyFlow()
        }
        val squeezer = mockk<Squeezer>(relaxed = true).apply { every { state } returns emptyFlow() }
        val appControl = mockk<AppControl>(relaxed = true).apply { every { state } returns emptyFlow() }
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns srcOr("analyzer", emptyFlow())
            every { progress } returns emptyFlow()
        }
        val schedulerManager = mockk<SchedulerManager>(relaxed = true).apply { every { state } returns emptyFlow() }
        val setupManager = mockk<SetupManager>(relaxed = true).apply { every { state } returns srcOr("setup", emptyFlow()) }
        val areaManager = mockk<DataAreaManager>(relaxed = true).apply { every { latestState } returns srcOr("dataArea", emptyFlow()) }
        val sessionManager = mockk<DebugLogSessionManager>(relaxed = true).apply { every { sessions } returns srcOr("sessions", emptyFlow()) }
        val motdRepo = mockk<MotdRepo>(relaxed = true).apply { every { motd } returns srcOr("motd", emptyFlow()) }
        val reviewTool = mockk<ReviewTool>(relaxed = true).apply { every { state } returns srcOr("review", emptyFlow()) }
        val anniversaryProvider = mockk<AnniversaryProvider>(relaxed = true).apply { every { item } returns srcOr("anniversary", emptyFlow()) }
        val statsRepo = mockk<StatsRepo>(relaxed = true).apply { every { state } returns srcOr("stats", emptyFlow()) }
        val swiper = mockk<Swiper>(relaxed = true).apply {
            every { getSessionsWithStats() } returns srcOr("swiper", emptyFlow())
            every { progress } returns emptyFlow()
        }
        val upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply {
            every { upgradeInfo } returns emptyFlow()
            every { isSettled } returns flowOf(true)
        }
        val updateService = mockk<UpdateService>(relaxed = true).apply { every { availableUpdate } returns emptyFlow() }
        val debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
            every { create(any(), any(), any(), any()) } returns srcOr("debug", emptyFlow())
        }
        val statsSettings = mockk<StatsSettings>(relaxed = true).apply {
            every { retentionReports } returns mockDuration()
            every { retentionPaths } returns mockDuration()
            every { retentionSnapshots } returns mockDuration()
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { dashboardCardConfig } returns mockk(relaxed = true) {
                every { flow } returns cardConfig
            }
            every { enableDashboardOneClick } returns mockk(relaxed = true) {
                every { flow } returns MutableStateFlow(false)
            }
        }
        val spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
            every { getAllHistory(any()) } returns history
        }

        val vm = DashboardViewModel(
            context = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            areaManager = areaManager,
            taskManager = taskManager,
            setupManager = setupManager,
            corpseFinder = corpseFinder,
            systemCleaner = systemCleaner,
            appCleaner = appCleaner,
            appControl = appControl,
            analyzer = analyzer,
            debugCardProvider = debugCardProvider,
            deduplicator = deduplicator,
            squeezer = squeezer,
            swiper = swiper,
            upgradeRepo = upgradeRepo,
            generalSettings = generalSettings,
            webpageTool = mockk<WebpageTool>(relaxed = true),
            schedulerManager = schedulerManager,
            updateService = updateService,
            sessionManager = sessionManager,
            motdRepo = motdRepo,
            releaseManager = mockk<ReleaseManager>(relaxed = true),
            reviewTool = reviewTool,
            anniversaryProvider = anniversaryProvider,
            statsRepo = statsRepo,
            statsSettings = statsSettings,
            spaceHistoryRepo = spaceHistoryRepo,
            deviceDetective = mockk(relaxed = true),
        )

        // Keep listState subscribed for the test scope's lifetime (safeStateIn uses WhileSubscribed).
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.listState.collect { } }
        return vm
    }

    private fun mockDuration(): DataStoreValue<java.time.Duration> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(java.time.Duration.ZERO)
    }

    @Test
    fun `listState emits when all sources behave`() = runTest2 {
        val vm = harness()
        advanceUntilIdle()
        vm.listState.value.shouldNotBeNull()
    }

    @Test
    fun `analyzer card shows trend shimmer until history resolves`() = runTest2 {
        val history = MutableSharedFlow<List<SpaceSnapshotEntity>>(replay = 1)
        val vm = harness(
            history = history,
            cardConfig = flowOf(DashboardCardConfig()),
        )
        advanceUntilIdle()

        val loading = vm.listState.value
            .shouldNotBeNull()
            .items.filterIsInstance<AnalyzerDashboardCardItem>().single()
        loading.isLoadingTrend shouldBe true
        loading.combinedDelta shouldBe null

        val t0 = Instant.parse("2026-06-01T00:00:00Z")
        history.emit(
            listOf(
                SpaceSnapshotEntity(storageId = "a", recordedAt = t0, spaceFree = 900, spaceCapacity = 1000),
                SpaceSnapshotEntity(storageId = "a", recordedAt = t0.plusSeconds(60), spaceFree = 800, spaceCapacity = 1000),
            )
        )
        // throttleLatest(500) delays on a real-time dispatcher (TestDispatcherProvider is Unconfined),
        // so await the resolved state instead of advancing virtual time.
        val loaded = vm.listState
            .mapNotNull { it?.items?.filterIsInstance<AnalyzerDashboardCardItem>()?.singleOrNull() }
            .first { !it.isLoadingTrend }
        loaded.combinedDelta shouldBe 100L
    }

    @Test
    fun `bottom bar emits even when upgrade info never emits`() = runTest2 {
        // upgradeRepo.upgradeInfo is emptyFlow() in the harness; the VM's shared upgradeInfo
        // injects an immediate null so the bottom bar must not stall waiting for billing.
        val vm = harness(emittingToolStates = true)

        val bar = vm.bottomBarState.mapNotNull { it }.first()

        bar.upgradeInfo shouldBe null
        bar.actionState shouldBe BottomBarState.Action.SCAN
    }

    @Test
    fun `appcleaner card derives freeable size from live data not the frozen scan result`() = runTest2 {
        fun junk(size: Long, items: Int): AppJunk = mockk {
            every { this@mockk.size } returns size
            every { itemCount } returns items
        }
        fun appState(vararg junks: AppJunk): AppCleaner.State = mockk(relaxed = true) {
            every { data } returns AppCleaner.Data(junks = junks.toList())
            every { progress } returns null
        }

        val liveState = MutableStateFlow(appState(junk(400L, 3), junk(100L, 2))) // live: 500 / 5

        // A completed scan task whose frozen result advertises a now-stale, larger total.
        val frozenScan = AppCleanerScanTask.Success(itemCount = 10, recoverableSpace = 1000L)
        val completedTask = mockk<TaskSubmitter.ManagedTask>(relaxed = true) {
            every { toolType } returns SDMTool.Type.APPCLEANER
            every { isComplete } returns true
            every { completedAt } returns Instant.EPOCH
            every { result } returns frozenScan
        }

        val vm = harness(
            appCleanerState = liveState,
            taskManagerState = MutableStateFlow(TaskSubmitter.State(tasks = listOf(completedTask))),
        )

        fun appCard() = vm.listState.mapNotNull { list ->
            list?.items?.filterIsInstance<ToolDashboardCardItem>()?.firstOrNull { it.toolType == SDMTool.Type.APPCLEANER }
        }

        // The card reflects LIVE data (500 / 5), not the frozen scan result (1000 / 10).
        appCard().first { it.result == AppCleanerScanTask.Success(itemCount = 5, recoverableSpace = 500L) }

        // Applying an exclusion shrinks live data; the card refreshes without a new task being submitted.
        liveState.value = appState(junk(100L, 2)) // live: 100 / 2
        appCard().first { it.result == AppCleanerScanTask.Success(itemCount = 2, recoverableSpace = 100L) }
    }

    @Test
    fun `corpsefinder card reflects live data via lastResult ignoring the uninstall watcher`() = runTest2 {
        // Simulates: a user scan found 1 corpse, then the background uninstall watcher deleted it.
        // CorpseFinder keeps the user-visible scan result in Data.lastResult (never overwritten by the
        // watcher) while corpses drain to empty. The card must show the live-derived Success(0, 0),
        // sourced from Data.lastResult — not the watcher's task result from TaskManager.
        val corpseState = MutableStateFlow<CorpseFinder.State>(
            mockk(relaxed = true) {
                every { data } returns CorpseFinder.Data(
                    corpses = emptyList(),
                    lastResult = CorpseFinderScanTask.Success(itemCount = 1, recoverableSpace = 100L),
                )
                every { progress } returns null
            }
        )
        val vm = harness(corpseFinderState = corpseState)

        vm.listState
            .mapNotNull { list ->
                list?.items?.filterIsInstance<ToolDashboardCardItem>()?.firstOrNull { it.toolType == SDMTool.Type.CORPSEFINDER }
            }
            .first { it.result == CorpseFinderScanTask.Success(itemCount = 0, recoverableSpace = 0L) }
    }

    @Test
    fun `listState emits even when a single source never emits`() = runTest2 {
        // One stalled source must not wedge the whole dashboard.
        for (stall in listOf("sessions", "motd", "stats", "swiper", "analyzer", "anniversary", "setup", "dataArea", "review", "debug")) {
            val vm = harness(stall = stall)
            advanceUntilIdle()
            withClue(stall) { vm.listState.value.shouldNotBeNull() }
        }
    }

    @Test
    fun `scheduler and swiper cards render as initializing placeholders in the first frame`() = runTest2 {
        // Regression guard: their backend states (DynamicStateFlow on IO / Room) don't emit here,
        // yet the builders self-seed an initializing placeholder so the cards never pop in after the
        // skeleton — and render a neutral loading state instead of misleading empty-state content.
        val vm = harness()
        advanceUntilIdle()

        val items = vm.listState.value.shouldNotBeNull().items
        items.filterIsInstance<SchedulerDashboardCardItem>().single().isInitializing shouldBe true
        items.filterIsInstance<SwiperDashboardCardItem>().single().isInitializing shouldBe true
    }

    @Test
    fun `cards hidden in the config are absent from the first frame`() = runTest2 {
        val config = DashboardCardConfig(
            cards = DashboardCardType.entries.map {
                DashboardCardConfig.CardEntry(
                    type = it,
                    isVisible = it != DashboardCardType.SCHEDULER && it != DashboardCardType.SWIPER,
                )
            },
        )
        val vm = harness(cardConfig = flowOf(config))
        advanceUntilIdle()

        val items = vm.listState.value.shouldNotBeNull().items
        // Tool cards still render as placeholders, but the two hidden ones must not appear.
        items.filterIsInstance<ToolDashboardCardItem>().isNotEmpty() shouldBe true
        items.filterIsInstance<SchedulerDashboardCardItem>().isEmpty() shouldBe true
        items.filterIsInstance<SwiperDashboardCardItem>().isEmpty() shouldBe true
    }

    @Test
    fun `stalled card config keeps the loading spinner`() = runTest2 {
        // The dashboard intentionally gates on the card config: without it we can't know which cards
        // to draw, so the spinner stays rather than flashing a default layout. Production DataStore
        // always emits; only a pathological/never-emitting config keeps us here.
        val vm = harness(cardConfig = stalled())
        advanceUntilIdle()
        vm.listState.value shouldBe null
    }
}
