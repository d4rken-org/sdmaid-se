package eu.darken.sdmse.common.debug.recorder.ui

import android.text.format.Formatter
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.databinding.DebugRecorderLogfileItemBinding
import java.io.File
import javax.inject.Inject


class LogFileAdapter @Inject constructor() :
    ModularAdapter<LogFileAdapter.BaseVH<LogFileAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<LogFileAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod(data))
        addMod(TypedVHCreatorMod({ data[it] is Entry.Item }) { Entry(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

    class Entry(parent: ViewGroup) :
        BaseVH<Entry.Item, DebugRecorderLogfileItemBinding>(
            R.layout.debug_recorder_logfile_item,
            parent
        ) {

        override val viewBinding = lazy { DebugRecorderLogfileItemBinding.bind(itemView) }

        override val onBindData: DebugRecorderLogfileItemBinding.(
            item: Item,
            payloads: List<Any>
        ) -> Unit = binding { item ->
            info.text = item.path.name
            path.text = item.path.path
            size.apply {
                text = "Size: "
                append(item.size?.let { Formatter.formatShortFileSize(context, it) } ?: "?")
            }
        }

        data class Item(
            val path: File,
            val size: Long? = null,
        ) : LogFileAdapter.Item {
            override val stableId: Long = path.hashCode().toLong()
        }
    }
}