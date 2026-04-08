package eu.darken.sdmse.stats.core.db.converter

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.util.UUID

class ReportIdTypeConverterTest : BaseTest() {

    private val converter = ReportIdTypeConverter()

    @Test
    fun `UUID round-trip`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        converter.to(converter.from(uuid)) shouldBe uuid
    }

    @Test
    fun `stored format is canonical lowercase UUID string`() {
        val uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        converter.from(uuid) shouldBe "550e8400-e29b-41d4-a716-446655440000"
    }
}
