package eu.darken.sdmse.common.coil

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest


class CoilTempFilesTest : BaseTest() {

    @Test fun `legacy cleanup tmp file path matching`() {
        CoilTempFiles.NAME_REGEX.matches("tmp9050482114132890229.tmp") shouldBe true
        CoilTempFiles.NAME_REGEX.matches("9050482114132890229.tmp") shouldBe false
        CoilTempFiles.NAME_REGEX.matches("tmp9050482114132890229.other") shouldBe false
        CoilTempFiles.NAME_REGEX.matches("tmp90504229.tmp") shouldBe true
    }
}