package eu.darken.sdmse.deduplicator.ui.settings.arbiter

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.DeduplicatorArbiterConfigHeaderBinding


class ArbiterConfigHeaderVH(parent: ViewGroup) :
    ArbiterConfigAdapter.BaseVH<ArbiterConfigAdapter.HeaderItem, DeduplicatorArbiterConfigHeaderBinding>(
        R.layout.deduplicator_arbiter_config_header,
        parent,
    ) {

    override val viewBinding = lazy { DeduplicatorArbiterConfigHeaderBinding.bind(itemView) }

    override val onBindData: DeduplicatorArbiterConfigHeaderBinding.(
        item: ArbiterConfigAdapter.HeaderItem,
        payloads: List<Any>,
    ) -> Unit = binding { _ ->
        // Static content - nothing to bind
    }
}
