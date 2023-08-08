package eu.darken.sdmse.systemcleaner.ui.customfilter.editor.live

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.iconRes
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SystemcleanerCustomfilterEditorLivesearchItemBinding


class LiveSearchListRow(parent: ViewGroup) :
    LiveSearchListAdapter.BaseVH<LiveSearchListRow.Item, SystemcleanerCustomfilterEditorLivesearchItemBinding>(
        R.layout.systemcleaner_customfilter_editor_livesearch_item,
        parent
    ) {

    override val viewBinding = lazy { SystemcleanerCustomfilterEditorLivesearchItemBinding.bind(itemView) }

    override val onBindData: SystemcleanerCustomfilterEditorLivesearchItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        icon.setImageResource(item.lookup.fileType.iconRes)
        primary.text = item.lookup.userReadablePath.get(context)
    }

    data class Item(
        val lookup: APathLookup<*>,
    ) : LiveSearchListAdapter.Item {
        override val stableId: Long = lookup.path.hashCode().toLong()
    }

}