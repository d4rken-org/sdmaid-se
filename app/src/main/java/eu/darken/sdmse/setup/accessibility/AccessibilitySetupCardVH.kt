package eu.darken.sdmse.setup.accessibility

import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
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
        val state = item.setupState
        enabledState.apply {
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
        runningState.apply {
            isGone = !state.isServiceEnabled
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

        runningStateHint.isGone = state.isServiceRunning || !state.isServiceEnabled

        grantAction.setOnClickListener { item.onGrantAction() }
        helpAction.setOnClickListener { item.onHelp() }
    }

    data class Item(
        val setupState: AccessibilitySetupModule.State,
        val onGrantAction: () -> Unit,
        val onHelp: () -> Unit,
    ) : SetupAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}