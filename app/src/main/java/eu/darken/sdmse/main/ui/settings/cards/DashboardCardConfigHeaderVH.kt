package eu.darken.sdmse.main.ui.settings.cards

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DashboardCardConfigHeaderBinding


class DashboardCardConfigHeaderVH(parent: ViewGroup) :
    DashboardCardConfigAdapter.BaseVH<DashboardCardConfigAdapter.HeaderItem, DashboardCardConfigHeaderBinding>(
        R.layout.dashboard_card_config_header,
        parent,
    ) {

    override val viewBinding = lazy { DashboardCardConfigHeaderBinding.bind(itemView) }

    override val onBindData: DashboardCardConfigHeaderBinding.(
        item: DashboardCardConfigAdapter.HeaderItem,
        payloads: List<Any>,
    ) -> Unit = binding { _ ->
        // Static content - nothing to bind
    }
}
