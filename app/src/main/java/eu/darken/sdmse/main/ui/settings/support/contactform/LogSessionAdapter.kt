package eu.darken.sdmse.main.ui.settings.support.contactform

import android.text.format.DateUtils
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
import eu.darken.sdmse.databinding.SupportContactLogSessionItemBinding
import java.io.File
import javax.inject.Inject

class LogSessionAdapter @Inject constructor() :
    ModularAdapter<LogSessionAdapter.BaseVH<LogSessionAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<LogSessionAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod({ data }))
        addMod(TypedVHCreatorMod({ data[it] is SessionVH.Item }) { SessionVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

    class SessionVH(parent: ViewGroup) :
        BaseVH<SessionVH.Item, SupportContactLogSessionItemBinding>(
            R.layout.support_contact_log_session_item,
            parent,
        ) {

        override val viewBinding = lazy { SupportContactLogSessionItemBinding.bind(itemView) }

        override val onBindData: SupportContactLogSessionItemBinding.(
            item: Item,
            payloads: List<Any>,
        ) -> Unit = binding { item ->
            radio.isChecked = item.isSelected
            timeLabel.text = DateUtils.getRelativeTimeSpanString(
                item.lastModified,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            sizeLabel.text = Formatter.formatShortFileSize(context, item.size)
            itemView.setOnClickListener { item.onSelected() }
            deleteAction.setOnClickListener { item.onDelete() }
        }

        data class Item(
            val zipFile: File,
            val size: Long,
            val lastModified: Long,
            val isSelected: Boolean,
            val onSelected: () -> Unit,
            val onDelete: () -> Unit,
        ) : LogSessionAdapter.Item {
            override val stableId: Long = zipFile.hashCode().toLong()
        }
    }
}
