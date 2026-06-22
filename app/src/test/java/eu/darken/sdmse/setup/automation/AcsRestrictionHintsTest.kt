package eu.darken.sdmse.setup.automation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AcsRestrictionHintsTest : BaseTest() {

    @Test
    fun `advanced protection hint requires AAPM, consent and a non-running service`() {
        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = true,
            isServiceRunning = false,
            appOpsRestrictionApplies = false,
        ).showAdvancedProtectionHint shouldBe true
    }

    @Test
    fun `no advanced protection hint without consent`() {
        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = null,
            isServiceRunning = false,
            appOpsRestrictionApplies = false,
        ).showAdvancedProtectionHint shouldBe false

        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = false,
            isServiceRunning = false,
            appOpsRestrictionApplies = false,
        ).showAdvancedProtectionHint shouldBe false
    }

    @Test
    fun `no advanced protection hint while the service is running`() {
        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = true,
            isServiceRunning = true,
            appOpsRestrictionApplies = false,
        ).showAdvancedProtectionHint shouldBe false
    }

    @Test
    fun `no advanced protection hint when AAPM is off`() {
        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = false,
            hasConsent = true,
            isServiceRunning = false,
            appOpsRestrictionApplies = false,
        ).showAdvancedProtectionHint shouldBe false
    }

    @Test
    fun `advanced protection takes precedence over the sideload appops hint`() {
        val hints = decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = true,
            isServiceRunning = false,
            appOpsRestrictionApplies = true,
        )
        hints.showAdvancedProtectionHint shouldBe true
        hints.showAppOpsRestrictionHint shouldBe false
    }

    @Test
    fun `AAPM suppresses the appops hint even when the advanced hint itself is not shown`() {
        // Privileged user without consent: advanced hint off, but appops must still be suppressed
        // because its remedy cannot lift an Advanced Protection block.
        val hints = decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = true,
            hasConsent = null,
            isServiceRunning = false,
            appOpsRestrictionApplies = true,
        )
        hints.showAdvancedProtectionHint shouldBe false
        hints.showAppOpsRestrictionHint shouldBe false
    }

    @Test
    fun `appops hint shows when applicable and AAPM is off`() {
        decideAcsRestrictionHints(
            advancedProtectionBlocksAcs = false,
            hasConsent = true,
            isServiceRunning = false,
            appOpsRestrictionApplies = true,
        ).showAppOpsRestrictionHint shouldBe true
    }
}
