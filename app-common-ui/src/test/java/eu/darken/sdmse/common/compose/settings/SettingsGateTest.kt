package eu.darken.sdmse.common.compose.settings

import eu.darken.sdmse.common.access.AccessState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class SettingsGateTest : BaseTest() {

    @Test
    fun `any active method makes the feature available`() {
        privilegedGateState(listOf(AccessState.Active)) shouldBe FeatureGateState.AVAILABLE
        // OR-semantics: one active unlocker wins even if another is blocked.
        privilegedGateState(listOf(AccessState.Unavailable, AccessState.Active)) shouldBe FeatureGateState.AVAILABLE
    }

    @Test
    fun `undecided or checking keeps the feature actionable (setup)`() {
        privilegedGateState(listOf(AccessState.Undecided)) shouldBe FeatureGateState.SETUP
        privilegedGateState(listOf(AccessState.Checking)) shouldBe FeatureGateState.SETUP
        // A still-open path on any unlocker keeps the whole feature actionable.
        privilegedGateState(listOf(AccessState.Unavailable, AccessState.Undecided)) shouldBe FeatureGateState.SETUP
    }

    @Test
    fun `all methods settled without access is a blocked dead-end`() {
        privilegedGateState(listOf(AccessState.Unavailable)) shouldBe FeatureGateState.BLOCKED
        privilegedGateState(listOf(AccessState.Declined)) shouldBe FeatureGateState.BLOCKED
        privilegedGateState(listOf(AccessState.Unavailable, AccessState.Declined)) shouldBe FeatureGateState.BLOCKED
    }

    @Test
    fun `available flag is authoritative for AVAILABLE and ignores stale Active`() {
        // available=true wins regardless of states.
        privilegedGateState(available = true, states = listOf(AccessState.Unavailable)) shouldBe FeatureGateState.AVAILABLE
        // available=false: a stale Active must NOT make it available; falls through to SETUP/BLOCKED.
        privilegedGateState(available = false, states = listOf(AccessState.Active)) shouldBe FeatureGateState.SETUP
        privilegedGateState(available = false, states = listOf(AccessState.Undecided)) shouldBe FeatureGateState.SETUP
        privilegedGateState(available = false, states = listOf(AccessState.Unavailable)) shouldBe FeatureGateState.BLOCKED
        privilegedGateState(available = false, states = listOf(AccessState.Declined, AccessState.Unavailable)) shouldBe FeatureGateState.BLOCKED
        privilegedGateState(available = false, states = listOf(AccessState.Unavailable, AccessState.Undecided)) shouldBe FeatureGateState.SETUP
    }

    @Test
    fun `gate state maps to the matching setting gate`() {
        FeatureGateState.AVAILABLE.toSettingGate() shouldBe null
        FeatureGateState.SETUP.toSettingGate() shouldBe SettingGate.SetupRequired
        FeatureGateState.BLOCKED.toSettingGate() shouldBe SettingGate.Unavailable
    }
}
