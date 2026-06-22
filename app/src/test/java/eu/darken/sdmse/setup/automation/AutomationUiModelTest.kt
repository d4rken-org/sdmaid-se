package eu.darken.sdmse.setup.automation

import android.content.Intent
import eu.darken.sdmse.R
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AutomationUiModelTest : BaseTest() {

    private fun result(
        hasConsent: Boolean? = null,
        canSelfEnable: Boolean = false,
        isServiceEnabled: Boolean = false,
        isServiceRunning: Boolean = false,
        isShortcutOrButtonEnabled: Boolean = false,
        needsXiaomiAutostart: Boolean = false,
        showAppOpsRestrictionHint: Boolean = false,
    ) = AutomationSetupModule.Result(
        isNotRequired = false,
        hasConsent = hasConsent,
        canSelfEnable = canSelfEnable,
        isServiceEnabled = isServiceEnabled,
        isServiceRunning = isServiceRunning,
        isShortcutOrButtonEnabled = isShortcutOrButtonEnabled,
        needsXiaomiAutostart = needsXiaomiAutostart,
        liftRestrictionsIntent = Intent(),
        showAppOpsRestrictionHint = showAppOpsRestrictionHint,
        settingsIntent = Intent(),
    )

    @Test
    fun `no consent hides state chips and shows positive allow action`() {
        val ui = result(hasConsent = null).toUiModel()
        ui.enabledState shouldBe null
        ui.runningState shouldBe null
        ui.showAllowAction shouldBe true
        ui.allowActionText shouldBe AutomationUiModel.AllowActionText.CONSENT_POSITIVE
        ui.showDisallowAction shouldBe true
        ui.showDisallowHint shouldBe true
    }

    @Test
    fun `consent denied hides allow action and disallow controls`() {
        val ui = result(hasConsent = false).toUiModel()
        ui.enabledState shouldBe null
        ui.runningState shouldBe null
        ui.showAllowAction shouldBe true
        ui.showDisallowAction shouldBe false
        ui.showDisallowHint shouldBe false
    }

    @Test
    fun `fully enabled and running reports primary chips and no allow action`() {
        val ui = result(
            hasConsent = true,
            isServiceEnabled = true,
            isServiceRunning = true,
        ).toUiModel()
        ui.enabledState?.tint shouldBe AutomationUiModel.ChipTint.PRIMARY
        ui.enabledState?.textRes shouldBe R.string.setup_acs_state_enabled
        ui.runningState?.tint shouldBe AutomationUiModel.ChipTint.PRIMARY
        ui.runningState?.textRes shouldBe R.string.setup_acs_state_running
        ui.showAllowAction shouldBe false
        ui.showRunningStateHint shouldBe false
        ui.showDisallowAction shouldBe true
    }

    @Test
    fun `can self enable shows secondary chip and hides allow action`() {
        val ui = result(
            hasConsent = true,
            canSelfEnable = true,
            isServiceEnabled = false,
        ).toUiModel()
        ui.enabledState?.tint shouldBe AutomationUiModel.ChipTint.SECONDARY
        ui.enabledState?.textRes shouldBe R.string.setup_acs_state_ondemand
        ui.runningState shouldBe null
        ui.showAllowAction shouldBe false
    }

    @Test
    fun `enabled but not running shows error running chip and enable service action`() {
        val ui = result(
            hasConsent = true,
            isServiceEnabled = true,
            isServiceRunning = false,
            canSelfEnable = false,
        ).toUiModel()
        ui.enabledState?.tint shouldBe AutomationUiModel.ChipTint.PRIMARY
        ui.runningState?.tint shouldBe AutomationUiModel.ChipTint.ERROR
        ui.runningState?.textRes shouldBe R.string.setup_acs_state_stopped
        ui.showRunningStateHint shouldBe true
        ui.showAllowAction shouldBe true
        ui.allowActionText shouldBe AutomationUiModel.AllowActionText.ENABLE_SERVICE
    }

    @Test
    fun `MIUI hint only shows when not enabled and autostart needed`() {
        val enabled = result(
            hasConsent = true,
            isServiceEnabled = true,
            needsXiaomiAutostart = true,
        ).toUiModel()
        enabled.showMiuiAutostartHint shouldBe false

        val disabled = result(
            hasConsent = true,
            isServiceEnabled = false,
            needsXiaomiAutostart = true,
        ).toUiModel()
        disabled.showMiuiAutostartHint shouldBe true
    }

    @Test
    fun `appops restriction hint propagates directly`() {
        val ui = result(
            hasConsent = true,
            showAppOpsRestrictionHint = true,
        ).toUiModel()
        ui.showAppOpsRestrictionHint shouldBe true
    }

    @Test
    fun `shortcut hint requires consent and shortcut flag`() {
        val none = result(hasConsent = null, isShortcutOrButtonEnabled = true).toUiModel()
        none.showShortcutHint shouldBe false

        val yes = result(hasConsent = true, isShortcutOrButtonEnabled = true).toUiModel()
        yes.showShortcutHint shouldBe true

        val noShortcut = result(hasConsent = true, isShortcutOrButtonEnabled = false).toUiModel()
        noShortcut.showShortcutHint shouldBe false
    }

    @Test
    fun `disabled state reports error chip when no self-enable`() {
        val ui = result(
            hasConsent = true,
            isServiceEnabled = false,
            canSelfEnable = false,
        ).toUiModel()
        ui.enabledState?.tint shouldBe AutomationUiModel.ChipTint.ERROR
        ui.enabledState?.textRes shouldBe R.string.setup_acs_state_disabled
        ui.runningState shouldBe null
    }
}
