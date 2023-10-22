package eu.darken.sdmse.scheduler.ui

import android.view.ViewGroup
import androidx.core.view.isVisible
import eu.darken.sdmse.R
import eu.darken.sdmse.common.lists.binding
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.databinding.SchedulerDashboardItemBinding
import eu.darken.sdmse.main.core.taskmanager.TaskManager
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.scheduler.core.SchedulerManager
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


class SchedulerDashCardVH(parent: ViewGroup) :
    DashboardAdapter.BaseVH<SchedulerDashCardVH.Item, SchedulerDashboardItemBinding>(
        R.layout.scheduler_dashboard_item,
        parent
    ) {

    override val viewBinding = lazy { SchedulerDashboardItemBinding.bind(itemView) }

    override val onBindData: SchedulerDashboardItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding { item ->

        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

        subtitle.isVisible = item.schedulerState.schedules.none { it.isEnabled }
        val now = Instant.now()
        val nextSchedule = item.schedulerState.schedules
            .filter { it.isEnabled }
            .minByOrNull { it.calcExecutionEta(now, false)!! }
        executionNextLabel.isVisible = nextSchedule != null
        executionNextValue.apply {
            isVisible = nextSchedule != null
            text = nextSchedule?.let {
                val nextAt = now
                    .plus(it.calcExecutionEta(now, false))
                    .toSystemTimezone().format(formatter)
                "$nextAt (${it.label})"
            }
        }

        val lastSchedule = item.schedulerState.schedules
            .filter { it.executedAt != null }
            .maxByOrNull { it.executedAt!! }
        executionLastLabel.isVisible = lastSchedule != null
        executionLastValue.apply {
            isVisible = lastSchedule != null
            text = lastSchedule?.let {
                "${it.executedAt?.toSystemTimezone()?.format(formatter)} (${it.label})"
            }
        }

        root.setOnClickListener { manageAction.performClick() }
        manageAction.apply { setOnClickListener { item.onManageClicked() } }
    }

    data class Item(
        val schedulerState: SchedulerManager.State,
        val taskState: TaskManager.State,
        val onManageClicked: () -> Unit,
    ) : DashboardAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }

}