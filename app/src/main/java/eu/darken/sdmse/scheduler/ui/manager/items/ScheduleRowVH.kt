package eu.darken.sdmse.scheduler.ui.manager.items

import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.setChecked2
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.SchedulerManagerListScheduleItemBinding
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.ui.manager.SchedulerAdapter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class ScheduleRowVH(parent: ViewGroup) :
    SchedulerAdapter.BaseVH<ScheduleRowVH.Item, SchedulerManagerListScheduleItemBinding>(
        R.layout.scheduler_manager_list_schedule_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerManagerListScheduleItemBinding.bind(itemView) }

    override val onBindData: SchedulerManagerListScheduleItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->
        val schedule = item.schedule

        title.text = schedule.label

        subtitle.apply {
            val days = schedule.repeatInterval.toDays()
            val daysText = getQuantityString(R.plurals.scheduler_schedule_repeat_x_days, days.toInt())

            val hourTxt = schedule.hour.toString().padStart(2, '0')
            val minuteTxt = schedule.minute.toString().padStart(2, '0')
            val time = "$hourTxt:$minuteTxt"

            text = getString(R.string.scheduler_current_schedule_x_at_x, daysText, time)
        }

        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        val now = Instant.now()
        primary.apply {
            isVisible = schedule.scheduledAt != null
            text = schedule.calcExecutionEta(now, false)?.let { eta ->
                val next = now.plus(eta).toSystemTimezone().format(formatter)
                getString(R.string.scheduler_schedule_next_at_x, next)
            }
        }

        secondary.apply {
            isVisible = schedule.executedAt != null
            text = schedule.executedAt?.let {
                val local = it.toSystemTimezone().format(formatter)
                getString(R.string.scheduler_schedule_last_at_x, local)
            }
        }

        enabledToggle.apply {
            setChecked2(schedule.isEnabled)
            setOnClickListener { item.onToggle() }
            text = when (schedule.isEnabled) {
                true -> getString(R.string.scheduler_schedule_toggle_enabled)
                else -> getString(R.string.scheduler_schedule_toggle_disabled)
            }
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

        toolCorpsefinderToggle.apply {
            setChecked2(schedule.useCorpseFinder)
            setOnCheckedChangeListener { _, _ -> item.onToggleCorpseFinder() }
        }
        toolSystemcleanerToggle.apply {
            setChecked2(schedule.useSystemCleaner)
            setOnCheckedChangeListener { _, _ -> item.onToggleSystemCleaner() }
        }
        toolAppcleanerToggle.apply {
            setChecked2(schedule.useAppCleaner)
            setOnCheckedChangeListener { _, _ -> item.onToggleAppCleaner() }
        }

        commandsContainer.isVisible = item.showCommands
        commandsInfo.text = if (schedule.commandsAfterSchedule.isNotEmpty()) {
            schedule.commandsAfterSchedule.mapIndexed { index, s -> "#$index: $s" }.joinToString("\n")
        } else {
            getString(R.string.scheduler_commands_after_schedule_desc)
        }
        commandsEditAction.setOnClickListener { item.onEditFinalCommands() }
    }

    data class Item(
        val schedule: Schedule,
        val showCommands: Boolean,
        val onEdit: () -> Unit,
        val onToggle: () -> Unit,
        val onRemove: () -> Unit,
        val onToggleCorpseFinder: () -> Unit,
        val onToggleSystemCleaner: () -> Unit,
        val onToggleAppCleaner: () -> Unit,
        val onEditFinalCommands: () -> Unit,
    ) : SchedulerAdapter.Item {

        override val stableId: Long = schedule.id.hashCode().toLong()
    }
}