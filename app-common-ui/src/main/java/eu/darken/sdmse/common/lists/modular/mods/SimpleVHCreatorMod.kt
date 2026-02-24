package eu.darken.sdmse.common.lists.modular.mods

import android.view.ViewGroup
import eu.darken.sdmse.common.lists.modular.ModularAdapter

class SimpleVHCreatorMod<HolderT> constructor(
    private val viewType: Int = 0,
    private val factory: (ViewGroup) -> HolderT
) : ModularAdapter.Module.Creator<HolderT> where HolderT : ModularAdapter.VH {

    override fun onCreateModularVH(adapter: ModularAdapter<HolderT>, parent: ViewGroup, viewType: Int): HolderT? {
        if (this.viewType != viewType) return null
        return factory.invoke(parent)
    }
}