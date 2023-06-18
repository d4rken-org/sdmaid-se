package eu.darken.sdmse.appcleaner.ui.details.appjunk.elements

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.ui.details.appjunk.AppJunkElementsAdapter
import eu.darken.sdmse.common.coil.loadAppIcon
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
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
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    log(WARN) { "Settings intent failed for ${junk.pkg}: $e" }
                }
                true
            }
        }
        appName.text = junk.label.get(context)
        appId.text = junk.pkg.packageName

        sizeValue.text = Formatter.formatFileSize(context, junk.size)

        userLabel.isVisible = junk.userProfile != null
        userValue.apply {
            isVisible = junk.userProfile != null
            text = junk.userProfile?.label ?: "ID#${junk.userProfile?.handle?.handleId}"
        }

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

        override val itemSelectionKey: String? = null
        override val stableId: Long = this::class.java.hashCode().toLong()
    }

}