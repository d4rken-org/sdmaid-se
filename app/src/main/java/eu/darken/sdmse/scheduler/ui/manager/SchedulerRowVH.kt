package eu.darken.sdmse.scheduler.ui.manager

import android.view.ViewGroup
import androidx.core.view.isGone
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.databinding.SchedulerManagerListItemBinding
import eu.darken.sdmse.scheduler.core.Schedule
import java.time.Duration


class SchedulerRowVH(parent: ViewGroup) :
    SchedulerAdapter.BaseVH<SchedulerRowVH.Item, SchedulerManagerListItemBinding>(
        R.layout.scheduler_manager_list_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerManagerListItemBinding.bind(itemView) }

    override val onBindData: SchedulerManagerListItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val schedule = item.schedule

        title.text = schedule.label

        val days = Duration.ofMillis(schedule.repeatIntervalMs).toDays().toString()

        val hourTxt = schedule.hour.toString().padStart(2, '0')
        val minuteTxt = schedule.minute.toString().padStart(2, '0')
        val time = "$hourTxt:$minuteTxt"
        subtitle.text = getString(R.string.scheduler_current_schedule_x_at_x, days, time)

        enabledToggle.apply {
            setOnClickListener(null)
            isChecked = schedule.isEnabled
            setOnCheckedChangeListener { _, _ -> item.onToggle() }
        }

        optionsContainer.isGone = schedule.isEnabled

        editAction.apply {
            isGone = schedule.isEnabled
            setOnClickListener { item.onEdit() }
        }

        removeAction.apply {
            isGone = schedule.isEnabled
            setOnClickListener { item.onRemove() }
        }
    }

    data class Item(
        val schedule: Schedule,
        val onEdit: () -> Unit,
        val onToggle: () -> Unit,
        val onRemove: () -> Unit,
    ) : SchedulerAdapter.Item {

        override val stableId: Long = schedule.id.hashCode().toLong()
    }

}