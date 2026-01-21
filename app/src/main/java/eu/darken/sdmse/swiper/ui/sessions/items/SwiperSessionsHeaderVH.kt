package eu.darken.sdmse.swiper.ui.sessions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SwiperSessionsHeaderItemBinding
import eu.darken.sdmse.swiper.ui.sessions.SwiperSessionsAdapter

class SwiperSessionsHeaderVH(parent: ViewGroup) :
    SwiperSessionsAdapter.BaseVH<SwiperSessionsHeaderVH.Item, SwiperSessionsHeaderItemBinding>(
        R.layout.swiper_sessions_header_item,
        parent,
    ) {

    override val viewBinding = lazy { SwiperSessionsHeaderItemBinding.bind(itemView) }

    override val onBindData: SwiperSessionsHeaderItemBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = binding { _ ->
        // Description is set in XML, nothing to bind
    }

    data class Item(
        val isPro: Boolean,
    ) : SwiperSessionsAdapter.Item {
        override val stableId: Long = "header".hashCode().toLong()
    }
}
