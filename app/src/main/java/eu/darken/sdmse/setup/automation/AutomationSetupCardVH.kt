package eu.darken.sdmse.setup.automation

import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.ui.setLeftIcon
import eu.darken.sdmse.databinding.SetupAutomationItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class AutomationSetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<AutomationSetupCardVH.Item, SetupAutomationItemBinding>(
        R.layout.setup_automation_item,
        parent
    ) {

    override val viewBinding = lazy { SetupAutomationItemBinding.bind(itemView) }

    override val onBindData: SetupAutomationItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val state = item.state
        enabledState.apply {
            isVisible = state.hasConsent == true

            when {
                state.isServiceEnabled -> setLeftIcon(
                    R.drawable.ic_check_circle,
                    androidx.appcompat.R.attr.colorPrimary
                )

                state.canSelfEnable -> setLeftIcon(
                    R.drawable.ic_baseline_access_time_filled_24,
                    com.google.android.material.R.attr.colorSecondary
                )

                else -> setLeftIcon(R.drawable.ic_cancel, androidx.appcompat.R.attr.colorError)
            }

            setTextColor(
                context.getColorForAttr(
                    when {
                        state.isServiceEnabled -> androidx.appcompat.R.attr.colorPrimary
                        state.canSelfEnable -> com.google.android.material.R.attr.colorSecondary
                        else -> androidx.appcompat.R.attr.colorError
                    }
                )
            )
            text = getString(
                when {
                    state.isServiceEnabled -> R.string.setup_acs_state_enabled
                    state.canSelfEnable -> R.string.setup_acs_state_ondemand
                    else -> R.string.setup_acs_state_disabled
                }
            )
        }
        restrictionAppopsHintContainer.isVisible = state.showAppOpsRestrictionHint
        restrictionAppopsHintHelpAction.setOnClickListener { item.onRestrictionsHelp() }
        restrictionAppopsHintShowAction.setOnClickListener { item.onRestrictionsShow() }

        enabledStateHint.apply {
            isVisible = !state.isServiceEnabled && state.needsXiaomiAutostart
            when {
                state.needsXiaomiAutostart -> getString(R.string.setup_acs_state_stopped_hint_miui)
                else -> ""
            }
        }

        runningState.apply {
            isVisible = state.isServiceEnabled && state.hasConsent == true

            when {
                state.isServiceRunning -> setLeftIcon(
                    R.drawable.ic_check_circle,
                    androidx.appcompat.R.attr.colorPrimary
                )

                else -> setLeftIcon(
                    R.drawable.ic_cancel,
                    androidx.appcompat.R.attr.colorError
                )
            }

            setTextColor(
                context.getColorForAttr(
                    if (state.isServiceRunning) androidx.appcompat.R.attr.colorPrimary else androidx.appcompat.R.attr.colorError
                )
            )
            text = getString(
                if (state.isServiceRunning) R.string.setup_acs_state_running
                else R.string.setup_acs_state_stopped
            )
        }

        runningStateHint.isVisible = !state.isServiceRunning && state.isServiceEnabled

        allowAction.apply {
            isVisible = state.hasConsent != true || (!state.isServiceRunning && !state.canSelfEnable)
            text = when {
                state.hasConsent != true -> getString(R.string.setup_acs_consent_positive_action)
                !state.isServiceRunning -> getString(eu.darken.sdmse.common.R.string.general_enable_service_action)
                else -> getString(R.string.setup_acs_consent_positive_action)
            }
            setOnClickListener { item.onGrantAction() }

        }
        shortcutHint.isVisible = state.hasConsent == true && state.isShortcutOrButtonEnabled

        disallowAction.apply {
            isVisible = state.hasConsent != false
            setOnClickListener { item.onDismiss() }
        }
        disallowHint.isVisible = state.hasConsent != false

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: AutomationSetupModule.Result,
        val onGrantAction: () -> Unit,
        val onDismiss: () -> Unit,
        val onHelp: () -> Unit,
        val onRestrictionsHelp: () -> Unit,
        val onRestrictionsShow: () -> Unit,
    ) : SetupAdapter.Item
}