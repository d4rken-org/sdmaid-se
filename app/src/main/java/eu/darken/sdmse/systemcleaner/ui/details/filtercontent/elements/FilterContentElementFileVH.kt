package eu.darken.sdmse.systemcleaner.ui.details.filtercontent.elements

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.SystemcleanerFiltercontentElementFileBinding
import eu.darken.sdmse.systemcleaner.core.FilterContent
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilter
import eu.darken.sdmse.systemcleaner.ui.details.filtercontent.FilterContentElementsAdapter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class FilterContentElementFileVH(parent: ViewGroup) :
    FilterContentElementsAdapter.BaseVH<FilterContentElementFileVH.Item, SystemcleanerFiltercontentElementFileBinding>(
        R.layout.systemcleaner_filtercontent_element_file,
        parent
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { SystemcleanerFiltercontentElementFileBinding.bind(itemView) }

    override val onBindData: SystemcleanerFiltercontentElementFileBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        lastItem = item
        icon.apply {
            loadFilePreview(item.match.lookup)
            if (item.onThumbnailClick != null) {
                setOnClickListener { item.onThumbnailClick.invoke(item) }
            } else {
                setOnClickListener(null)
            }
        }

        primary.text = item.match.lookup.userReadablePath.get(context)

        secondary.apply {
            isVisible = item.showDate
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            text = item.match.lookup.modifiedAt.toSystemTimezone().format(formatter)
        }

        size.apply {
            text = Formatter.formatShortFileSize(context, item.match.expectedGain)
            isGone = item.match.lookup.fileType != FileType.FILE
        }

        root.setOnClickListener { item.onItemClick(item) }
    }

    data class Item(
        val filterContent: FilterContent,
        val match: SystemCleanerFilter.Match,
        val onItemClick: (Item) -> Unit,
        val onThumbnailClick: ((Item) -> Unit)? = null,
        val showDate: Boolean,
    ) : FilterContentElementsAdapter.Item {

        override val itemSelectionKey: String
            get() = match.path.path

        override val stableId: Long = match.lookup.hashCode().toLong()
    }

}