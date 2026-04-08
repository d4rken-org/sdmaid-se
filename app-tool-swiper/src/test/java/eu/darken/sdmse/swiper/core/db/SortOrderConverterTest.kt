package eu.darken.sdmse.swiper.core.db

import eu.darken.sdmse.swiper.core.SortOrder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SortOrderConverterTest : BaseTest() {

    private val converter = SortOrderConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        SortOrder.OLDEST_FIRST to "OLDEST_FIRST",
        SortOrder.NEWEST_FIRST to "NEWEST_FIRST",
        SortOrder.NAME_ASC to "NAME_ASC",
        SortOrder.SIZE_DESC to "SIZE_DESC",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        SortOrder.entries.size shouldBe golden.size
    }

    @Test
    fun `enum to string matches golden`() {
        golden.forEach { (value, expected) -> converter.toValue(value) shouldBe expected }
    }

    @Test
    fun `string to enum matches golden`() {
        golden.forEach { (expected, stored) -> converter.fromValue(stored) shouldBe expected }
    }

    @Test
    fun `unknown stored value throws`() {
        shouldThrow<IllegalArgumentException> { converter.fromValue("NOT_A_REAL_VALUE") }
    }
}
