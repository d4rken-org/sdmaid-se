package eu.darken.sdmse.swiper.core.db

import eu.darken.sdmse.swiper.core.SessionState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SessionStateConverterTest : BaseTest() {

    private val converter = SessionStateConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        SessionState.CREATED to "CREATED",
        SessionState.READY to "READY",
        SessionState.COMPLETED to "COMPLETED",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        SessionState.entries.size shouldBe golden.size
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
