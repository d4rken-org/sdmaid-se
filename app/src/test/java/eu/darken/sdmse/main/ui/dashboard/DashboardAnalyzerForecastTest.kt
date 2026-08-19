package eu.darken.sdmse.main.ui.dashboard

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.appcleaner.core.AppCleaner
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
import eu.darken.sdmse.deduplicator.core.Deduplicator
import eu.darken.sdmse.main.core.CurriculumVitae
import eu.darken.sdmse.main.core.DashboardCardConfig
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.motd.MotdRepo
import eu.darken.sdmse.main.core.release.ReleaseManager
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.ui.dashboard.cards.AnalyzerDashboardCardItem
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.setup.SetupManager
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.SpaceTracker
import eu.darken.sdmse.stats.core.StatsRepo
import eu.darken.sdmse.stats.core.StatsSettings
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import eu.darken.sdmse.swiper.core.Swiper
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Analyzer card's free-space forecast: derived from the primary volume alone and from a live
 * capacity/free reading, while the delta line keeps summing every storage.
 */
internal class DashboardAnalyzerForecastTest : BaseTest() {

    private val base = LocalDate.parse("2026-06-01")
    private val primaryId = "primary"
    private val secondaryId = "sd-card"
    private val primaryCapacity = 100_000_000_000L
    private val secondaryCapacity = 64_000_000_000L
    private val floor = 2_147_483_648L

    private fun snapshot(
        storageId: String,
        day: Long,
        used: Long,
        capacity: Long,
        hour: Long,
    ) = SpaceSnapshotEntity(
        storageId = storageId,
        recordedAt = base.plusDays(day).atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(hour)),
        spaceFree = capacity - used,
        spaceCapacity = capacity,
    )

    /** Seven days of primary growth at [ratePerDay], plus a flat secondary volume recorded later each day. */
    private fun mixedHistory(ratePerDay: Long): List<SpaceSnapshotEntity> = (0L..6L).flatMap { day ->
        listOf(
            snapshot(
                storageId = primaryId,
                day = day,
                used = 10_000_000_000L + ratePerDay * day,
                capacity = primaryCapacity,
                hour = 12,
            ),
            // Later in the day and on a different capacity: if these leaked into the primary
            // forecast they would own every day bucket and flatten the rate to zero.
            snapshot(
                storageId = secondaryId,
                day = day,
                used = 60_000_000_000L,
                capacity = secondaryCapacity,
                hour = 18,
            ),
        )
    }

    private fun primaryReading(free: Long) = SpaceTracker.StorageSnapshot(
        storageId = primaryId,
        spaceFree = free,
        spaceCapacity = primaryCapacity,
    )

    private fun TestScope.harness(
        history: List<SpaceSnapshotEntity>,
        primary: SpaceTracker.StorageSnapshot?,
        thresholdSource: Flow<Long?> = flowOf(null),
    ): DashboardViewModel = harness(
        historySource = flowOf(history),
        primarySource = { primary },
        thresholdSource = thresholdSource,
    )

    private fun TestScope.harness(
        historySource: Flow<List<SpaceSnapshotEntity>>,
        primarySource: suspend () -> SpaceTracker.StorageSnapshot?,
        thresholdSource: Flow<Long?> = flowOf(null),
    ): DashboardViewModel {
        val statsSettings = mockk<StatsSettings>(relaxed = true).apply {
            every { retentionReports } returns mockDuration()
            every { retentionPaths } returns mockDuration()
            every { retentionSnapshots } returns mockDuration()
        }
        val generalSettings = mockk<GeneralSettings>(relaxed = true).apply {
            every { dashboardCardConfig } returns mockk(relaxed = true) {
                every { flow } returns flowOf(DashboardCardConfig())
            }
            every { enableDashboardOneClick } returns mockk(relaxed = true) {
                every { flow } returns MutableStateFlow(false)
            }
            every { dashboardHeroAutoShow } returns mockk(relaxed = true) {
                every { flow } returns MutableStateFlow(true)
            }
        }
        // A relaxed mock's flow never emits, which would stall the Analyzer card's combine.
        val analyzerSettings = mockk<AnalyzerSettings>(relaxed = true).apply {
            every { lowStorageThresholdBytes } returns mockk(relaxed = true) {
                every { flow } returns thresholdSource
            }
        }

        val vm = DashboardViewModel(
            context = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            areaManager = mockk<DataAreaManager>(relaxed = true).apply { every { latestState } returns emptyFlow() },
            taskManager = mockk<TaskManager>(relaxed = true).apply {
                every { state } returns MutableStateFlow(TaskSubmitter.State())
            },
            setupManager = mockk<SetupManager>(relaxed = true).apply { every { state } returns emptyFlow() },
            corpseFinder = mockk<CorpseFinder>(relaxed = true).apply { every { state } returns emptyFlow() },
            systemCleaner = mockk<SystemCleaner>(relaxed = true).apply { every { state } returns emptyFlow() },
            appCleaner = mockk<AppCleaner>(relaxed = true).apply { every { state } returns emptyFlow() },
            appControl = mockk<AppControl>(relaxed = true).apply { every { state } returns emptyFlow() },
            analyzer = mockk<Analyzer>(relaxed = true).apply {
                every { data } returns emptyFlow()
                every { progress } returns emptyFlow()
            },
            analyzerSettings = analyzerSettings,
            debugCardProvider = mockk<DebugCardProvider>(relaxed = true).apply {
                every { create(any(), any(), any(), any()) } returns emptyFlow()
            },
            deduplicator = mockk<Deduplicator>(relaxed = true).apply { every { state } returns emptyFlow() },
            squeezer = mockk<Squeezer>(relaxed = true).apply { every { state } returns emptyFlow() },
            swiper = mockk<Swiper>(relaxed = true).apply {
                every { getSessionsWithStats() } returns emptyFlow()
                every { progress } returns emptyFlow()
            },
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true).apply { every { upgradeInfo } returns emptyFlow() },
            generalSettings = generalSettings,
            webpageTool = mockk<WebpageTool>(relaxed = true),
            schedulerManager = mockk<SchedulerManager>(relaxed = true).apply { every { state } returns emptyFlow() },
            updateService = mockk<UpdateService>(relaxed = true).apply { every { availableUpdate } returns emptyFlow() },
            sessionManager = mockk<DebugLogSessionManager>(relaxed = true).apply { every { sessions } returns emptyFlow() },
            motdRepo = mockk<MotdRepo>(relaxed = true).apply { every { motd } returns emptyFlow() },
            releaseManager = mockk<ReleaseManager>(relaxed = true),
            reviewTool = mockk<ReviewTool>(relaxed = true).apply { every { state } returns emptyFlow() },
            anniversaryProvider = mockk<AnniversaryProvider>(relaxed = true).apply { every { item } returns emptyFlow() },
            statsRepo = mockk<StatsRepo>(relaxed = true).apply { every { state } returns emptyFlow() },
            statsSettings = statsSettings,
            curriculumVitae = mockk<CurriculumVitae>(relaxed = true).apply { every { installedAt } returns emptyFlow() },
            spaceHistoryRepo = mockk<SpaceHistoryRepo>(relaxed = true).apply {
                every { getAllHistory(any()) } returns historySource
            },
            spaceTracker = mockk<SpaceTracker>(relaxed = true).apply {
                coEvery { readPrimaryStorage() } coAnswers { primarySource() }
            },
            deviceDetective = mockk(relaxed = true),
        )

        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.listState.collect { } }
        return vm
    }

    private fun mockDuration(): DataStoreValue<Duration> = mockk(relaxed = true) {
        every { flow } returns MutableStateFlow(Duration.ZERO)
    }

    private suspend fun DashboardViewModel.analyzerCard(
        predicate: (AnalyzerDashboardCardItem) -> Boolean = { true },
    ): AnalyzerDashboardCardItem = listState
        .mapNotNull { it?.items?.filterIsInstance<AnalyzerDashboardCardItem>()?.singleOrNull() }
        .first { !it.isLoadingTrend && predicate(it) }

    @Test
    fun `the forecast comes from the primary volume alone`() = runTest2 {
        val vm = harness(
            history = mixedHistory(ratePerDay = 1_000_000_000L),
            primary = primaryReading(free = 20_000_000_000L),
        )

        vm.analyzerCard().forecast shouldBe StorageForecast.Filling(
            daysUntilFloor = 18,
            bytesPerDay = 1_000_000_000L,
            isUrgent = false,
        )
    }

    @Test
    fun `an urgent forecast reaches the card`() = runTest2 {
        val vm = harness(
            history = mixedHistory(ratePerDay = 1_000_000_000L),
            primary = primaryReading(free = floor + 5_500_000_000L),
        )

        vm.analyzerCard().forecast shouldBe StorageForecast.Filling(
            daysUntilFloor = 6,
            bytesPerDay = 1_000_000_000L,
            isUrgent = true,
        )
    }

    @Test
    fun `without a live reading there is no forecast and the delta line survives`() = runTest2 {
        val vm = harness(
            history = mixedHistory(ratePerDay = 1_000_000_000L),
            primary = null,
        )

        val card = vm.analyzerCard()
        card.forecast.shouldBeNull()
        card.combinedDelta shouldBe 6_000_000_000L
    }

    @Test
    fun `every history emission is forecast against a fresh live reading`() = runTest2 {
        // A cleanup forces a snapshot, so the history re-emits within the hourly refresh window.
        // Reusing the reading taken at the last tick would keep an obsolete forecast on screen.
        val historySource = MutableSharedFlow<List<SpaceSnapshotEntity>>(replay = 1)
        var reading = primaryReading(free = 20_000_000_000L)
        val vm = harness(historySource = historySource, primarySource = { reading })

        historySource.emit(mixedHistory(ratePerDay = 1_000_000_000L))
        val stale = vm.analyzerCard().forecast
        stale shouldBe StorageForecast.Filling(
            daysUntilFloor = 18,
            bytesPerDay = 1_000_000_000L,
            isUrgent = false,
        )

        reading = primaryReading(free = 50_000_000_000L)
        historySource.emit(mixedHistory(ratePerDay = 1_000_000_000L))

        vm.analyzerCard { it.forecast != stale }.forecast shouldBe StorageForecast.Filling(
            daysUntilFloor = 48,
            bytesPerDay = 1_000_000_000L,
            isUrgent = false,
        )
    }

    @Test
    fun `a non-filling forecast leaves the delta line in place`() = runTest2 {
        val vm = harness(
            history = mixedHistory(ratePerDay = 0L),
            primary = primaryReading(free = 20_000_000_000L),
        )

        val card = vm.analyzerCard()
        card.forecast shouldBe StorageForecast.Stable
        card.combinedDelta shouldBe 0L
    }

    @Test
    fun `a custom threshold recomputes the forecast`() = runTest2 {
        // Same history and the same live reading: only the configured floor moves, and the card
        // has to follow it.
        val threshold = MutableStateFlow<Long?>(null)
        val vm = harness(
            history = mixedHistory(ratePerDay = 1_000_000_000L),
            primary = primaryReading(free = 20_000_000_000L),
            thresholdSource = threshold,
        )

        val automatic = vm.analyzerCard().forecast
        automatic shouldBe StorageForecast.Filling(
            daysUntilFloor = 18,
            bytesPerDay = 1_000_000_000L,
            isUrgent = false,
        )

        threshold.value = 10_000_000_000L

        vm.analyzerCard { it.forecast != automatic }.forecast shouldBe StorageForecast.Filling(
            daysUntilFloor = 10,
            bytesPerDay = 1_000_000_000L,
            isUrgent = true,
        )
    }

    @Test
    fun `a custom threshold above the free space puts the card below the floor`() = runTest2 {
        val vm = harness(
            history = mixedHistory(ratePerDay = 1_000_000_000L),
            primary = primaryReading(free = 20_000_000_000L),
            thresholdSource = flowOf(25_000_000_000L),
        )

        vm.analyzerCard().forecast shouldBe StorageForecast.BelowFloor
    }
}
