package eu.darken.sdmse.main.ui.settings.cards

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardCardConfigRowBinding

class DashboardCardConfigRowVH(parent: ViewGroup) :
    DashboardCardConfigAdapter.BaseVH<DashboardCardConfigAdapter.CardItem, DashboardCardConfigRowBinding>(
        R.layout.dashboard_card_config_row,
        parent,
    ) {

    override val viewBinding = lazy { DashboardCardConfigRowBinding.bind(itemView) }

    @SuppressLint("ClickableViewAccessibility")
    override val onBindData: DashboardCardConfigRowBinding.(
        item: DashboardCardConfigAdapter.CardItem,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        val cardEntry = item.cardEntry

        icon.setImageResource(cardEntry.type.iconRes)
        title.setText(cardEntry.type.labelRes)

        visibilityToggle.apply {
            setOnCheckedChangeListener(null)
            isChecked = cardEntry.isVisible
            setOnCheckedChangeListener { _, _ ->
                item.onVisibilityToggle(item)
            }
        }

        dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                (bindingAdapter as? DashboardCardConfigAdapter)?.onStartDrag(this@DashboardCardConfigRowVH)
            }
            false
        }
    }
}
