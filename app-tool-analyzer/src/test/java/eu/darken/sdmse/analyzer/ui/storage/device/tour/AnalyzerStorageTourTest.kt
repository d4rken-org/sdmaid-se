package eu.darken.sdmse.analyzer.ui.storage.device.tour

import eu.darken.sdmse.common.compose.tour.TourId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AnalyzerStorageTourTest : BaseTest() {

    @Test
    fun `definition has a centerless intro followed by the storage-card step`() {
        val def = AnalyzerStorageTour.definition()
        def.id shouldBe TourId("tour.analyzer.storage")
        def.clickProtection shouldBe true
        def.steps.map { it.stepId } shouldBe listOf("intro", "storage")
        def.steps.map { it.targetId } shouldBe listOf(null, AnalyzerStorageTour.STORAGE_CARD_TARGET)
    }

    @Test
    fun `the anchored step is the storage card`() {
        val def = AnalyzerStorageTour.definition()
        // Step 1 (after the centerless intro) must anchor an always-present element: a finished scan
        // always yields at least one storage card.
        def.steps.last().targetId shouldBe AnalyzerStorageTour.STORAGE_CARD_TARGET
    }
}
