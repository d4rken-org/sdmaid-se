package eu.darken.sdmse.common.debug.logviewer.ui

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.logging.Logging
import eu.darken.sdmse.common.debug.logviewer.core.LogViewLogger
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.databinding.DebugLogviewerRowBinding
import javax.inject.Inject


class LogViewerAdapter @Inject constructor() :
    ModularAdapter<LogViewerAdapter.BaseVH<LogViewerAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<LogViewerAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is LogViewerRow.Item }) { LogViewerRow(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new ->
                if (new::class.isInstance(old)) new else null
            }
    }

    class LogViewerRow(parent: ViewGroup) :
        BaseVH<LogViewerRow.Item, DebugLogviewerRowBinding>(R.layout.debug_logviewer_row, parent) {

        override val viewBinding = lazy { DebugLogviewerRowBinding.bind(itemView) }

        override val onBindData: DebugLogviewerRowBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = binding { item ->
            val row = item.row
            primary.text = "${row.priority.toLabel()} | ${row.message}"
        }

        private fun Logging.Priority.toLabel(): String = when (this) {
            Logging.Priority.VERBOSE -> "V"
            Logging.Priority.DEBUG -> "D"
            Logging.Priority.INFO -> "I"
            Logging.Priority.WARN -> "W"
            Logging.Priority.ERROR -> "E"
            Logging.Priority.ASSERT -> "WTF"
        }

        data class Item(
            val row: LogViewLogger.Item,
        ) : LogViewerAdapter.Item {

            override val stableId: Long = row.hashCode().toLong()
        }

    }
}