package eu.darken.sdmse.widget

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
    private val widgetDataProvider = mockk<WidgetDataProvider>()

    private val renderState = MutableStateFlow<WidgetRenderState>(
        WidgetRenderState.Data(storages = emptyList(), freedBytes = 0L)
    )

    init {
        every { widgetDataProvider.renderState } returns renderState
    }

    private fun data(freed: Long = 0L, working: Boolean = false, cancellable: Boolean = false) =
        WidgetRenderState.Data(
            storages = emptyList(),
            freedBytes = freed,
            isWorking = working,
            isCancellable = working && cancellable,
        )

    private fun coordinator(scope: CoroutineScope) =
        WidgetRefreshCoordinator(scope, widgetDataProvider, widgetUpdater)

    @Test
    fun `refreshes once at start then on each distinct render state change`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        advanceUntilIdle()
        // Initial replay = the app-start refresh that re-bakes widgets after app updates.
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        renderState.value = data(freed = 1000L)
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        renderState.value = data(freed = 1000L) // unchanged → distinctUntilChanged suppresses
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        scope.cancel()
    }

    @Test
    fun `refreshes across the working-cancel cycle`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        renderState.value = data(working = true, cancellable = true) // Clean → Cancel
        advanceUntilIdle()
        coVerify(exactly = 2) { widgetUpdater.updateAll() }

        renderState.value = data(working = true, cancellable = false) // Cancel tapped → "Working…"
        advanceUntilIdle()
        coVerify(exactly = 3) { widgetUpdater.updateAll() }

        renderState.value = data() // done → Clean
        advanceUntilIdle()
        coVerify(exactly = 4) { widgetUpdater.updateAll() }

        scope.cancel()
    }

    @Test
    fun `publishes the generated picker preview once at start`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetUpdater.publishPreviews() }

        scope.cancel()
    }

    @Test
    fun `start is idempotent`() = runTest2 {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = coordinator(scope)

        coordinator.start()
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { widgetUpdater.updateAll() }

        scope.cancel()
    }
}
