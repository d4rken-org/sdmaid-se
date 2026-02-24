package eu.darken.sdmse.common.lists.selection

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView

class ItemSelectionLookup(
    private val list: RecyclerView
) : ItemDetailsLookup<String>() {
    override fun getItemDetails(event: MotionEvent): ItemDetails<String>? {
        val view = list.findChildViewUnder(event.x, event.y)
        if (view != null) {
            val viewHolder = list.getChildViewHolder(view)
            if (viewHolder is SelectableVH) {
                return viewHolder.getItemDetails()
            }
        }
        return null
    }
}
