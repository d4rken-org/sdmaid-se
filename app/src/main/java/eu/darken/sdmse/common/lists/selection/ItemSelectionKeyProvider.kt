package eu.darken.sdmse.common.lists.selection

import androidx.recyclerview.selection.ItemKeyProvider
import eu.darken.sdmse.common.lists.DataAdapter

class ItemSelectionKeyProvider(
    private val adapter: DataAdapter<*>
) : ItemKeyProvider<String>(SCOPE_MAPPED) {
    override fun getKey(position: Int): String? {
        return (adapter.data[position] as? SelectableItem)?.itemSelectionKey
    }

    override fun getPosition(key: String): Int {
        return adapter.data.indexOfFirst { it is SelectableItem && it.itemSelectionKey == key }
    }
}