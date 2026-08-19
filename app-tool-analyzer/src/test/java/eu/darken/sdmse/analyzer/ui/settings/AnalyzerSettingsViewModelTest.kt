package eu.darken.sdmse.analyzer.ui.settings

import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.LowStorage
import eu.darken.sdmse.stats.core.SpaceTracker
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AnalyzerSettingsViewModelTest : BaseTest() {

    private val capacity = 128_000_000_000L

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Harness(
        val vm: AnalyzerSettingsViewModel,
        val threshold: DataStoreValue<Long?>,
        val notificationEnabled: DataStoreValue<Boolean>,
    )

    private fun TestScope.harness(
        customThresholdBytes: Long? = null,
        primaryStorage: SpaceTracker.StorageSnapshot? = SpaceTracker.StorageSnapshot(
            storageId = "primary",
            spaceFree = 50_000_000_000L,
            spaceCapacity = 128_000_000_000L,
        ),
        notificationEnabled: Boolean = false,
        isPro: Boolean = false,
    ): Harness {
        val threshold = rwDataStoreValue(customThresholdBytes)
        val notification = rwDataStoreValue(notificationEnabled)
        val settings = mockk<AnalyzerSettings>().apply {
            every { lowStorageThresholdBytes } returns threshold
            every { lowSpaceNotificationEnabled } returns notification
        }
        val spaceTracker = mockk<SpaceTracker>(relaxed = true).apply {
            coEvery { readPrimaryStorage() } returns primaryStorage
        }
        val info = mockk<UpgradeRepo.Info>().apply {
            every { this@apply.isPro } returns isPro
        }
        val upgradeRepo = mockk<UpgradeRepo>().apply {
            every { upgradeInfo } returns flowOf(info)
        }
        val vm = AnalyzerSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
            spaceTracker = spaceTracker,
            upgradeRepo = upgradeRepo,
        )
        // safeStateIn is WhileSubscribed: without a live subscriber `state.value` stays at the
        // initial State() and the Pro guard in setNotificationEnabled would never see isPro.
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, threshold, notification)
    }

    // ─────────────────────────── state ───────────────────────────

    @Test
    fun `the default is automatic`() = runTest2 {
        val h = harness(customThresholdBytes = null)

        val state = h.vm.state.first()
        state.customThresholdBytes shouldBe null
        state.primaryCapacityBytes shouldBe capacity
        state.effectiveThresholdBytes shouldBe LowStorage.resolveThreshold(capacity, null)
    }

    @Test
    fun `a stored custom value surfaces and drives the effective threshold`() = runTest2 {
        val h = harness(customThresholdBytes = 10_000_000_000L)

        val state = h.vm.state.first()
        state.customThresholdBytes shouldBe 10_000_000_000L
        state.primaryCapacityBytes shouldBe capacity
        state.effectiveThresholdBytes shouldBe 10_000_000_000L
    }

    @Test
    fun `a zero-capacity primary reading leaves the capacity unknown`() = runTest2 {
        // readPrimaryStorage() can return a non-null 0/0 reading; accepting it would render
        // "Automatic (currently 0 B)".
        val h = harness(
            primaryStorage = SpaceTracker.StorageSnapshot(
                storageId = "primary",
                spaceFree = 0L,
                spaceCapacity = 0L,
            ),
        )

        val state = h.vm.state.first()
        state.primaryCapacityBytes shouldBe null
        state.effectiveThresholdBytes shouldBe null
    }

    @Test
    fun `a missing primary reading leaves the capacity unknown`() = runTest2 {
        val h = harness(primaryStorage = null)

        val state = h.vm.state.first()
        state.primaryCapacityBytes shouldBe null
        state.effectiveThresholdBytes shouldBe null
    }

    // ─────────────────────────── setters ───────────────────────────

    @Test
    fun `setThreshold writes a custom value through`() = runTest2 {
        val h = harness(customThresholdBytes = null)

        h.vm.setThreshold(10_000_000_000L)
        advanceUntilIdle()

        val captured = slot<(Long?) -> Long?>()
        coVerify(exactly = 1) { h.threshold.update(capture(captured)) }
        captured.captured(null) shouldBe 10_000_000_000L
    }

    @Test
    fun `setThreshold null writes automatic through`() = runTest2 {
        val h = harness(customThresholdBytes = 10_000_000_000L)

        h.vm.setThreshold(null)
        advanceUntilIdle()

        val captured = slot<(Long?) -> Long?>()
        coVerify(exactly = 1) { h.threshold.update(capture(captured)) }
        captured.captured(10_000_000_000L) shouldBe null
    }

    // ─────────────────────────── low space warning ───────────────────────────

    @Test
    fun `the stored toggle and the Pro state surface`() = runTest2 {
        val h = harness(notificationEnabled = true, isPro = true)
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.notificationEnabled shouldBe true
        state.isPro shouldBe true
    }

    @Test
    fun `a non-Pro user sees the stored value but the row renders unchecked`() = runTest2 {
        // The screen renders `isPro && notificationEnabled`, so the stored value stays intact.
        val h = harness(notificationEnabled = true, isPro = false)
        advanceUntilIdle()

        val state = h.vm.state.first()
        state.notificationEnabled shouldBe true
        state.isPro shouldBe false
    }

    @Test
    fun `a Pro user can enable the warning`() = runTest2 {
        val h = harness(notificationEnabled = false, isPro = true)
        advanceUntilIdle()

        h.vm.setNotificationEnabled(true)
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.notificationEnabled.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `a non-Pro user cannot enable the warning`() = runTest2 {
        val h = harness(notificationEnabled = false, isPro = false)
        advanceUntilIdle()

        h.vm.setNotificationEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 0) { h.notificationEnabled.update(any()) }
    }
}
