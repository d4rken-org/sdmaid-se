package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessTime
import androidx.compose.material.icons.twotone.AccessTimeFilled
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.main.core.taskmanager.TaskSubmitter
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton

import eu.darken.sdmse.scheduler.R as SchedulerR
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.SchedulerManager
import java.time.Instant
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
    val now = remember(item.schedulerState) { Instant.now() }

    val nextExecution: Pair<Schedule, Instant>? = remember(item.schedulerState, now) {
        item.schedulerState.schedules
            .asSequence()
            .filter { it.isEnabled }
            .mapNotNull { schedule ->
                runCatching { schedule.calcExecutionEta(now, reschedule = false) }
                    .getOrNull()
                    ?.let { eta -> schedule to now.plus(eta) }
            }
            .minByOrNull { (_, at) -> at }
    }
    val lastSchedule: Schedule? = remember(item.schedulerState) {
        item.schedulerState.schedules
            .filter { it.executedAt != null }
            .maxByOrNull { it.executedAt!! }
    }

    val noActive = item.schedulerState.schedules.none { it.isEnabled }

    DashboardCard(onClick = item.onManageClicked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.Alarm,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_label),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (noActive) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(SchedulerR.string.scheduler_no_active_schedules_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (nextExecution != null || lastSchedule != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    nextExecution?.let { (schedule, at) ->
                        ExecutionInfoRow(
                            icon = Icons.TwoTone.AccessTime,
                            label = stringResource(SchedulerR.string.scheduler_execution_label),
                            time = at.toSystemTimezone().format(formatter),
                            scheduleLabel = schedule.label.takeIf { it.isNotBlank() },
                        )
                    }
                    if (nextExecution != null && lastSchedule != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    lastSchedule?.let { schedule ->
                        ExecutionInfoRow(
                            icon = Icons.TwoTone.History,
                            label = stringResource(SchedulerR.string.scheduler_execution_last_label),
                            time = schedule.executedAt!!.toSystemTimezone().format(formatter),
                            scheduleLabel = schedule.label.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DashboardFlatActionButton(onClick = item.onManageClicked) {
                Icon(
                    imageVector = Icons.TwoTone.AccessTimeFilled,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = stringResource(CommonR.string.general_manage_action))
            }
        }
    }
}

@Composable
private fun ExecutionInfoRow(
    icon: ImageVector,
    label: String,
    time: String,
    scheduleLabel: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (scheduleLabel != null) {
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SchedulerDashboardCardEmptyPreview() {
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

@Preview2
@Composable
private fun SchedulerDashboardCardActivePreview() {
    PreviewWrapper {
        SchedulerDashboardCard(
            item = SchedulerDashboardCardItem(
                schedulerState = SchedulerManager.State(
                    schedules = setOf(
                        Schedule(
                            id = "preview-next",
                            label = "Daily clean",
                            hour = 22,
                            minute = 0,
                            scheduledAt = Instant.parse("2026-05-01T00:00:00Z"),
                            executedAt = Instant.parse("2026-05-10T20:00:00Z"),
                        ),
                    ),
                ),
                taskState = TaskSubmitter.State(),
                onManageClicked = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun SchedulerDashboardCardHistoryOnlyPreview() {
    PreviewWrapper {
        SchedulerDashboardCard(
            item = SchedulerDashboardCardItem(
                schedulerState = SchedulerManager.State(
                    schedules = setOf(
                        Schedule(
                            id = "preview-past",
                            label = "",
                            hour = 3,
                            minute = 30,
                            executedAt = Instant.parse("2026-05-09T01:30:00Z"),
                        ),
                    ),
                ),
                taskState = TaskSubmitter.State(),
                onManageClicked = {},
            ),
        )
    }
}
