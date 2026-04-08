package eu.darken.sdmse.stats.core.db.converter

import eu.darken.sdmse.stats.core.Report
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ReportStatusConverterTest : BaseTest() {

    private val converter = ReportStatusConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        Report.Status.SUCCESS to "SUCCESS",
        Report.Status.PARTIAL_SUCCESS to "PARTIAL_SUCCESS",
        Report.Status.FAILURE to "FAILURE",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        Report.Status.entries.size shouldBe golden.size
    }

    @Test
    fun `enum to string matches golden`() {
        golden.forEach { (value, expected) -> converter.from(value) shouldBe expected }
    }

    @Test
    fun `string to enum matches golden`() {
        golden.forEach { (expected, stored) -> converter.to(stored) shouldBe expected }
    }

    @Test
    fun `unknown stored value throws`() {
        shouldThrow<IllegalArgumentException> { converter.to("NOT_A_REAL_VALUE") }
    }
}
