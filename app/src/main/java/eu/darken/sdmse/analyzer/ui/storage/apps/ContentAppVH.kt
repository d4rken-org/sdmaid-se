package eu.darken.sdmse.analyzer.ui.storage.apps

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.types.AppContent
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerContentAppsItemBinding


class ContentAppVH(parent: ViewGroup) :
    ContentAppsAdapter.BaseVH<ContentAppVH.Item, AnalyzerContentAppsItemBinding>(
        R.layout.analyzer_content_apps_item,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerContentAppsItemBinding.bind(itemView) }

    override val onBindData: AnalyzerContentAppsItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val app = item.app

        appIcon.loadAppIcon(app.pkg)
        primary.text = app.pkg.label?.get(context) ?: app.pkg.packageName

        usedSpace.text = Formatter.formatShortFileSize(context, app.totalSize)
        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        val app: AppContent.PkgStat,
        val onItemClicked: (Item) -> Unit,
    ) : ContentAppsAdapter.Item {

        override val stableId: Long = app.pkg.installId.hashCode().toLong()
    }

}