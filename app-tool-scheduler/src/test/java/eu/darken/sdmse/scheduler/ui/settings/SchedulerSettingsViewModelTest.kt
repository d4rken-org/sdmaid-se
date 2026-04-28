package eu.darken.sdmse.scheduler.ui.settings

import eu.darken.sdmse.scheduler.core.SchedulerSettings
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2
import testhelpers.mockDataStoreValue

class SchedulerSettingsViewModelTest : BaseTest() {

    private fun vm(
        skipPowerSaving: Boolean = true,
        skipNotCharging: Boolean = false,
        useAutomation: Boolean = false,
    ): SchedulerSettingsViewModel {
        val settings = mockk<SchedulerSettings>().apply {
            coEvery { this@apply.skipWhenPowerSaving } returns mockDataStoreValue(skipPowerSaving)
            coEvery { this@apply.skipWhenNotCharging } returns mockDataStoreValue(skipNotCharging)
            coEvery { this@apply.useAutomation } returns mockDataStoreValue(useAutomation)
        }
        return SchedulerSettingsViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            settings = settings,
        )
    }

    @Test
    fun `state reflects default DataStore values`() = runTest2 {
        val state = vm().state.first()
        state.skipWhenPowerSaving shouldBe true
        state.skipWhenNotCharging shouldBe false
        state.useAutomation shouldBe false
    }

    @Test
    fun `state reflects non-default DataStore values`() = runTest2 {
        val state = vm(skipPowerSaving = false, skipNotCharging = true, useAutomation = true).state.first()
        state.skipWhenPowerSaving shouldBe false
        state.skipWhenNotCharging shouldBe true
        state.useAutomation shouldBe true
    }
}
