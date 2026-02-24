package eu.darken.sdmse.common.lists.differ

import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.StableIdMod

class AsyncDiffer<A, T : DifferItem> internal constructor(
    adapter: A,
    compareItem: (T, T) -> Boolean = { i1, i2 -> i1.stableId == i2.stableId },
    compareItemContent: (T, T) -> Boolean = { i1, i2 -> i1 == i2 },
    determinePayload: (T, T) -> Any? = { i1, i2 ->
        when {
            i1::class == i2::class -> i1.payloadProvider?.invoke(i1, i2)
            else -> null
        }
    }
) where A : HasAsyncDiffer<T>, A : ModularAdapter<*> {
    private val callback = object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = compareItem(oldItem, newItem)
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = compareItemContent(oldItem, newItem)
        override fun getChangePayload(oldItem: T, newItem: T): Any? = determinePayload(oldItem, newItem)
    }

    @Volatile
    private var snapshot: List<T> = emptyList()
    private val listDiffer = AsyncListDiffer(adapter, callback)

    /**
     * Returns a snapshot of the current list data.
     * A new [List] reference is produced on each [submitUpdate] or [resetTo] commit,
     * so callers may rely on reference identity (`===`) for cheap change detection.
     */
    val currentList: List<T>
        get() = snapshot

    init {
        adapter.addMod(position = 0, mod = StableIdMod(data = { snapshot }))
    }

    fun submitUpdate(newData: List<T>, onCommit: (() -> Unit)? = null) {
        listDiffer.submitList(newData) {
            snapshot = ArrayList(newData)
            onCommit?.invoke()
        }
    }

    /**
     * Resets the internal state to match [newData] without calculating diff-based moves.
     * Uses submitList(null) then submitList(newData) to avoid move operations.
     * Call this after manual notifyItemMoved operations to sync state.
     */
    fun resetTo(newData: List<T>, onCommit: (() -> Unit)? = null) {
        listDiffer.submitList(null)
        listDiffer.submitList(newData) {
            snapshot = ArrayList(newData)
            onCommit?.invoke()
        }
    }
}
