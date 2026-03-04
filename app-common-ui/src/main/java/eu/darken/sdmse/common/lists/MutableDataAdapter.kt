package eu.darken.sdmse.common.lists

import androidx.recyclerview.widget.RecyclerView

interface MutableDataAdapter<T> {
    val data: MutableList<T>
}

fun <X, T> X.update(
    newData: List<T>?,
    notify: Boolean = true
) where X : MutableDataAdapter<T>, X : RecyclerView.Adapter<*> {
    data.clear()
    if (newData != null) data.addAll(newData)
    if (notify) notifyDataSetChanged()
}