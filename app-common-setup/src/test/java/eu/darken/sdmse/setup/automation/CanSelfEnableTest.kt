package eu.darken.sdmse.setup.automation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class CanSelfEnableTest : BaseTest() {

    @Test
    fun `can self enable with secure settings and no advanced protection`() {
        decideCanSelfEnable(
            hasSecureSettings = true,
            advancedProtectionBlocksAcs = false,
        ) shouldBe true
    }

    @Test
    fun `advanced protection blocks self enabling despite secure settings`() {
        decideCanSelfEnable(
            hasSecureSettings = true,
            advancedProtectionBlocksAcs = true,
        ) shouldBe false
    }

    @Test
    fun `no self enabling without secure settings`() {
        decideCanSelfEnable(
            hasSecureSettings = false,
            advancedProtectionBlocksAcs = false,
        ) shouldBe false
    }

    @Test
    fun `no self enabling without secure settings and with advanced protection`() {
        decideCanSelfEnable(
            hasSecureSettings = false,
            advancedProtectionBlocksAcs = true,
        ) shouldBe false
    }
}
