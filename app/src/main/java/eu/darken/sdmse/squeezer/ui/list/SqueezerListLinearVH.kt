package eu.darken.sdmse.squeezer.ui.list

import android.graphics.Bitmap
import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.squeezer.core.CompressibleImage
import eu.darken.sdmse.databinding.SqueezerListLinearItemBinding


class SqueezerListLinearVH(parent: ViewGroup) :
    SqueezerListAdapter.BaseVH<SqueezerListLinearVH.Item, SqueezerListLinearItemBinding>(
        R.layout.squeezer_list_linear_item,
        parent,
    ), SelectableVH {

    private var lastItem: Item? = null
    override val itemSelectionKey: String?
        get() = lastItem?.itemSelectionKey

    override fun updatedSelectionState(selected: Boolean) {
        itemView.isActivated = selected
    }

    override val viewBinding = lazy { SqueezerListLinearItemBinding.bind(itemView) }

    override val onBindData: SqueezerListLinearItemBinding.(
        item: Item,
        payloads: List<Any>,
    ) -> Unit = binding { item ->
        lastItem = item
        val image = item.image

        previewImage.apply {
            loadFilePreview(image.lookup) {
                bitmapConfig(Bitmap.Config.ARGB_8888)
            }
            setOnClickListener { item.onPreviewClicked(item) }
        }

        filename.text = image.lookup.name
        filepath.text = image.lookup.userReadablePath.get(context).replaceLast(image.lookup.name, "")

        currentSize.text = context.getString(
            R.string.squeezer_current_size_format,
            Formatter.formatShortFileSize(context, image.size)
        )

        val savings = image.estimatedSavings
        estimatedSavings.text = if (savings != null && savings > 0) {
            context.getString(
                R.string.squeezer_estimated_savings_format,
                Formatter.formatShortFileSize(context, savings)
            )
        } else {
            context.getString(R.string.squeezer_no_savings_expected)
        }

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        override val image: CompressibleImage,
        val onItemClicked: (Item) -> Unit,
        val onPreviewClicked: (Item) -> Unit,
    ) : SqueezerListAdapter.Item {

        override val itemSelectionKey: String
            get() = image.identifier.value

        override val stableId: Long = image.identifier.hashCode().toLong()
    }
}
