package eu.darken.sdmse.analyzer.ui.storage.apps

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerAppsVhBinding


class AppsItemVH(parent: ViewGroup) :
    AppsAdapter.BaseVH<AppsItemVH.Item, AnalyzerAppsVhBinding>(
        R.layout.analyzer_apps_vh,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerAppsVhBinding.bind(itemView) }

    override val onBindData: AnalyzerAppsVhBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val category = item.appCategory
        val app = item.pkgStat

        appIcon.loadAppIcon(app.pkg)

        primary.text = app.pkg.label?.get(context) ?: app.pkg.packageName
        secondary.text = Formatter.formatShortFileSize(context, app.totalSize)

        progress.progress = ((app.totalSize / category.spaceUsed.toDouble()) * 100).toInt()

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val appCategory: AppCategory,
        val pkgStat: AppCategory.PkgStat,
        val onItemClicked: (Item) -> Unit,
    ) : AppsAdapter.Item {

        override val stableId: Long = pkgStat.pkg.installId.hashCode().toLong()
    }

}