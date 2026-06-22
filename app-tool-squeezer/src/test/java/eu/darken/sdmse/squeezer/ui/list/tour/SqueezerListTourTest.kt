package eu.darken.sdmse.squeezer.ui.list.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SqueezerListTourTest : BaseTest() {

    @Test
    fun `definition is a centerless intro followed by the compress-all step`() {
        val def = SqueezerListTour.definition()
        def.id shouldBe TourId("tour.squeezer.list")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro", "compress")
        def.steps.map { it.targetId } shouldBe listOf(null, SqueezerListTour.COMPRESS_ALL_TARGET)
    }
}
