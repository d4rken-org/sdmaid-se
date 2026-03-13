package eu.darken.sdmse.swiper.ui.status

import android.view.ViewGroup
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.coil.loadFilePreview
import eu.darken.sdmse.common.getColorForAttr
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.SimpleVHCreatorMod
import eu.darken.sdmse.common.lists.selection.SelectableItem
import eu.darken.sdmse.common.lists.selection.SelectableVH
import eu.darken.sdmse.databinding.SwiperStatusItemBinding
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem

class SwiperStatusAdapter :
    ModularAdapter<SwiperStatusAdapter.VH>(),
    HasAsyncDiffer<SwiperStatusAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    var onItemClick: ((SwipeItem) -> Unit)? = null
    var onResetClick: ((SwipeItem) -> Unit)? = null
    var onQuickKeepClick: ((SwipeItem) -> Unit)? = null
    var onQuickDeleteClick: ((SwipeItem) -> Unit)? = null

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod({ data }))
        addMod(SimpleVHCreatorMod { VH(it) })
    }

    inner class VH(parent: ViewGroup) : ModularAdapter.VH(
        R.layout.swiper_status_item,
        parent
    ), BindableVH<Item, SwiperStatusItemBinding>, SelectableVH {

        private var lastItem: Item? = null

        override val itemSelectionKey: String?
            get() = lastItem?.itemSelectionKey

        override fun updatedSelectionState(selected: Boolean) {
            itemView.isActivated = selected
        }

        override val viewBinding = lazy {
            SwiperStatusItemBinding.bind(itemView)
        }

        override val onBindData: SwiperStatusItemBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = { item, _ ->
            lastItem = item
            val swipeItem = item.swipeItem

            // Decision stripe instead of full row tint
            when (swipeItem.decision) {
                SwipeDecision.DELETE, SwipeDecision.DELETE_FAILED -> {
                    decisionStripe.visibility = android.view.View.VISIBLE
                    decisionStripe.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorError)
                    )
                }
                SwipeDecision.KEEP, SwipeDecision.DELETED -> {
                    decisionStripe.visibility = android.view.View.VISIBLE
                    decisionStripe.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    )
                }
                SwipeDecision.UNDECIDED -> {
                    decisionStripe.visibility = android.view.View.GONE
                }
            }

            val fileName = swipeItem.lookup.name
            name.text = fileName
            val fullPath = swipeItem.lookup.userReadablePath.get(context)
            path.text = fullPath.removeSuffix(fileName)

            val (size, _) = ByteFormatter.formatSize(context, swipeItem.lookup.size)
            meta.text = size

            thumbnail.loadFilePreview(swipeItem.lookup) {
                size(48, 48)
            }

            when (swipeItem.decision) {
                SwipeDecision.KEEP -> {
                    decisionIndicator.visibility = android.view.View.VISIBLE
                    decisionIndicator.setImageResource(R.drawable.ic_heart)
                    decisionIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    )
                }
                SwipeDecision.DELETE -> {
                    decisionIndicator.visibility = android.view.View.VISIBLE
                    decisionIndicator.setImageResource(R.drawable.ic_delete)
                    decisionIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorError)
                    )
                }
                SwipeDecision.UNDECIDED -> {
                    decisionIndicator.visibility = android.view.View.GONE
                }
                SwipeDecision.DELETED -> {
                    decisionIndicator.visibility = android.view.View.VISIBLE
                    decisionIndicator.setImageResource(R.drawable.ic_heart)
                    decisionIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorPrimary)
                    )
                }
                SwipeDecision.DELETE_FAILED -> {
                    decisionIndicator.visibility = android.view.View.VISIBLE
                    decisionIndicator.setImageResource(R.drawable.ic_error_outline)
                    decisionIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                        context.getColorForAttr(androidx.appcompat.R.attr.colorError)
                    )
                }
            }

            // Conditional button visibility
            val isUndecided = swipeItem.decision == SwipeDecision.UNDECIDED
            resetAction.visibility = if (isUndecided) android.view.View.GONE else android.view.View.VISIBLE
            quickKeepAction.visibility = if (isUndecided) android.view.View.VISIBLE else android.view.View.GONE
            quickDeleteAction.visibility = if (isUndecided) android.view.View.VISIBLE else android.view.View.GONE

            root.setOnClickListener { onItemClick?.invoke(swipeItem) }
            resetAction.setOnClickListener { onResetClick?.invoke(swipeItem) }
            quickKeepAction.setOnClickListener { onQuickKeepClick?.invoke(swipeItem) }
            quickDeleteAction.setOnClickListener { onQuickDeleteClick?.invoke(swipeItem) }
        }
    }

    data class Item(
        val swipeItem: SwipeItem,
    ) : DifferItem, SelectableItem {
        override val stableId: Long = swipeItem.id
        override val itemSelectionKey: String = swipeItem.id.toString()
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (old::class.isInstance(new)) new else null }
    }
}
