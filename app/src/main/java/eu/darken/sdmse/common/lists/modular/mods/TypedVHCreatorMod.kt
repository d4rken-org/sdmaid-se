package eu.darken.sdmse.common.lists.modular.mods

import android.view.ViewGroup
import eu.darken.sdmse.common.lists.modular.ModularAdapter

class TypedVHCreatorMod<HolderT> constructor(
    private val typeResolver: (Int) -> Boolean,
    private val factory: (ViewGroup) -> HolderT
) : ModularAdapter.Module.Typing,
    ModularAdapter.Module.Creator<HolderT> where HolderT : ModularAdapter.VH {

    private fun ModularAdapter<*>.determineOurViewType(): Int {
        val typingModules = mods.filterIsInstance(ModularAdapter.Module.Typing::class.java)
        return typingModules.indexOf(this@TypedVHCreatorMod)
    }

    override fun onGetItemType(adapter: ModularAdapter<*>, pos: Int): Int? {
        return if (typeResolver.invoke(pos)) adapter.determineOurViewType() else null
    }

    override fun onCreateModularVH(
        adapter: ModularAdapter<HolderT>,
        parent: ViewGroup,
        viewType: Int
    ): HolderT? {
        if (adapter.determineOurViewType() != viewType) return null
        return factory.invoke(parent)
    }
}
