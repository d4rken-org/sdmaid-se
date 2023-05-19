package eu.darken.sdmse.analyzer.ui.storage.app.items

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.app.AppDetailsAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerAppVhDataPublicBinding


class AppDetailsPublicDataVH(parent: ViewGroup) :
    AppDetailsAdapter.BaseVH<AppDetailsPublicDataVH.Item, AnalyzerAppVhDataPublicBinding>(
        R.layout.analyzer_app_vh_data_public,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerAppVhDataPublicBinding.bind(itemView) }

    override val onBindData: AnalyzerAppVhDataPublicBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage
        val pkgStat = item.pkgStat

        val appCodeSize = pkgStat.appCode.groupSize
        primary.text = Formatter.formatShortFileSize(context, appCodeSize)

        root.setOnClickListener { item.onViewAction(item) }
    }

    data class Item(
        val storage: DeviceStorage,
        val pkgStat: AppCategory.PkgStat,
        val onViewAction: (Item) -> Unit,
    ) : AppDetailsAdapter.Item {

        override val stableId: Long = pkgStat.id.hashCode().toLong()
    }

}