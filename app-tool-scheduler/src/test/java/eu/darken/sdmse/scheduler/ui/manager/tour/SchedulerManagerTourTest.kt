package eu.darken.sdmse.scheduler.ui.manager.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SchedulerManagerTourTest : BaseTest() {

    @Test
    fun `definition is a centerless intro followed by the add-FAB step`() {
        val def = SchedulerManagerTour.definition()
        def.id shouldBe TourId("tour.scheduler.manager")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro", "create")
        // Only the FAB is anchored — a fresh manager screen has no schedule rows to point at.
        def.steps.map { it.targetId } shouldBe listOf(null, SchedulerManagerTour.ADD_TARGET)
    }
}
