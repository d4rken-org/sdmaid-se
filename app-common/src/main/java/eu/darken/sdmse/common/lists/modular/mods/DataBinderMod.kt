package eu.darken.sdmse.common.lists.modular.mods

import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.modular.ModularAdapter

class DataBinderMod<ItemT, HolderT> constructor(
    private val data: List<ItemT>,
    private val customBinder: (
        (adapter: ModularAdapter<HolderT>, vh: HolderT, pos: Int, payload: MutableList<Any>) -> Unit
    )? = null
) : ModularAdapter.Module.Binder<HolderT> where HolderT : BindableVH<ItemT, ViewBinding>, HolderT : ModularAdapter.VH {

    override fun onBindModularVH(adapter: ModularAdapter<HolderT>, vh: HolderT, pos: Int, payloads: MutableList<Any>) {
        customBinder?.invoke(adapter, vh, pos, mutableListOf()) ?: vh.bind(data[pos], payloads)
    }
}