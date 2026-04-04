package eu.darken.sdmse.squeezer.ui.list

import android.graphics.Bitmap
import android.text.format.Formatter
import android.view.ViewGroup
import eu.darken.sdmse.squeezer.R
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.common.replaceLast
import eu.darken.sdmse.squeezer.core.CompressibleMedia
import eu.darken.sdmse.squeezer.databinding.SqueezerListLinearItemBinding


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
        val media = item.media

        previewImage.apply {
            loadFilePreview(media.lookup) {
                bitmapConfig(Bitmap.Config.ARGB_8888)
            }
            setOnClickListener { item.onPreviewClicked(item) }
        }

        filename.text = media.lookup.name
        filepath.text = media.lookup.userReadablePath.get(context).replaceLast(media.lookup.name, "")

        currentSize.text = context.getString(
            eu.darken.sdmse.squeezer.R.string.squeezer_current_size_format,
            Formatter.formatShortFileSize(context, media.size)
        )

        val savings = media.estimatedSavings
        estimatedSavings.text = if (savings != null && savings > 0) {
            context.getString(
                eu.darken.sdmse.squeezer.R.string.squeezer_estimated_savings_format,
                Formatter.formatShortFileSize(context, savings)
            )
        } else {
            context.getString(eu.darken.sdmse.squeezer.R.string.squeezer_no_savings_expected)
        }

        root.setOnClickListener { item.onItemClicked(item) }
    }

    data class Item(
        override val media: CompressibleMedia,
        val onItemClicked: (Item) -> Unit,
        val onPreviewClicked: (Item) -> Unit,
    ) : SqueezerListAdapter.Item {

        override val itemSelectionKey: String
            get() = media.identifier.value

        override val stableId: Long = media.identifier.hashCode().toLong()
    }
}
