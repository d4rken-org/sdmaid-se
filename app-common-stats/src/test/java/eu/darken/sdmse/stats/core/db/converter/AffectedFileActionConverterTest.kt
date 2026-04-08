package eu.darken.sdmse.stats.core.db.converter

import eu.darken.sdmse.stats.core.AffectedPath
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AffectedFileActionConverterTest : BaseTest() {

    private val converter = AffectedFileActionConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        AffectedPath.Action.DELETED to "DELETED",
        AffectedPath.Action.COMPRESSED to "COMPRESSED",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        AffectedPath.Action.entries.size shouldBe golden.size
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
