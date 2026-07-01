package eu.darken.sdmse.widget

import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.stats.core.StatsSettings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2

class WidgetRefreshCoordinatorTest : BaseTest() {

    private val widgetUpdater = mockk<WidgetUpdater>(relaxed = true)
    private val statsSettings = mockk<StatsSettings>()

    private fun bindFreed(flow: MutableStateFlow<Long>) {
        val value = mockk<DataStoreValue<Long>>()
        every { value.flow } returns flow
        every { statsSettings.totalSpaceFreed } returns value
    }

    @Test
    fun `refreshes widgets on each distinct freed change`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val freed = MutableStateFlow(0L)
        bindFreed(freed)
        val coordinator = WidgetRefreshCoordinator(scope, statsSettings, widgetUpdater)

        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() } // initial replay

        freed.value = 1000L
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        freed.value = 1000L // unchanged → distinctUntilChanged suppresses
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        scope.cancel()
    }

    @Test
    fun `start is idempotent`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val freed = MutableStateFlow(0L)
        bindFreed(freed)
        val coordinator = WidgetRefreshCoordinator(scope, statsSettings, widgetUpdater)

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        scope.cancel()
    }
}
