package eu.darken.sdmse.analyzer.ui.storage.app.items

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.analyzer.core.storage.categories.AppCategory
import eu.darken.sdmse.analyzer.ui.storage.app.AppDetailsAdapter
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.AnalyzerAppVhHeaderBinding


class AppDetailsHeaderVH(parent: ViewGroup) :
    AppDetailsAdapter.BaseVH<AppDetailsHeaderVH.Item, AnalyzerAppVhHeaderBinding>(
        R.layout.analyzer_app_vh_header,
        parent
    ) {

    override val viewBinding = lazy { AnalyzerAppVhHeaderBinding.bind(itemView) }

    override val onBindData: AnalyzerAppVhHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val storage = item.storage
        val pkgStat = item.pkgStat

        appIcon.loadAppIcon(pkgStat.pkg)

        title.text = pkgStat.pkg.label?.get(context) ?: pkgStat.pkg.packageName
        subtitle.text = pkgStat.id.pkgId.name

        primary.text = getString(
            R.string.analyzer_app_details_app_occupies_x_on_y,
            Formatter.formatShortFileSize(context, pkgStat.totalSize),
            storage.label.get(context)
        )

        settingsAction.setOnClickListener { item.onSettingsClicked() }
    }

    data class Item(
        val storage: DeviceStorage,
        val pkgStat: AppCategory.PkgStat,
        val onSettingsClicked: () -> Unit,
    ) : AppDetailsAdapter.Item {

        override val stableId: Long = pkgStat.id.hashCode().toLong()
    }

}