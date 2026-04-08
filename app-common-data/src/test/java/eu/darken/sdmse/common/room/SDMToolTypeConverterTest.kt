package eu.darken.sdmse.common.room

import eu.darken.sdmse.main.core.SDMTool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SDMToolTypeConverterTest : BaseTest() {

    private val converter = SDMToolTypeConverter()

    // Changing any string below is a breaking DB schema change.
    // See "Intentional rename workflow" before editing.
    private val golden = mapOf(
        SDMTool.Type.CORPSEFINDER to "CORPSEFINDER",
        SDMTool.Type.SYSTEMCLEANER to "SYSTEMCLEANER",
        SDMTool.Type.APPCLEANER to "APPCLEANER",
        SDMTool.Type.APPCONTROL to "APPCONTROL",
        SDMTool.Type.ANALYZER to "ANALYZER",
        SDMTool.Type.DEDUPLICATOR to "DEDUPLICATOR",
        SDMTool.Type.SQUEEZER to "SQUEEZER",
        SDMTool.Type.SWIPER to "SWIPER",
    )

    @Test
    fun `golden mappings cover every enum value`() {
        SDMTool.Type.entries.size shouldBe golden.size
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
