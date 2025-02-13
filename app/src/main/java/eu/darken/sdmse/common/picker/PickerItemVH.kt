package eu.darken.sdmse.common.picker

import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.dispose
import eu.darken.sdmse.R
import eu.darken.sdmse.common.areas.label
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.CommonPickerItemBinding


class PickerItemVH(parent: ViewGroup) :
    PickerAdapter.BaseVH<PickerItemVH.Item, CommonPickerItemBinding>(
        R.layout.common_picker_item,
        parent
    ) {

    override val viewBinding = lazy { CommonPickerItemBinding.bind(itemView) }

    override val onBindData: CommonPickerItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { wrapper ->
        val item = wrapper.item

        contentIcon.apply {
            if (item.parent == null) {
                setImageResource(R.drawable.ic_folder_home_24)
            } else {
                dispose()
                loadFilePreview(item.lookup)
            }
        }

        primary.text = when {
            item.parent == null -> item.lookup.path
            else -> item.lookup.name
        }
        secondary.text = item.dataArea.type.label.get(context)
        tertiary.text = when (item.lookup.fileType) {
            FileType.DIRECTORY -> getString(eu.darken.sdmse.common.R.string.file_type_directory)
            FileType.FILE -> getString(eu.darken.sdmse.common.R.string.file_type_file)
            FileType.SYMBOLIC_LINK -> getString(eu.darken.sdmse.common.R.string.file_type_symbolic_link)
            FileType.UNKNOWN -> getString(eu.darken.sdmse.common.R.string.file_type_unknown)
        }

        indicator.apply {
            isChecked = item.selected
            isVisible = item.selectable
            setOnClickListener { wrapper.onSelect?.invoke() }
        }

        root.setOnClickListener { wrapper.onItemClicked() }
    }

    data class Item(
        val item: PickerItem,
        val onItemClicked: () -> Unit,
        val onSelect: (() -> Unit)?,
    ) : PickerAdapter.Item {

        val path: APath
            get() = item.lookup.lookedUp

        override val stableId: Long = path.hashCode().toLong()
    }

}