package eu.darken.sdmse.setup.automation

import eu.darken.sdmse.common.device.RomType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class XiaomiAutostartHintTest : BaseTest() {

    @Test
    fun `hint shows on HyperOS`() {
        needsXiaomiAutostartHint(
            romType = RomType.HYPEROS,
            canSelfEnable = false,
        ) shouldBe true
    }

    @Test
    fun `hint shows on MIUI`() {
        needsXiaomiAutostartHint(
            romType = RomType.MIUI,
            canSelfEnable = false,
        ) shouldBe true
    }

    @Test
    fun `no hint when the service can be self enabled`() {
        needsXiaomiAutostartHint(
            romType = RomType.HYPEROS,
            canSelfEnable = true,
        ) shouldBe false

        needsXiaomiAutostartHint(
            romType = RomType.MIUI,
            canSelfEnable = true,
        ) shouldBe false
    }

    @Test
    fun `no hint on any other ROM type`() {
        RomType.entries
            .filter { it != RomType.MIUI && it != RomType.HYPEROS }
            .forEach { romType ->
                needsXiaomiAutostartHint(
                    romType = romType,
                    canSelfEnable = false,
                ) shouldBe false
            }
    }
}
