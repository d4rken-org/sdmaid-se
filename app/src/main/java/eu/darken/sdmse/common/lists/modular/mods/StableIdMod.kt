package eu.darken.sdmse.common.lists.modular.mods

import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.modular.ModularAdapter

class StableIdMod<ItemT : DifferItem> constructor(
    private val data: List<ItemT>,
    private val customResolver: (position: Int) -> Long = {
        (data[it] as? DifferItem)?.stableId ?: RecyclerView.NO_ID
    }
) : ModularAdapter.Module.ItemId, ModularAdapter.Module.Setup {

    override fun onAdapterReady(adapter: ModularAdapter<*>) {
        adapter.setHasStableIds(true)
    }

    override fun getItemId(adapter: ModularAdapter<*>, position: Int): Long? {
        return customResolver.invoke(position)
    }
}
