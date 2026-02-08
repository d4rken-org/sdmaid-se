package eu.darken.sdmse.common.lists.selection

import androidx.recyclerview.selection.ItemKeyProvider
import eu.darken.sdmse.common.lists.DataAdapter

class ItemSelectionKeyProvider(
    private val adapter: DataAdapter<out SelectableItem>
) : ItemKeyProvider<String>(SCOPE_MAPPED) {

    private var cachedData: List<SelectableItem>? = null
    private var positionMap: Map<String, Int> = emptyMap()

    private fun ensureCache(): Map<String, Int> {
        val currentData = adapter.data
        if (currentData !== cachedData) {
            cachedData = currentData
            positionMap = buildMap {
                currentData.forEachIndexed { index, item ->
                    item.itemSelectionKey?.let { key -> put(key, index) }
                }
            }
        }
        return positionMap
    }

    override fun getKey(position: Int): String? {
        return adapter.data.getOrNull(position)?.itemSelectionKey
    }

    override fun getPosition(key: String): Int {
        return ensureCache()[key] ?: -1
    }
}
