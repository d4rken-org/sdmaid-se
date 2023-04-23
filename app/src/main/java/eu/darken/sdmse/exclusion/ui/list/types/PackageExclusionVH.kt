package eu.darken.sdmse.exclusion.ui.list.types

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.Pkg
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
        item.pkg?.let { icon.loadAppIcon(it) }
        primary.text = item.exclusion.label.get(context)
        secondary.text = item.exclusion.pkgId.name

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val pkg: Pkg?,
        val exclusion: PackageExclusion,
        val onItemClick: (Item) -> Unit,
    ) : ExclusionListAdapter.Item {

        override val stableId: Long = exclusion.hashCode().toLong()
    }

}