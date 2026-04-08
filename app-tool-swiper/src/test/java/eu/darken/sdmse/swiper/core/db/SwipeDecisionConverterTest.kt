package eu.darken.sdmse.swiper.core.db

import eu.darken.sdmse.swiper.core.SwipeDecision
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SwipeDecisionConverterTest : BaseTest() {

    private val converter = SwipeDecisionConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        SwipeDecision.UNDECIDED to "UNDECIDED",
        SwipeDecision.KEEP to "KEEP",
        SwipeDecision.DELETE to "DELETE",
        SwipeDecision.DELETED to "DELETED",
        SwipeDecision.DELETE_FAILED to "DELETE_FAILED",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        SwipeDecision.entries.size shouldBe golden.size
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
