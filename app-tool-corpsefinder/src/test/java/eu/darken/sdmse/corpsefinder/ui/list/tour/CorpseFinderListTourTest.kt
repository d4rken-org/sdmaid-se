package eu.darken.sdmse.corpsefinder.ui.list.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CorpseFinderListTourTest : BaseTest() {

    @Test
    fun `definition is a centerless intro followed by the corpse-row step`() {
        val def = CorpseFinderListTour.definition()
        def.id shouldBe TourId("tour.corpsefinder.list")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro", "row")
        def.steps.map { it.targetId } shouldBe listOf(null, CorpseFinderListTour.CORPSE_ROW_TARGET)
    }
}
