package eu.darken.sdmse.stats.ui.pkgs.elements

import android.view.ViewGroup
import androidx.core.view.isInvisible
import coil.load
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.databinding.StatsAffectedPkgsPkgItemBinding
import eu.darken.sdmse.stats.core.AffectedPkg
import eu.darken.sdmse.stats.core.iconRes
import eu.darken.sdmse.stats.ui.pkgs.AffectedPkgsAdapter


class AffectedPkgVH(parent: ViewGroup) :
    AffectedPkgsAdapter.BaseVH<AffectedPkgVH.Item, StatsAffectedPkgsPkgItemBinding>(
        R.layout.stats_affected_pkgs_pkg_item,
        parent
    ) {

    override val viewBinding = lazy { StatsAffectedPkgsPkgItemBinding.bind(itemView) }

    override val onBindData: StatsAffectedPkgsPkgItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        icon.setImageResource(item.affectedPkg.action.iconRes)

        pkg.text = item.installedPkg?.let {
            "${it.label?.get(context)} (${item.affectedPkg.pkgId.name})"
        } ?: item.affectedPkg.pkgId.name

        item.installedPkg?.let { preview.load(it) }
        preview.isInvisible = item.installedPkg == null
    }

    data class Item(
        val affectedPkg: AffectedPkg,
        val installedPkg: Pkg?,
    ) : AffectedPkgsAdapter.Item {
        override val stableId: Long = affectedPkg.hashCode().toLong()
    }
}