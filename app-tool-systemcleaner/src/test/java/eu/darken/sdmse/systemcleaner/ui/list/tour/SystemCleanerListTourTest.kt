package eu.darken.sdmse.systemcleaner.ui.list.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SystemCleanerListTourTest : BaseTest() {

    @Test
    fun `definition is a centerless intro followed by the filter-row step`() {
        val def = SystemCleanerListTour.definition()
        def.id shouldBe TourId("tour.systemcleaner.list")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro", "filter")
        def.steps.map { it.targetId } shouldBe listOf(null, SystemCleanerListTour.FILTER_ROW_TARGET)
    }
}
