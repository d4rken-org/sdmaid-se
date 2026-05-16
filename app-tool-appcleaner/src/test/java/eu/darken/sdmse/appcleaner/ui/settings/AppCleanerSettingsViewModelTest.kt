package eu.darken.sdmse.appcleaner.ui.settings

import eu.darken.sdmse.appcleaner.core.AppCleaner
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.progress.Progress
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AppCleanerSettingsViewModelTest : BaseTest() {

    // `.value(value: T)` is an extension that delegates to `DataStoreValue.update`. Stub `update`
    // to a no-op success so MockK can both succeed the call and verify it later.
    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private fun appState(
        otherUsers: Boolean = false,
        runningApps: Boolean = false,
        inaccessibleCache: Boolean = false,
        acsRequired: Boolean = false,
    ): AppCleaner.State = AppCleaner.State(
        data = null,
        progress = null as Progress.Data?,
        isOtherUsersAvailable = otherUsers,
        isRunningAppsDetectionAvailable = runningApps,
        isInaccessibleCacheAvailable = inaccessibleCache,
        isAcsRequired = acsRequired,
    )

    private class Values(
        val includeInaccessibleEnabled: DataStoreValue<Boolean>,
        val forceStopBeforeClearing: DataStoreValue<Boolean>,
        val includeSystemAppsEnabled: DataStoreValue<Boolean>,
        val includeRunningAppsEnabled: DataStoreValue<Boolean>,
        val includeOtherUsersEnabled: DataStoreValue<Boolean>,
        val filterDefaultCachesPublicEnabled: DataStoreValue<Boolean>,
        val filterDefaultCachesPrivateEnabled: DataStoreValue<Boolean>,
        val filterCodeCacheEnabled: DataStoreValue<Boolean>,
        val filterAdvertisementEnabled: DataStoreValue<Boolean>,
        val filterBugreportingEnabled: DataStoreValue<Boolean>,
        val filterAnalyticsEnabled: DataStoreValue<Boolean>,
        val filterGameFilesEnabled: DataStoreValue<Boolean>,
        val filterHiddenCachesEnabled: DataStoreValue<Boolean>,
        val filterThumbnailsEnabled: DataStoreValue<Boolean>,
        val filterOfflineCachesEnabled: DataStoreValue<Boolean>,
        val filterRecycleBinsEnabled: DataStoreValue<Boolean>,
        val filterShortcutServiceEnabled: DataStoreValue<Boolean>,
        val filterWebviewEnabled: DataStoreValue<Boolean>,
        val filterThreemaEnabled: DataStoreValue<Boolean>,
        val filterTelegramEnabled: DataStoreValue<Boolean>,
        val filterWhatsAppBackupsEnabled: DataStoreValue<Boolean>,
        val filterWhatsAppReceivedEnabled: DataStoreValue<Boolean>,
        val filterWhatsAppSentEnabled: DataStoreValue<Boolean>,
        val filterWeChatEnabled: DataStoreValue<Boolean>,
        val filterMobileQQEnabled: DataStoreValue<Boolean>,
        val filterViberEnabled: DataStoreValue<Boolean>,
        val minCacheAgeMs: DataStoreValue<Long>,
        val minCacheSizeBytes: DataStoreValue<Long>,
    )

    private class Harness(
        val vm: AppCleanerSettingsViewModel,
        val settings: AppCleanerSettings,
        val values: Values,
    )

    private fun harness(
        appState: AppCleaner.State = appState(),
        includeInaccessible: Boolean = true,
        forceStop: Boolean = false,
        includeSystem: Boolean = false,
        includeRunning: Boolean = true,
        includeOther: Boolean = false,
        filterDefaultCachesPublic: Boolean = true,
        filterDefaultCachesPrivate: Boolean = true,
        filterCodeCache: Boolean = true,
        filterAdvertisement: Boolean = true,
        filterBugreporting: Boolean = false,
        filterAnalytics: Boolean = true,
        filterGameFiles: Boolean = false,
        filterHiddenCaches: Boolean = true,
        filterThumbnails: Boolean = true,
        filterOfflineCaches: Boolean = false,
        filterRecycleBins: Boolean = false,
        filterShortcutService: Boolean = false,
        filterWebview: Boolean = true,
        filterThreema: Boolean = false,
        filterTelegram: Boolean = false,
        filterWhatsAppBackups: Boolean = false,
        filterWhatsAppReceived: Boolean = false,
        filterWhatsAppSent: Boolean = false,
        filterWeChat: Boolean = false,
        filterMobileQQ: Boolean = false,
        filterViber: Boolean = false,
        minCacheAge: Long = AppCleanerSettings.MIN_CACHE_AGE_DEFAULT,
        minCacheSize: Long = AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT,
    ): Harness {
        val values = Values(
            includeInaccessibleEnabled = rwDataStoreValue(includeInaccessible),
            forceStopBeforeClearing = rwDataStoreValue(forceStop),
            includeSystemAppsEnabled = rwDataStoreValue(includeSystem),
            includeRunningAppsEnabled = rwDataStoreValue(includeRunning),
            includeOtherUsersEnabled = rwDataStoreValue(includeOther),
            filterDefaultCachesPublicEnabled = rwDataStoreValue(filterDefaultCachesPublic),
            filterDefaultCachesPrivateEnabled = rwDataStoreValue(filterDefaultCachesPrivate),
            filterCodeCacheEnabled = rwDataStoreValue(filterCodeCache),
            filterAdvertisementEnabled = rwDataStoreValue(filterAdvertisement),
            filterBugreportingEnabled = rwDataStoreValue(filterBugreporting),
            filterAnalyticsEnabled = rwDataStoreValue(filterAnalytics),
            filterGameFilesEnabled = rwDataStoreValue(filterGameFiles),
            filterHiddenCachesEnabled = rwDataStoreValue(filterHiddenCaches),
            filterThumbnailsEnabled = rwDataStoreValue(filterThumbnails),
            filterOfflineCachesEnabled = rwDataStoreValue(filterOfflineCaches),
            filterRecycleBinsEnabled = rwDataStoreValue(filterRecycleBins),
            filterShortcutServiceEnabled = rwDataStoreValue(filterShortcutService),
            filterWebviewEnabled = rwDataStoreValue(filterWebview),
            filterThreemaEnabled = rwDataStoreValue(filterThreema),
            filterTelegramEnabled = rwDataStoreValue(filterTelegram),
            filterWhatsAppBackupsEnabled = rwDataStoreValue(filterWhatsAppBackups),
            filterWhatsAppReceivedEnabled = rwDataStoreValue(filterWhatsAppReceived),
            filterWhatsAppSentEnabled = rwDataStoreValue(filterWhatsAppSent),
            filterWeChatEnabled = rwDataStoreValue(filterWeChat),
            filterMobileQQEnabled = rwDataStoreValue(filterMobileQQ),
            filterViberEnabled = rwDataStoreValue(filterViber),
            minCacheAgeMs = rwDataStoreValue(minCacheAge),
            minCacheSizeBytes = rwDataStoreValue(minCacheSize),
        )
        val settings = mockk<AppCleanerSettings>().apply {
            every { this@apply.includeInaccessibleEnabled } returns values.includeInaccessibleEnabled
            every { this@apply.forceStopBeforeClearing } returns values.forceStopBeforeClearing
            every { this@apply.includeSystemAppsEnabled } returns values.includeSystemAppsEnabled
            every { this@apply.includeRunningAppsEnabled } returns values.includeRunningAppsEnabled
            every { this@apply.includeOtherUsersEnabled } returns values.includeOtherUsersEnabled
            every { this@apply.filterDefaultCachesPublicEnabled } returns values.filterDefaultCachesPublicEnabled
            every { this@apply.filterDefaultCachesPrivateEnabled } returns values.filterDefaultCachesPrivateEnabled
            every { this@apply.filterCodeCacheEnabled } returns values.filterCodeCacheEnabled
            every { this@apply.filterAdvertisementEnabled } returns values.filterAdvertisementEnabled
            every { this@apply.filterBugreportingEnabled } returns values.filterBugreportingEnabled
            every { this@apply.filterAnalyticsEnabled } returns values.filterAnalyticsEnabled
            every { this@apply.filterGameFilesEnabled } returns values.filterGameFilesEnabled
            every { this@apply.filterHiddenCachesEnabled } returns values.filterHiddenCachesEnabled
            every { this@apply.filterThumbnailsEnabled } returns values.filterThumbnailsEnabled
            every { this@apply.filterOfflineCachesEnabled } returns values.filterOfflineCachesEnabled
            every { this@apply.filterRecycleBinsEnabled } returns values.filterRecycleBinsEnabled
            every { this@apply.filterShortcutServiceEnabled } returns values.filterShortcutServiceEnabled
            every { this@apply.filterWebviewEnabled } returns values.filterWebviewEnabled
            every { this@apply.filterThreemaEnabled } returns values.filterThreemaEnabled
            every { this@apply.filterTelegramEnabled } returns values.filterTelegramEnabled
            every { this@apply.filterWhatsAppBackupsEnabled } returns values.filterWhatsAppBackupsEnabled
            every { this@apply.filterWhatsAppReceivedEnabled } returns values.filterWhatsAppReceivedEnabled
            every { this@apply.filterWhatsAppSentEnabled } returns values.filterWhatsAppSentEnabled
            every { this@apply.filterWeChatEnabled } returns values.filterWeChatEnabled
            every { this@apply.filterMobileQQEnabled } returns values.filterMobileQQEnabled
            every { this@apply.filterViberEnabled } returns values.filterViberEnabled
            every { this@apply.minCacheAgeMs } returns values.minCacheAgeMs
            every { this@apply.minCacheSizeBytes } returns values.minCacheSizeBytes
        }
        val appCleaner = mockk<AppCleaner>().apply {
            every { state } returns flowOf(appState)
        }
        val vm = AppCleanerSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            appCleaner = appCleaner,
            settings = settings,
        )
        return Harness(vm, settings, values)
    }

    @Test
    fun `state passes through DataStore values seeded with production defaults`() = runTest2 {
        // The harness defaults mirror the production defaults declared in AppCleanerSettings.
        // This test verifies the VM's combine() threads each value into State; it doesn't
        // validate the production defaults themselves (those would need a real DataStore). If
        // AppCleanerSettings flips a default, update this harness alongside.
        val h = harness()

        val state = h.vm.state.first()
        state.includeSystemApps shouldBe false
        state.includeOtherUsers shouldBe false
        state.includeRunningApps shouldBe true
        state.includeInaccessible shouldBe true
        state.forceStopBeforeClearing shouldBe false
        state.minCacheSizeBytes shouldBe AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT
        state.minCacheAgeMs shouldBe AppCleanerSettings.MIN_CACHE_AGE_DEFAULT

        state.filterDefaultCachesPublic shouldBe true
        state.filterDefaultCachesPrivate shouldBe true
        state.filterCodeCache shouldBe true
        state.filterAdvertisement shouldBe true
        state.filterBugreporting shouldBe false
        state.filterAnalytics shouldBe true
        state.filterGameFiles shouldBe false
        state.filterHiddenCaches shouldBe true
        state.filterThumbnails shouldBe true
        state.filterOfflineCache shouldBe false
        state.filterRecycleBins shouldBe false
        state.filterShortcutService shouldBe false
        state.filterWebview shouldBe true

        state.filterTelegram shouldBe false
        state.filterThreema shouldBe false
        state.filterWhatsappBackups shouldBe false
        state.filterWhatsappReceived shouldBe false
        state.filterWhatsappSent shouldBe false
        state.filterWeChat shouldBe false
        state.filterMobileQQ shouldBe false
        state.filterViber shouldBe false
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val h = harness(
            includeSystem = true,
            includeOther = true,
            includeRunning = false,
            includeInaccessible = false,
            forceStop = true,
            filterBugreporting = true,
            filterGameFiles = true,
            filterOfflineCaches = true,
            filterRecycleBins = true,
            filterShortcutService = true,
            filterTelegram = true,
            filterWhatsAppBackups = true,
            filterWeChat = true,
        )

        val state = h.vm.state.first()
        state.includeSystemApps shouldBe true
        state.includeOtherUsers shouldBe true
        state.includeRunningApps shouldBe false
        state.includeInaccessible shouldBe false
        state.forceStopBeforeClearing shouldBe true
        state.filterBugreporting shouldBe true
        state.filterGameFiles shouldBe true
        state.filterOfflineCache shouldBe true
        state.filterRecycleBins shouldBe true
        state.filterShortcutService shouldBe true
        state.filterTelegram shouldBe true
        state.filterWhatsappBackups shouldBe true
        state.filterWeChat shouldBe true
    }

    @Test
    fun `state surfaces availability flags from AppCleaner state`() = runTest2 {
        val h = harness(
            appState = appState(
                otherUsers = true,
                runningApps = true,
                inaccessibleCache = true,
                acsRequired = true,
            ),
        )

        val state = h.vm.state.first()
        state.isOtherUsersAvailable shouldBe true
        state.isRunningAppsDetectionAvailable shouldBe true
        state.isInaccessibleCacheAvailable shouldBe true
        state.isAcsRequired shouldBe true
    }

    @Test
    fun `setIncludeSystemApps writes the new value to the DataStore value`() = runTest2 {
        val h = harness(includeSystem = false)

        h.vm.setIncludeSystemApps(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeSystemAppsEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeOtherUsers writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setIncludeOtherUsers(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeOtherUsersEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setIncludeInaccessible writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setIncludeInaccessible(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.includeInaccessibleEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setForceStopBeforeClearing writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setForceStopBeforeClearing(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.forceStopBeforeClearing.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setMinCacheSizeBytes writes the new long value`() = runTest2 {
        val h = harness()

        h.vm.setMinCacheSizeBytes(1024L * 1024)
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minCacheSizeBytes.update(capture(captured)) }
        captured.captured(0L) shouldBe 1024L * 1024
    }

    @Test
    fun `resetMinCacheSizeBytes writes the default`() = runTest2 {
        val h = harness()

        h.vm.resetMinCacheSizeBytes()
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minCacheSizeBytes.update(capture(captured)) }
        captured.captured(0L) shouldBe AppCleanerSettings.MIN_CACHE_SIZE_DEFAULT
    }

    @Test
    fun `setMinCacheAgeMs writes the new long value`() = runTest2 {
        val h = harness()

        h.vm.setMinCacheAgeMs(60_000L)
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minCacheAgeMs.update(capture(captured)) }
        captured.captured(0L) shouldBe 60_000L
    }

    @Test
    fun `resetMinCacheAgeMs writes the default`() = runTest2 {
        val h = harness()

        h.vm.resetMinCacheAgeMs()
        advanceUntilIdle()

        val captured = slot<(Long) -> Long?>()
        coVerify(exactly = 1) { h.values.minCacheAgeMs.update(capture(captured)) }
        captured.captured(60_000L) shouldBe AppCleanerSettings.MIN_CACHE_AGE_DEFAULT
    }

    @Test
    fun `setFilterDefaultCachesPublic writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setFilterDefaultCachesPublic(false)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterDefaultCachesPublicEnabled.update(capture(captured)) }
        captured.captured(true) shouldBe false
    }

    @Test
    fun `setFilterBugreporting writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setFilterBugreporting(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterBugreportingEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setFilterWhatsappBackups writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setFilterWhatsappBackups(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterWhatsAppBackupsEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `setFilterMobileQQ writes the new value`() = runTest2 {
        val h = harness()

        h.vm.setFilterMobileQQ(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.values.filterMobileQQEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }
}
