package eu.darken.sdmse.systemcleaner.ui.settings

import eu.darken.sdmse.common.access.AccessState
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.navigation.routes.CustomFilterListRoute
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.setup.SetupRoute
import eu.darken.sdmse.systemcleaner.core.SystemCleaner
import eu.darken.sdmse.systemcleaner.core.SystemCleanerSettings
import eu.darken.sdmse.systemcleaner.core.rwDataStoreValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import java.time.Duration

class SystemCleanerSettingsViewModelTest : BaseTest() {

    private class Values(
        val filterLogFiles: DataStoreValue<Boolean>,
        val filterAdvertisements: DataStoreValue<Boolean>,
        val filterEmptyDirectories: DataStoreValue<Boolean>,
        val filterSuperfluosApks: DataStoreValue<Boolean>,
        val filterSuperfluosApksIncludeSameVersion: DataStoreValue<Boolean>,
        val filterTrashed: DataStoreValue<Boolean>,
        val filterScreenshots: DataStoreValue<Boolean>,
        val filterLostDir: DataStoreValue<Boolean>,
        val filterLinuxFiles: DataStoreValue<Boolean>,
        val filterMacFiles: DataStoreValue<Boolean>,
        val filterWindowsFiles: DataStoreValue<Boolean>,
        val filterTempFiles: DataStoreValue<Boolean>,
        val filterThumbnails: DataStoreValue<Boolean>,
        val filterAnalytics: DataStoreValue<Boolean>,
        val filterAnr: DataStoreValue<Boolean>,
        val filterLocalTmp: DataStoreValue<Boolean>,
        val filterDownloadCache: DataStoreValue<Boolean>,
        val filterDataLogger: DataStoreValue<Boolean>,
        val filterLogDropbox: DataStoreValue<Boolean>,
        val filterRecentTasks: DataStoreValue<Boolean>,
        val filterTombstones: DataStoreValue<Boolean>,
        val filterUsageStats: DataStoreValue<Boolean>,
        val filterPackageCache: DataStoreValue<Boolean>,
        val filterScreenshotsAge: DataStoreValue<Duration>,
    )

    private class Harness(
        val vm: SystemCleanerSettingsViewModel,
        val settings: SystemCleanerSettings,
        val values: Values,
    )

    private fun buildHarness(
        rooted: Boolean = false,
        isPro: Boolean = false,
        filterLogFiles: Boolean = true,
        filterAdvertisements: Boolean = true,
        filterEmptyDirectories: Boolean = true,
        filterSuperfluosApks: Boolean = false,
        filterSuperfluosApksIncludeSameVersion: Boolean = true,
        filterTrashed: Boolean = false,
        filterScreenshots: Boolean = false,
        filterLostDir: Boolean = true,
        filterLinuxFiles: Boolean = true,
        filterMacFiles: Boolean = true,
        filterWindowsFiles: Boolean = true,
        filterTempFiles: Boolean = true,
        filterThumbnails: Boolean = false,
        filterAnalytics: Boolean = true,
        filterAnr: Boolean = true,
        filterLocalTmp: Boolean = false,
        filterDownloadCache: Boolean = true,
        filterDataLogger: Boolean = true,
        filterLogDropbox: Boolean = true,
        filterRecentTasks: Boolean = false,
        filterTombstones: Boolean = false,
        filterUsageStats: Boolean = false,
        filterPackageCache: Boolean = false,
        screenshotsAge: Duration = SystemCleanerSettings.SCREENSHOTS_AGE_DEFAULT,
    ): Harness {
        val values = Values(
            filterLogFiles = rwDataStoreValue(filterLogFiles),
            filterAdvertisements = rwDataStoreValue(filterAdvertisements),
            filterEmptyDirectories = rwDataStoreValue(filterEmptyDirectories),
            filterSuperfluosApks = rwDataStoreValue(filterSuperfluosApks),
            filterSuperfluosApksIncludeSameVersion = rwDataStoreValue(filterSuperfluosApksIncludeSameVersion),
            filterTrashed = rwDataStoreValue(filterTrashed),
            filterScreenshots = rwDataStoreValue(filterScreenshots),
            filterLostDir = rwDataStoreValue(filterLostDir),
            filterLinuxFiles = rwDataStoreValue(filterLinuxFiles),
            filterMacFiles = rwDataStoreValue(filterMacFiles),
            filterWindowsFiles = rwDataStoreValue(filterWindowsFiles),
            filterTempFiles = rwDataStoreValue(filterTempFiles),
            filterThumbnails = rwDataStoreValue(filterThumbnails),
            filterAnalytics = rwDataStoreValue(filterAnalytics),
            filterAnr = rwDataStoreValue(filterAnr),
            filterLocalTmp = rwDataStoreValue(filterLocalTmp),
            filterDownloadCache = rwDataStoreValue(filterDownloadCache),
            filterDataLogger = rwDataStoreValue(filterDataLogger),
            filterLogDropbox = rwDataStoreValue(filterLogDropbox),
            filterRecentTasks = rwDataStoreValue(filterRecentTasks),
            filterTombstones = rwDataStoreValue(filterTombstones),
            filterUsageStats = rwDataStoreValue(filterUsageStats),
            filterPackageCache = rwDataStoreValue(filterPackageCache),
            filterScreenshotsAge = rwDataStoreValue(screenshotsAge),
        )
        val settings = mockk<SystemCleanerSettings>().apply {
            every { filterLogFilesEnabled } returns values.filterLogFiles
            every { filterAdvertisementsEnabled } returns values.filterAdvertisements
            every { filterEmptyDirectoriesEnabled } returns values.filterEmptyDirectories
            every { filterSuperfluosApksEnabled } returns values.filterSuperfluosApks
            every { this@apply.filterSuperfluosApksIncludeSameVersion } returns values.filterSuperfluosApksIncludeSameVersion
            every { filterTrashedEnabled } returns values.filterTrashed
            every { filterScreenshotsEnabled } returns values.filterScreenshots
            every { filterLostDirEnabled } returns values.filterLostDir
            every { filterLinuxFilesEnabled } returns values.filterLinuxFiles
            every { filterMacFilesEnabled } returns values.filterMacFiles
            every { filterWindowsFilesEnabled } returns values.filterWindowsFiles
            every { filterTempFilesEnabled } returns values.filterTempFiles
            every { filterThumbnailsEnabled } returns values.filterThumbnails
            every { filterAnalyticsEnabled } returns values.filterAnalytics
            every { filterAnrEnabled } returns values.filterAnr
            every { filterLocalTmpEnabled } returns values.filterLocalTmp
            every { filterDownloadCacheEnabled } returns values.filterDownloadCache
            every { filterDataLoggerEnabled } returns values.filterDataLogger
            every { filterLogDropboxEnabled } returns values.filterLogDropbox
            every { filterRecentTasksEnabled } returns values.filterRecentTasks
            every { filterTombstonesEnabled } returns values.filterTombstones
            every { filterUsageStatsEnabled } returns values.filterUsageStats
            every { filterPackageCacheEnabled } returns values.filterPackageCache
            every { this@apply.filterScreenshotsAge } returns values.filterScreenshotsAge
        }
        val systemCleaner = mockk<SystemCleaner>(relaxed = true).apply {
            every { state } returns MutableStateFlow(
                SystemCleaner.State(
                    data = null,
                    progress = null as Progress.Data?,
                    areSystemFilterAvailable = rooted,
                ),
            )
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(
                mockk<UpgradeRepo.Info>().apply { every { this@apply.isPro } returns isPro },
            )
        }
        val rootManager = mockk<RootManager>().apply {
            every { accessState } returns flowOf(
                if (rooted) AccessState.Active else AccessState.Undecided,
            )
        }
        val vm = SystemCleanerSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            upgradeRepo = upgradeRepo,
            systemCleaner = systemCleaner,
            rootManager = rootManager,
            settings = settings,
        )
        return Harness(vm, settings, values)
    }

    @Test
    fun `state propagates all 23 filter booleans from settings`() = runTest2 {
        val h = buildHarness(
            filterLogFiles = false,
            filterAdvertisements = false,
            filterEmptyDirectories = false,
            filterSuperfluosApks = true,
            filterSuperfluosApksIncludeSameVersion = false,
            filterTrashed = true,
            filterScreenshots = true,
            filterLostDir = false,
            filterLinuxFiles = false,
            filterMacFiles = false,
            filterWindowsFiles = false,
            filterTempFiles = false,
            filterThumbnails = true,
            filterAnalytics = false,
            filterAnr = false,
            filterLocalTmp = true,
            filterDownloadCache = false,
            filterDataLogger = false,
            filterLogDropbox = false,
            filterRecentTasks = true,
            filterTombstones = true,
            filterUsageStats = true,
            filterPackageCache = true,
        )

        val state = h.vm.state.first()

        state.filterLogFiles shouldBe false
        state.filterAdvertisements shouldBe false
        state.filterEmptyDirectories shouldBe false
        state.filterSuperfluosApks shouldBe true
        state.filterSuperfluosApksIncludeSameVersion shouldBe false
        state.filterTrashed shouldBe true
        state.filterScreenshots shouldBe true
        state.filterLostDir shouldBe false
        state.filterLinuxFiles shouldBe false
        state.filterMacFiles shouldBe false
        state.filterWindowsFiles shouldBe false
        state.filterTempFiles shouldBe false
        state.filterThumbnails shouldBe true
        state.filterAnalytics shouldBe false
        state.filterAnr shouldBe false
        state.filterLocalTmp shouldBe true
        state.filterDownloadCache shouldBe false
        state.filterDataLogger shouldBe false
        state.filterLogDropbox shouldBe false
        state.filterRecentTasks shouldBe true
        state.filterTombstones shouldBe true
        state.filterUsageStats shouldBe true
        state.filterPackageCache shouldBe true
    }

    @Test
    fun `state propagates areSystemFilterAvailable from systemCleaner state`() = runTest2 {
        buildHarness(rooted = true).vm.state.first().areSystemFilterAvailable shouldBe true
        buildHarness(rooted = false).vm.state.first().areSystemFilterAvailable shouldBe false
    }

    @Test
    fun `state propagates screenshotsAge from settings`() = runTest2 {
        val customAge = Duration.ofDays(30)
        buildHarness(screenshotsAge = customAge).vm.state.first().screenshotsAge shouldBe customAge
    }

    @Test
    fun `state propagates isPro from upgradeRepo`() = runTest2 {
        buildHarness(isPro = true).vm.state.first().isPro shouldBe true
        buildHarness(isPro = false).vm.state.first().isPro shouldBe false
    }

    @Test
    fun `setFilterLogFiles writes through to DataStore`() = runTest2 {
        val h = buildHarness()
        h.vm.setFilterLogFiles(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterLogFiles.update(any()) }
    }

    @Test
    fun `setFilterTrashed writes through to DataStore - representative generic toggle`() = runTest2 {
        val h = buildHarness()
        h.vm.setFilterTrashed(true)
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterTrashed.update(any()) }
    }

    @Test
    fun `setFilterAnr writes through to DataStore - representative root-gated toggle`() = runTest2 {
        val h = buildHarness()
        h.vm.setFilterAnr(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterAnr.update(any()) }
    }

    @Test
    fun `setFilterSuperfluosApksIncludeSameVersion writes through to DataStore`() = runTest2 {
        // Conditional sub-row: only visible in the screen when filterSuperfluosApks is true.
        val h = buildHarness()
        h.vm.setFilterSuperfluosApksIncludeSameVersion(false)
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterSuperfluosApksIncludeSameVersion.update(any()) }
    }

    @Test
    fun `setFilterScreenshotsAge writes through to DataStore`() = runTest2 {
        val h = buildHarness()
        h.vm.setFilterScreenshotsAge(Duration.ofDays(7))
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterScreenshotsAge.update(any()) }
    }

    @Test
    fun `resetFilterScreenshotsAge writes the default through to DataStore`() = runTest2 {
        val h = buildHarness()
        h.vm.resetFilterScreenshotsAge()
        advanceUntilIdle()
        coVerify(exactly = 1) { h.values.filterScreenshotsAge.update(any()) }
    }

    @Test
    fun `onCustomFiltersClick navigates to CustomFilterListRoute`() = runTest2 {
        val h = buildHarness()
        h.vm.onCustomFiltersClick()
        advanceUntilIdle()
        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination shouldBe CustomFilterListRoute
    }

    @Test
    fun `onRootFilterBadgeClick navigates to SetupRoute with ROOT typeFilter`() = runTest2 {
        val h = buildHarness()
        h.vm.onRootFilterBadgeClick()
        advanceUntilIdle()
        val event = h.vm.navEvents.first()
        event.shouldBeInstanceOf<NavEvent.GoTo>()
        event.destination.shouldBeInstanceOf<SetupRoute>()
    }
}
