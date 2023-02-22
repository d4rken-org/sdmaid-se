package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.databinding.AppcleanerAppjunkElementHeaderBinding


class AppJunkElementHeaderVH(parent: ViewGroup) :
    AppJunkElementsAdapter.BaseVH<AppJunkElementHeaderVH.Item, AppcleanerAppjunkElementHeaderBinding>(
        R.layout.appcleaner_appjunk_element_header,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerAppjunkElementHeaderBinding.bind(itemView) }

    override val onBindData: AppcleanerAppjunkElementHeaderBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val junk = item.appJunk

        icon.apply {
            loadAppIcon(junk.pkg)
            setOnLongClickListener {
                val intent = junk.pkg.getSettingsIntent(context)
                context.startActivity(intent)
                true
            }
        }
        appName.text = junk.label.get(context)
        appId.text = junk.pkg.packageName

        sizeValue.text = Formatter.formatFileSize(context, junk.size)

        val hasHint = false
        hintsLabel.isGone = !hasHint
        hintsValue.isGone = !hasHint
        hintsValue.text = ""

        deleteAction.setOnClickListener { item.onDeleteAllClicked(item) }
        excludeAction.setOnClickListener { item.onExcludeClicked(item) }
    }

    data class Item(
        val appJunk: AppJunk,
        val onDeleteAllClicked: (Item) -> Unit,
        val onExcludeClicked: (Item) -> Unit,
    ) : AppJunkElementsAdapter.Item {

        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}