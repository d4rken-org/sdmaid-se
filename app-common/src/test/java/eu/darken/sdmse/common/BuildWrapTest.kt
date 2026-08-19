package eu.darken.sdmse.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class BuildWrapTest : BaseTest() {

    @Test fun `absent build values normalize to null`() {
        // Build.UNKNOWN is the documented sentinel for "the vendor did not set this", and some
        // vendors leave the property blank instead. Neither is usable, so both must read as absent,
        // otherwise a SoC check would treat the literal string "unknown" as a vendor name.
        null.normalizeBuildValue() shouldBe null
        "".normalizeBuildValue() shouldBe null
        "   ".normalizeBuildValue() shouldBe null
        "unknown".normalizeBuildValue() shouldBe null
        "UNKNOWN".normalizeBuildValue() shouldBe null
        "Unknown".normalizeBuildValue() shouldBe null
    }

    @Test fun `real build values survive, trimmed`() {
        "Mediatek".normalizeBuildValue() shouldBe "Mediatek"
        "  Qualcomm  ".normalizeBuildValue() shouldBe "Qualcomm"
        "MT8781V/NA".normalizeBuildValue() shouldBe "MT8781V/NA"
        // Only the exact sentinel is dropped, not anything containing it.
        "unknown-soc".normalizeBuildValue() shouldBe "unknown-soc"
    }
}
