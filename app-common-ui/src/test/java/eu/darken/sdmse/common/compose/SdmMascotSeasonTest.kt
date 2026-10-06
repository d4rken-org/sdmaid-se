package eu.darken.sdmse.common.compose

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.LocalDate

class SdmMascotSeasonTest : BaseTest() {

    private fun seasonOn(month: Int, day: Int, year: Int = 2026) = mascotSeasonOf(LocalDate.of(year, month, day))

    @Test
    fun `halloween runs from october 24th to 31st`() {
        seasonOn(10, 23) shouldBe null
        seasonOn(10, 24) shouldBe MascotSeason.HALLOWEEN
        seasonOn(10, 31) shouldBe MascotSeason.HALLOWEEN
        seasonOn(11, 1) shouldBe null
    }

    @Test
    fun `christmas runs from december 21st to 28th, new year wins from the 29th`() {
        seasonOn(12, 20) shouldBe null
        seasonOn(12, 21) shouldBe MascotSeason.CHRISTMAS
        seasonOn(12, 28) shouldBe MascotSeason.CHRISTMAS
        seasonOn(12, 29) shouldBe MascotSeason.NEW_YEAR
    }

    @Test
    fun `new year spans the turn of the year`() {
        seasonOn(12, 31) shouldBe MascotSeason.NEW_YEAR
        seasonOn(1, 2, year = 2027) shouldBe MascotSeason.NEW_YEAR
        seasonOn(1, 3, year = 2027) shouldBe null
    }
}
