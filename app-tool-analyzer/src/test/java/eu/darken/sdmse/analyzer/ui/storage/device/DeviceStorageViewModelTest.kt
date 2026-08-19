package eu.darken.sdmse.analyzer.ui.storage.device

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class DeviceStorageViewModelTest : BaseTest() {

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Harness(
        val vm: DeviceStorageViewModel,
        val hintDismissed: DataStoreValue<Boolean>,
    )

    private fun TestScope.harness(
        isPro: Boolean = false,
        hintDismissed: Boolean = false,
    ): Harness {
        val dismissed = rwDataStoreValue(hintDismissed)
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns MutableStateFlow(Analyzer.Data())
            every { progress } returns MutableStateFlow(null)
        }
        val settings = mockk<AnalyzerSettings>().apply {
            every { hintLowSpaceDismissed } returns dismissed
        }
        val info = mockk<UpgradeRepo.Info>().apply {
            every { this@apply.isPro } returns isPro
        }
        val vm = DeviceStorageViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            analyzer = analyzer,
            analyzerSettings = settings,
            spaceHistoryRepo = mockk<SpaceHistoryRepo>().apply {
                every { getAllHistory(any()) } returns flowOf(emptyList())
            },
            upgradeRepo = mockk<UpgradeRepo>().apply { every { upgradeInfo } returns flowOf(info) },
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, dismissed)
    }

    @Test
    fun `the hint shows for a non-Pro user who has not dismissed it`() = runTest2 {
        val h = harness(isPro = false, hintDismissed = false)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe true
    }

    @Test
    fun `the hint stays hidden for a Pro user`() = runTest2 {
        val h = harness(isPro = true, hintDismissed = false)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe false
    }

    @Test
    fun `the hint stays hidden once dismissed`() = runTest2 {
        val h = harness(isPro = false, hintDismissed = true)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe false
    }

    @Test
    fun `dismissing writes the flag through`() = runTest2 {
        val h = harness()
        advanceUntilIdle()

        h.vm.dismissLowSpaceHint()
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.hintDismissed.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `the hint's upgrade button navigates to the upgrade screen`() = runTest2 {
        val h = harness()
        val events = mutableListOf<NavEvent>()
        // Foreground scope on purpose: advanceUntilIdle() stops as soon as no FOREGROUND event is
        // queued, so a collector in backgroundScope would never be resumed to receive the emission.
        val job = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.navEvents.collect { events += it } }

        h.vm.openUpgrade()
        advanceUntilIdle()

        (events.single() as NavEvent.GoTo).destination shouldBe UpgradeRoute()
        job.cancel()
    }
}
