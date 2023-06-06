package eu.darken.sdmse.exclusion.ui.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.APathLookup
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
        item.lookup?.let { icon.loadFilePreview(it) }
        primary.text = item.exclusion.label.get(context)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val lookup: APathLookup<*>?,
        override val exclusion: PathExclusion,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {

        override val stableId: Long = exclusion.hashCode().toLong()
    }

}