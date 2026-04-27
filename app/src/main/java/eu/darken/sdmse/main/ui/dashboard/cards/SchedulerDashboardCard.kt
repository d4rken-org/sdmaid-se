package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton

import eu.darken.sdmse.scheduler.R as SchedulerR
import eu.darken.sdmse.scheduler.core.SchedulerManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class SchedulerDashboardCardItem(
    val schedulerState: SchedulerManager.State,
    val taskState: TaskSubmitter.State,
    val onManageClicked: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun SchedulerDashboardCard(item: SchedulerDashboardCardItem) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val now = Instant.now()
    val nextSchedule = item.schedulerState.schedules
        .filter { it.isEnabled }
        .minByOrNull { it.calcExecutionEta(now, false)!! }
    val lastSchedule = item.schedulerState.schedules
        .filter { it.executedAt != null }
        .maxByOrNull { it.executedAt!! }

    DashboardCard(onClick = item.onManageClicked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_alarm_check_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_label),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (item.schedulerState.schedules.none { it.isEnabled }) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_no_active_schedules_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        nextSchedule?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_execution_label),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = now.plus(it.calcExecutionEta(now, false)).atZone(ZoneId.systemDefault()).format(formatter) + " (${it.label})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        lastSchedule?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_execution_last_label),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "${it.executedAt?.atZone(ZoneId.systemDefault())?.format(formatter)} (${it.label})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DashboardFlatActionButton(onClick = item.onManageClicked) {
                Icon(
                    painter = painterResource(CommonR.drawable.ic_baseline_access_time_filled_24),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = stringResource(CommonR.string.general_manage_action))
            }
        }
    }
}

@Preview2
@Composable
private fun SchedulerDashboardCardPreview() {
    PreviewWrapper {
        SchedulerDashboardCard(
            item = SchedulerDashboardCardItem(
                schedulerState = SchedulerManager.State(
                    schedules = emptySet(),
                ),
                taskState = TaskSubmitter.State(),
                onManageClicked = {},
            ),
        )
    }
}
