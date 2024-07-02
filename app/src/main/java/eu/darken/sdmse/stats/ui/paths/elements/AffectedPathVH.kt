package eu.darken.sdmse.stats.ui.paths.elements

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.StatsAffectedPathsPathItemBinding
import eu.darken.sdmse.stats.core.AffectedPath
import eu.darken.sdmse.stats.core.iconRes
import eu.darken.sdmse.stats.ui.paths.AffectedPathsAdapter


class AffectedPathVH(parent: ViewGroup) :
    AffectedPathsAdapter.BaseVH<AffectedPathVH.Item, StatsAffectedPathsPathItemBinding>(
        R.layout.stats_affected_paths_path_item,
        parent
    ) {

    override val viewBinding = lazy { StatsAffectedPathsPathItemBinding.bind(itemView) }

    override val onBindData: StatsAffectedPathsPathItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        icon.setImageResource(item.path.action.iconRes)
        primary.text = item.path.path.userReadablePath.get(context)
    }

    data class Item(
        val path: AffectedPath,
    ) : AffectedPathsAdapter.Item {

        override val stableId: Long = path.hashCode().toLong()
    }

}