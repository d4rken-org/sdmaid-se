package eu.darken.sdmse.common.lists.selection

import androidx.recyclerview.selection.SelectionTracker
import eu.darken.sdmse.common.lists.modular.ModularAdapter

class ItemSelectionMod constructor(
    private val tracker: SelectionTracker<String>,
) : ModularAdapter.Module.Binder<ModularAdapter.VH> {

    override fun onBindModularVH(
        adapter: ModularAdapter<ModularAdapter.VH>,
        vh: ModularAdapter.VH,
        pos: Int,
        payloads: MutableList<Any>
    ) {
        if (vh !is SelectableVH) return

        vh.updatedSelectionState(tracker.isSelected(vh.itemSelectionKey))

        vh.itemSelectionKey
            ?.let { key ->
                vh.itemView.setOnLongClickListener {
                    tracker.select(key)
                    true
                }
            }
            ?: vh.itemView.setOnLongClickListener(null)

    }
}