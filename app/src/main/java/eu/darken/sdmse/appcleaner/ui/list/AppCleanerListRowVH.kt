package eu.darken.sdmse.appcleaner.ui.list

import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import eu.darken.sdmse.common.ui.performClickWithRipple
import eu.darken.sdmse.databinding.AppcleanerListItemBinding


class AppCleanerListRowVH(parent: ViewGroup) :
    AppCleanerListAdapter.BaseVH<AppCleanerListRowVH.Item, AppcleanerListItemBinding>(
        R.layout.appcleaner_list_item,
        parent
    ) {

    override val viewBinding = lazy { AppcleanerListItemBinding.bind(itemView) }

    override val onBindData: AppcleanerListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val junk = item.junk
        icon.apply {
            loadAppIcon(junk.pkg)
            setOnClickListener { root.performClickWithRipple() }
            setOnLongClickListener {
                val intent = junk.pkg.getSettingsIntent(context)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    log(WARN) { "Settings intent failed for ${junk.pkg}: $e" }
                }
                true
            }
        }
        primary.text = junk.label.get(context)
        secondary.text = junk.pkg.packageName

        items.text = getQuantityString(eu.darken.sdmse.common.R.plurals.result_x_items, junk.itemCount)
        size.text = Formatter.formatShortFileSize(context, junk.size)

        root.setOnClickListener { item.onItemClicked(junk) }
        detailsAction.setOnClickListener { item.onDetailsClicked(junk) }
    }

    data class Item(
        val junk: AppJunk,
        val onItemClicked: (AppJunk) -> Unit,
        val onDetailsClicked: (AppJunk) -> Unit,
    ) : AppCleanerListAdapter.Item {

        override val stableId: Long = junk.pkg.id.hashCode().toLong()
    }

}