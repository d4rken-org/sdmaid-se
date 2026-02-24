package eu.darken.sdmse.common.lists.differ

import androidx.recyclerview.widget.RecyclerView
import eu.darken.sdmse.common.lists.modular.ModularAdapter


fun <X, T> X.update(newData: List<T>?, onCommit: (() -> Unit)? = null)
        where X : HasAsyncDiffer<T>, X : RecyclerView.Adapter<*> {

    asyncDiffer.submitUpdate(newData ?: emptyList(), onCommit)
}

/**
 * Resets the differ's internal state to [newData] without diff-based move calculations.
 * Use after manual notifyItemMoved operations (e.g., drag-and-drop) to sync state.
 */
fun <X, T> X.resetTo(newData: List<T>?, onCommit: (() -> Unit)? = null)
        where X : HasAsyncDiffer<T>, X : RecyclerView.Adapter<*> {

    asyncDiffer.resetTo(newData ?: emptyList(), onCommit)
}

fun <A, T : DifferItem> A.setupDiffer(): AsyncDiffer<A, T>
        where A : HasAsyncDiffer<T>, A : ModularAdapter<*> =
    AsyncDiffer(this)
