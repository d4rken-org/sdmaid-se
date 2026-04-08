package eu.darken.sdmse.common.room

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class InstantConverterTest : BaseTest() {

    private val converter = InstantConverter()

    @Test
    fun `null round-trips both directions`() {
        converter.toValue(null).shouldBeNull()
        converter.fromValue(null).shouldBeNull()
    }

    @Test
    fun `epoch round-trip`() {
        val epoch = Instant.EPOCH
        converter.fromValue(converter.toValue(epoch)) shouldBe epoch
    }

    @Test
    fun `recent timestamp round-trip`() {
        val now = Instant.ofEpochMilli(1_712_000_000_000)
        converter.fromValue(converter.toValue(now)) shouldBe now
    }

    @Test
    fun `far future timestamp round-trip`() {
        val future = Instant.ofEpochMilli(4_102_444_800_000) // year 2100
        converter.fromValue(converter.toValue(future)) shouldBe future
    }

    @Test
    fun `stored format is epoch milliseconds`() {
        converter.toValue(Instant.ofEpochMilli(1_712_000_000_000)) shouldBe 1_712_000_000_000
    }
}
