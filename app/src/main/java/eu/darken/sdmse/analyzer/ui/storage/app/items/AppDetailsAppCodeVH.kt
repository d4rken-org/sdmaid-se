package eu.darken.sdmse.analyzer.ui.storage.app.items

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.app.AppDetailsAdapter
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerAppVhAppcodeBinding


class AppDetailsAppCodeVH(parent: ViewGroup) :
    AppDetailsAdapter.BaseVH<AppDetailsAppCodeVH.Item, AnalyzerAppVhAppcodeBinding>(
        R.layout.analyzer_app_vh_appcode,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerAppVhAppcodeBinding.bind(itemView) }

    override val onBindData: AnalyzerAppVhAppcodeBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val appCodeSize = item.group.groupSize

        primary.text = Formatter.formatShortFileSize(context, appCodeSize)

        root.setOnClickListener { item.onViewAction(item) }
    }

    data class Item(
        val storage: DeviceStorage,
        val pkgStat: AppCategory.PkgStat,
        val group: ContentGroup,
        val onViewAction: (Item) -> Unit,
    ) : AppDetailsAdapter.Item {

        override val stableId: Long = pkgStat.id.hashCode().toLong()
    }

}