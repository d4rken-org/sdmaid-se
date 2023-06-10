package eu.darken.sdmse.common.lists.differ

import eu.darken.sdmse.common.lists.DataAdapter

interface HasAsyncDiffer<T : DifferItem> : DataAdapter<T> {

    override val data: List<T>
        get() = asyncDiffer.currentList

    val asyncDiffer: AsyncDiffer<*, T>

}