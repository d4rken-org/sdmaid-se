package eu.darken.sdmse.setup.automation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessTimeFilled
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.R

internal data class AutomationUiModel(
    val enabledState: StateChip?,
    val showMiuiAutostartHint: Boolean,
    val showAppOpsRestrictionHint: Boolean,
    val runningState: StateChip?,
    val showRunningStateHint: Boolean,
    val showAllowAction: Boolean,
    val allowActionText: AllowActionText,
    val showShortcutHint: Boolean,
    val showDisallowAction: Boolean,
    val showDisallowHint: Boolean,
) {
    data class StateChip(
        val icon: ImageVector,
        val tint: ChipTint,
        @StringRes val textRes: Int,
    )

    enum class ChipTint { PRIMARY, SECONDARY, ERROR }

    @Suppress("unused")
    enum class AllowActionText(@StringRes val textRes: Int) {
        CONSENT_POSITIVE(R.string.setup_acs_consent_positive_action),
        ENABLE_SERVICE(eu.darken.sdmse.common.R.string.general_enable_service_action),
    }
}

internal fun AutomationSetupModule.Result.toUiModel(): AutomationUiModel {
    val enabledChip = when {
        hasConsent != true -> null
        isServiceEnabled -> AutomationUiModel.StateChip(
            icon = Icons.TwoTone.CheckCircle,
            tint = AutomationUiModel.ChipTint.PRIMARY,
            textRes = R.string.setup_acs_state_enabled,
        )
        canSelfEnable -> AutomationUiModel.StateChip(
            icon = Icons.TwoTone.AccessTimeFilled,
            tint = AutomationUiModel.ChipTint.SECONDARY,
            textRes = R.string.setup_acs_state_ondemand,
        )
        else -> AutomationUiModel.StateChip(
            icon = Icons.TwoTone.Cancel,
            tint = AutomationUiModel.ChipTint.ERROR,
            textRes = R.string.setup_acs_state_disabled,
        )
    }

    val runningChip = when {
        hasConsent != true || !isServiceEnabled -> null
        isServiceRunning -> AutomationUiModel.StateChip(
            icon = Icons.TwoTone.CheckCircle,
            tint = AutomationUiModel.ChipTint.PRIMARY,
            textRes = R.string.setup_acs_state_running,
        )
        else -> AutomationUiModel.StateChip(
            icon = Icons.TwoTone.Cancel,
            tint = AutomationUiModel.ChipTint.ERROR,
            textRes = R.string.setup_acs_state_stopped,
        )
    }

    val showAllow = hasConsent != true || (!isServiceRunning && !canSelfEnable)
    val allowText = when {
        hasConsent != true -> AutomationUiModel.AllowActionText.CONSENT_POSITIVE
        !isServiceRunning -> AutomationUiModel.AllowActionText.ENABLE_SERVICE
        else -> AutomationUiModel.AllowActionText.CONSENT_POSITIVE
    }

    return AutomationUiModel(
        enabledState = enabledChip,
        showMiuiAutostartHint = !isServiceEnabled && needsXiaomiAutostart,
        showAppOpsRestrictionHint = showAppOpsRestrictionHint,
        runningState = runningChip,
        showRunningStateHint = !isServiceRunning && isServiceEnabled,
        showAllowAction = showAllow,
        allowActionText = allowText,
        showShortcutHint = hasConsent == true && isShortcutOrButtonEnabled,
        showDisallowAction = hasConsent != false,
        showDisallowHint = hasConsent != false,
    )
}
