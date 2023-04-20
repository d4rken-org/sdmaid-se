package eu.darken.sdmse.exclusion.ui.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.ExclusionListItemPathBinding
import eu.darken.sdmse.exclusion.core.types.PathExclusion
import eu.darken.sdmse.exclusion.ui.list.ExclusionListAdapter


class PathExclusionVH(parent: ViewGroup) :
    ExclusionListAdapter.BaseVH<PathExclusionVH.Item, ExclusionListItemPathBinding>(
        R.layout.exclusion_list_item_path,
        parent
    ) {

    override val viewBinding = lazy { ExclusionListItemPathBinding.bind(itemView) }

    override val onBindData: ExclusionListItemPathBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val excl = item.exclusion
        primary.text = excl.path.userReadablePath.get(context)
        secondary.text = excl.path.pathType.name

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val exclusion: PathExclusion,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {

        override val stableId: Long = exclusion.hashCode().toLong()
    }

}