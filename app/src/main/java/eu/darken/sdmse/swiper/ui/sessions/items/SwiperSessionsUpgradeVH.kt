package eu.darken.sdmse.swiper.ui.sessions.items

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SwiperSessionsUpgradeItemBinding
import eu.darken.sdmse.swiper.ui.sessions.SwiperSessionsAdapter

class SwiperSessionsUpgradeVH(parent: ViewGroup) :
    SwiperSessionsAdapter.BaseVH<SwiperSessionsUpgradeVH.Item, SwiperSessionsUpgradeItemBinding>(
        R.layout.swiper_sessions_upgrade_item,
        parent,
    ) {

    override val viewBinding = lazy { SwiperSessionsUpgradeItemBinding.bind(itemView) }

    override val onBindData: SwiperSessionsUpgradeItemBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        val filesLimit = getQuantityString(R.plurals.swiper_sessions_upgrade_files_limit, item.freeVersionLimit)
        val sessionsLimit = getQuantityString(R.plurals.swiper_sessions_upgrade_sessions_limit, item.freeSessionLimit)
        body.text = getString(R.string.swiper_sessions_upgrade_body) + "\n\u2022 $filesLimit\n\u2022 $sessionsLimit"
        upgradeAction.setOnClickListener { item.onUpgrade() }
    }

    data class Item(
        val freeVersionLimit: Int,
        val freeSessionLimit: Int,
        val onUpgrade: () -> Unit,
    ) : SwiperSessionsAdapter.Item {
        override val stableId: Long = "upgrade".hashCode().toLong()
    }
}
