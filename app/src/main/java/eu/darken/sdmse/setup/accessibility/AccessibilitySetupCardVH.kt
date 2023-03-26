package eu.darken.sdmse.setup.accessibility

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import eu.darken.sdmse.R
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SetupAccessibilityItemBinding
import eu.darken.sdmse.setup.SetupAdapter


class AccessibilitySetupCardVH(parent: ViewGroup) :
    SetupAdapter.BaseVH<AccessibilitySetupCardVH.Item, SetupAccessibilityItemBinding>(
        R.layout.setup_accessibility_item,
        parent
    ) {

    override val viewBinding = lazy { SetupAccessibilityItemBinding.bind(itemView) }

    override val onBindData: SetupAccessibilityItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val state = item.state
        enabledState.apply {
            isVisible = state.hasConsent == true
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(
                    context, if (state.isServiceEnabled) R.drawable.ic_check_circle else R.drawable.ic_cancel
                ),
                null,
                null,
                null,
            )
            TextViewCompat.setCompoundDrawableTintList(
                this,
                ColorStateList.valueOf(
                    context.getColorForAttr(if (state.isServiceEnabled) R.attr.colorPrimary else R.attr.colorError)
                )
            )
            text = getString(
                if (state.isServiceEnabled) R.string.setup_acs_state_enabled
                else R.string.setup_acs_state_disabled
            )
            setTextColor(
                context.getColorForAttr(
                    if (state.isServiceEnabled) R.attr.colorPrimary else R.attr.colorError
                )
            )
        }
        enabledStateHint.apply {
            isVisible = !state.isServiceEnabled && state.needsAutostart
            when {
                state.needsAutostart -> getString(R.string.setup_acs_state_stopped_hint_miui)
                else -> ""
            }
        }

        runningState.apply {
            isVisible = state.isServiceEnabled && state.hasConsent == true
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(
                    context, if (state.isServiceRunning) R.drawable.ic_check_circle else R.drawable.ic_cancel
                ),
                null,
                null,
                null,
            )
            TextViewCompat.setCompoundDrawableTintList(
                this,
                ColorStateList.valueOf(
                    context.getColorForAttr(if (state.isServiceRunning) R.attr.colorPrimary else R.attr.colorError)
                )
            )
            text = getString(
                if (state.isServiceRunning) R.string.setup_acs_state_running
                else R.string.setup_acs_state_stopped
            )
            setTextColor(
                context.getColorForAttr(
                    if (state.isServiceRunning) R.attr.colorPrimary else R.attr.colorError
                )
            )
        }

        runningStateHint.isVisible = !state.isServiceRunning && state.isServiceEnabled

        allowAction.apply {
            isVisible = state.hasConsent != true || (state.hasConsent == true && !state.isServiceRunning)
            text = when {
                state.hasConsent != true -> getString(R.string.setup_acs_consent_positive_action)
                state.hasConsent == true && !state.isServiceRunning -> getString(R.string.general_enable_service_action)
                else -> getString(R.string.setup_acs_consent_positive_action)
            }
            setOnClickListener { item.onGrantAction() }

        }
        shortcutHint.isVisible = state.hasConsent != true || (state.hasConsent == true && !state.isServiceRunning)

        disallowAction.apply {
            isVisible = state.hasConsent != false
            setOnClickListener { item.onDismiss() }
        }
        disallowHint.isVisible = state.hasConsent != false

        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        override val state: AccessibilitySetupModule.State,
        val onGrantAction: () -> Unit,
        val onDismiss: () -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}