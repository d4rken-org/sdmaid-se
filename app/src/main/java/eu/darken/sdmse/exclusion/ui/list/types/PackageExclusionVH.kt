package eu.darken.sdmse.exclusion.ui.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.ExclusionListItemPackageBinding
import eu.darken.sdmse.exclusion.core.types.PackageExclusion
import eu.darken.sdmse.exclusion.ui.list.ExclusionListAdapter


class PackageExclusionVH(parent: ViewGroup) :
    ExclusionListAdapter.BaseVH<PackageExclusionVH.Item, ExclusionListItemPackageBinding>(
        R.layout.exclusion_list_item_package,
        parent
    ) {

    override val viewBinding = lazy { ExclusionListItemPackageBinding.bind(itemView) }

    override val onBindData: ExclusionListItemPackageBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        primary.apply {
            text = item.exclusion.pkgId.name
            item.appLabel?.let {
                append(" (${it.get(context)})")
            }
        }
        secondary.text = getString(R.string.exclusion_type_package)

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val exclusion: PackageExclusion,
        val appLabel: CaString?,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {

        override val stableId: Long = exclusion.hashCode().toLong()
    }

}