package eu.darken.sdmse.main.ui.settings.support.sessions

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import eu.darken.sdmse.R
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.lists.BindableVH
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.lists.differ.AsyncDiffer
import eu.darken.sdmse.common.lists.differ.DifferItem
import eu.darken.sdmse.common.lists.differ.HasAsyncDiffer
import eu.darken.sdmse.common.lists.differ.setupDiffer
import eu.darken.sdmse.common.lists.modular.ModularAdapter
import eu.darken.sdmse.common.lists.modular.mods.DataBinderMod
import eu.darken.sdmse.common.lists.modular.mods.TypedVHCreatorMod
import eu.darken.sdmse.databinding.DebugLogSessionsItemBinding
import javax.inject.Inject
import eu.darken.sdmse.common.ui.R as UiR

class DebugLogSessionsAdapter @Inject constructor() :
    ModularAdapter<DebugLogSessionsAdapter.BaseVH<DebugLogSessionsAdapter.Item, ViewBinding>>(),
    HasAsyncDiffer<DebugLogSessionsAdapter.Item> {

    override val asyncDiffer: AsyncDiffer<*, Item> = setupDiffer()

    override fun getItemCount(): Int = data.size

    init {
        addMod(DataBinderMod({ data }))
        addMod(TypedVHCreatorMod({ data[it] is SessionItemVH.Item }) { SessionItemVH(it) })
    }

    abstract class BaseVH<D : Item, B : ViewBinding>(
        @LayoutRes layoutId: Int,
        parent: ViewGroup,
    ) : VH(layoutId, parent), BindableVH<D, B>

    interface Item : DifferItem {
        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }

    class SessionItemVH(parent: ViewGroup) :
        BaseVH<SessionItemVH.Item, DebugLogSessionsItemBinding>(
            R.layout.debug_log_sessions_item,
            parent,
        ) {

        override val viewBinding = lazy { DebugLogSessionsItemBinding.bind(itemView) }

        override val onBindData: DebugLogSessionsItemBinding.(
            item: Item,
            payloads: List<Any>,
        ) -> Unit = binding { item ->
            val session = item.session

            timeLabel.text = DateUtils.getRelativeTimeSpanString(
                session.createdAt.toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )

            when (session) {
                is DebugLogSession.Recording -> {
                    statusIcon.setImageResource(UiR.drawable.ic_bug_report)
                    sizeLabel.setText(R.string.debug_debuglog_recording_progress)
                    deleteAction.isEnabled = false
                    deleteAction.alpha = 0.3f
                }

                is DebugLogSession.Zipping -> {
                    statusIcon.setImageResource(UiR.drawable.ic_baseline_refresh_24)
                    sizeLabel.setText(R.string.debug_debuglog_sessions_zipping_label)
                    deleteAction.isEnabled = false
                    deleteAction.alpha = 0.3f
                }

                is DebugLogSession.Finished -> {
                    statusIcon.setImageResource(UiR.drawable.ic_file_chart_outline_24)
                    sizeLabel.text = Formatter.formatShortFileSize(context, session.compressedSize)
                    deleteAction.isEnabled = true
                    deleteAction.alpha = 1.0f
                }
            }

            deleteAction.setOnClickListener { item.onDelete() }
        }

        data class Item(
            val session: DebugLogSession,
            val onDelete: () -> Unit,
        ) : DebugLogSessionsAdapter.Item {
            override val stableId: Long = session.id.hashCode().toLong()
        }
    }
}
