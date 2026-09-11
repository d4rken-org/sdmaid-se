package eu.darken.sdmse.appcleaner.core.automation.specs.aosp

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Covers which manufacturers AOSPSpecs opens the DPAD focus-traversal fallback for.
 *
 * BuildWrap.MANUFACTOR is whatever the ROM wrote into android.os.Build, so casing is not
 * guaranteed: the Motorola report that added it here reads "motorola", Google devices read
 * "Google".
 */
class DpadFallbackGateTest : BaseTest() {

    @Test
    fun `motorola gets the fallback regardless of casing`() {
        supportsDpadFallback("motorola") shouldBe true
        supportsDpadFallback("Motorola") shouldBe true
        supportsDpadFallback("MOTOROLA") shouldBe true
    }

    @Test
    fun `google keeps the fallback it already had`() {
        supportsDpadFallback("Google") shouldBe true
        supportsDpadFallback("google") shouldBe true
    }

    @Test
    fun `manufacturers without the withheld-row behaviour stay on the label matcher`() {
        supportsDpadFallback("samsung") shouldBe false
        supportsDpadFallback("Xiaomi") shouldBe false
    }

    @Test
    fun `an unset manufacturer does not open the fallback`() {
        supportsDpadFallback("") shouldBe false
    }
}
