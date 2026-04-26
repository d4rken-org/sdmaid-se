package eu.darken.sdmse.scheduler.ui.manager.create

import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleItemViewModelTest : BaseTest() {

    private val existingSchedule = Schedule(
        id = "schedule-1",
        label = "Daily clean",
        hour = 8,
        minute = 30,
    )

    private data class Harness(
        val vm: ScheduleItemViewModel,
        val manager: SchedulerManager,
        val stateFlow: MutableStateFlow<SchedulerManager.State>,
    )

    private fun harness(initial: Set<Schedule>): Harness {
        val stateFlow = MutableStateFlow(SchedulerManager.State(schedules = initial))
        val manager = mockk<SchedulerManager>(relaxed = true).also { mock ->
            coEvery { mock.state } returns stateFlow
        }
        val vm = ScheduleItemViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            schedulerManager = manager,
        )
        return Harness(vm = vm, manager = manager, stateFlow = stateFlow)
    }

    @Test
    fun `save refuses to resurrect a deleted schedule`() = runTest2 {
        val (vm, manager, stateFlow) = harness(initial = setOf(existingSchedule))
        vm.setScheduleId(existingSchedule.id)
        advanceUntilIdle()

        // Schedule was deleted between sheet open and save.
        stateFlow.value = SchedulerManager.State(schedules = emptySet())

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 0) { manager.saveSchedule(any()) }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `save refuses to mutate an externally-enabled schedule`() = runTest2 {
        val (vm, manager, stateFlow) = harness(initial = setOf(existingSchedule))
        vm.setScheduleId(existingSchedule.id)
        advanceUntilIdle()

        // Schedule became enabled (scheduledAt non-null) between sheet open and save.
        stateFlow.value = SchedulerManager.State(
            schedules = setOf(
                existingSchedule.copy(scheduledAt = java.time.Instant.parse("2026-04-26T00:00:00Z")),
            ),
        )

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 0) { manager.saveSchedule(any()) }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `save creates a new schedule when initial id was unknown`() = runTest2 {
        val (vm, manager, _) = harness(initial = emptySet())
        vm.setScheduleId("new-schedule")
        advanceUntilIdle()

        vm.updateLabel("My new schedule")
        vm.updateTime(hour = 14, minute = 15)
        advanceUntilIdle()

        vm.saveSchedule()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            manager.saveSchedule(match {
                it.id == "new-schedule" && it.label == "My new schedule" && it.hour == 14 && it.minute == 15
            })
        }
        vm.navEvents.first().shouldBeInstanceOf<NavEvent.Up>()
    }

    @Test
    fun `canSave is false for blank labels`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.updateLabel("   ")
        vm.updateTime(hour = 9, minute = 0)
        advanceUntilIdle()
        vm.state.first().canSave shouldBe false
    }

    @Test
    fun `canSave is true when label hour and minute are non-blank`() = runTest2 {
        val (vm, _, _) = harness(initial = emptySet())
        vm.setScheduleId("brand-new")
        advanceUntilIdle()
        vm.updateLabel("Tidy")
        vm.updateTime(hour = 9, minute = 0)
        advanceUntilIdle()
        vm.state.first().canSave shouldBe true
    }
}
