package eu.darken.sdmse.common.lists

import androidx.viewbinding.ViewBinding

interface BindableVH<ItemT, ViewBindingT : ViewBinding> {

    val viewBinding: Lazy<ViewBindingT>

    val onBindData: ViewBindingT.(item: ItemT, payloads: List<Any>) -> Unit

    fun bind(item: ItemT, payloads: MutableList<Any> = mutableListOf()) = with(viewBinding.value) {
        onBindData(item, payloads)
    }
}

@Suppress("unused")
inline fun <reified ItemT, ViewBindingT : ViewBinding> BindableVH<ItemT, ViewBindingT>.binding(
    payload: Boolean = true,
    crossinline block: ViewBindingT.(ItemT) -> Unit,
): ViewBindingT.(item: ItemT, payloads: List<Any>) -> Unit = { item: ItemT, payloads: List<Any> ->
    val newestItem = when (payload) {
        true -> payloads.filterIsInstance<ItemT>().lastOrNull() ?: item
        false -> item
    }
    block(newestItem)
}