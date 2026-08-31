package eu.darken.sdmse.scheduler.ui.manager

import android.content.Context
import eu.darken.sdmse.common.BatteryHelper
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.main.core.GeneralSettings
import eu.darken.sdmse.main.core.taskmanager.AcsScheduleRisk
import eu.darken.sdmse.main.core.taskmanager.SchedulerAppCleanerAdvisor
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import eu.darken.sdmse.scheduler.core.SchedulerSettings
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
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
import testhelpers.mockDataStoreValue

class SchedulerManagerViewModelTest : BaseTest() {

    private class Harness(
        val vm: SchedulerManagerViewModel,
        val useRoot: MutableStateFlow<Boolean>,
        val useAdb: MutableStateFlow<Boolean>,
        val schedules: MutableStateFlow<SchedulerManager.State>,
    )

    private fun TestScope.harness(
        useRoot: Boolean = false,
        useAdb: Boolean = false,
    ): Harness {
        val rootFlow = MutableStateFlow(useRoot)
        val adbFlow = MutableStateFlow(useAdb)
        // A seeded schedule keeps the init block's one-shot default-entry creation from firing and
        // mutating what the test is observing.
        val schedules = MutableStateFlow(
            SchedulerManager.State(schedules = setOf(Schedule(id = "test-schedule", label = "Test"))),
        )
        val schedulerManager = mockk<SchedulerManager>(relaxed = true).apply {
            every { state } returns schedules
        }
        val settings = mockk<SchedulerSettings>().apply {
            every { useAutomation } returns mockDataStoreValue(false)
            every { createdDefaultEntry } returns mockDataStoreValue(true)
            every { hintBatteryDismissed } returns mockDataStoreValue(false)
            every { hintAcsScreenLockedDismissed } returns mockDataStoreValue(false)
        }
        val generalSettings = mockk<GeneralSettings>().apply {
            every { hasAcsConsent } returns mockDataStoreValue(true)
        }
        val vm = SchedulerManagerViewModel(
            context = mockk<Context>(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(),
            schedulerManager = schedulerManager,
            settings = settings,
            generalSettings = generalSettings,
            appCleanerAdvisor = mockk<SchedulerAppCleanerAdvisor>().apply {
                every { acsScheduleRisk } returns flowOf(AcsScheduleRisk.NONE)
            },
            upgradeRepo = mockk<UpgradeRepo>(relaxed = true),
            rootManager = mockk<RootManager>().apply { every { this@apply.useRoot } returns rootFlow },
            adbManager = mockk<AdbManager>().apply { every { this@apply.useAdb } returns adbFlow },
            batteryHelper = mockk<BatteryHelper>().apply {
                every { isIgnoringBatteryOptimizations } returns flowOf(false)
            },
            webpageTool = mockk<WebpageTool>(relaxed = true),
            shellOps = mockk<ShellOps>(relaxed = true),
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, rootFlow, adbFlow, schedules)
    }

    @Test
    fun `ADB becoming available reveals the commands section without a schedule change`() = runTest2 {
        val h = harness()
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe false

        h.useAdb.value = true
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe true
    }

    @Test
    fun `root becoming available reveals the commands section without a schedule change`() = runTest2 {
        val h = harness()
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe false

        h.useRoot.value = true
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe true
    }

    @Test
    fun `revoked ADB access hides the commands section again`() = runTest2 {
        val h = harness(useAdb = true)
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe true

        h.useAdb.value = false
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe false
    }

    @Test
    fun `revoked root access hides the commands section again`() = runTest2 {
        val h = harness(useRoot = true)
        advanceUntilIdle()
        h.vm.state.first().showCommands shouldBe true

        h.useRoot.value = false
        advanceUntilIdle()

        h.vm.state.first().showCommands shouldBe false
    }
}
