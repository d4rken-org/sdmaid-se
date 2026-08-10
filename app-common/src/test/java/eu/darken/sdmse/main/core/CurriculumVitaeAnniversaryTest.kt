package eu.darken.sdmse.main.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.LocalDate

class CurriculumVitaeAnniversaryTest : BaseTest() {

    private fun occurrence(install: LocalDate, today: LocalDate) =
        CurriculumVitae.anniversaryOccurrenceOf(install, today)

    @Test fun `the install year itself is not an anniversary`() {
        occurrence(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20)) shouldBe null
    }

    @Test fun `the first anniversary starts on the nominal date`() {
        occurrence(LocalDate.of(2024, 8, 10), LocalDate.of(2025, 8, 10)) shouldBe CurriculumVitae.AnniversaryOccurrence(
            ordinal = 1,
            nominalYear = 2025,
            date = LocalDate.of(2025, 8, 10),
        )
    }

    @Test fun `the window covers the nominal date plus 14 days`() {
        val install = LocalDate.of(2024, 8, 10)

        occurrence(install, LocalDate.of(2025, 8, 24))?.ordinal shouldBe 1
        occurrence(install, LocalDate.of(2025, 8, 25)) shouldBe null
        occurrence(install, LocalDate.of(2025, 8, 9)) shouldBe null
    }

    @Test fun `a late-December window keeps the same occurrence into January`() {
        val install = LocalDate.of(2024, 12, 20)

        occurrence(install, LocalDate.of(2026, 12, 25)) shouldBe CurriculumVitae.AnniversaryOccurrence(
            ordinal = 2,
            nominalYear = 2026,
            date = LocalDate.of(2026, 12, 20),
        )
        occurrence(install, LocalDate.of(2027, 1, 2)) shouldBe CurriculumVitae.AnniversaryOccurrence(
            ordinal = 2,
            nominalYear = 2026,
            date = LocalDate.of(2026, 12, 20),
        )
    }

    @Test fun `a Feb-29 install celebrates on Feb 28 in non-leap years`() {
        val install = LocalDate.of(2024, 2, 29)

        occurrence(install, LocalDate.of(2026, 2, 28)) shouldBe CurriculumVitae.AnniversaryOccurrence(
            ordinal = 2,
            nominalYear = 2026,
            date = LocalDate.of(2026, 2, 28),
        )
        // Still inside the 14 day window that started on Feb 28.
        occurrence(install, LocalDate.of(2026, 3, 14))?.ordinal shouldBe 2
        occurrence(install, LocalDate.of(2026, 3, 15)) shouldBe null
    }

    @Test fun `a Feb-29 install celebrates on Feb 29 in leap years`() {
        occurrence(
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2028, 2, 29),
        ) shouldBe CurriculumVitae.AnniversaryOccurrence(
            ordinal = 4,
            nominalYear = 2028,
            date = LocalDate.of(2028, 2, 29),
        )
    }

    @Test fun `the ordinal counts full years since install`() {
        occurrence(LocalDate.of(2020, 8, 10), LocalDate.of(2026, 8, 15))?.ordinal shouldBe 6
    }
}
