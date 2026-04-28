package eu.darken.sdmse.scheduler.ui.manager.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Alarm
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.AccessTime
import androidx.compose.material.icons.twotone.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.scheduler.R
import eu.darken.sdmse.scheduler.core.Schedule
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ScheduleRow(
    schedule: Schedule,
    showCommands: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onToggleCorpseFinder: () -> Unit,
    onToggleSystemCleaner: () -> Unit,
    onToggleAppCleaner: () -> Unit,
    onEditCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = remember(schedule.id) { Instant.now() }
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val days = schedule.repeatInterval.toDays().toInt()
    val daysText = pluralStringResource(R.plurals.scheduler_schedule_repeat_x_days, days, days)
    val timeText = "%02d:%02d".format(schedule.hour, schedule.minute)

    val nextEtaText: String? = remember(
        schedule.scheduledAt,
        schedule.hour,
        schedule.minute,
        schedule.repeatInterval,
        schedule.userZone,
    ) {
        if (schedule.scheduledAt == null) return@remember null
        runCatching {
            schedule.calcExecutionEta(now, reschedule = false)?.let { eta ->
                now.plus(eta).toSystemTimezone().format(formatter)
            }
        }.getOrNull()
    }
    val lastExecutedText = schedule.executedAt?.toSystemTimezone()?.format(formatter)
    val isEnabled = schedule.isEnabled
    val rowEnabled = !isEnabled

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.TwoTone.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = schedule.label.ifBlank { stringResource(R.string.scheduler_schedule_default_name) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.scheduler_current_schedule_x_at_x, daysText, timeText),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            if (nextEtaText != null || lastExecutedText != null) {
                Spacer(modifier = Modifier.padding(vertical = 8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (nextEtaText != null) {
                            ExecutionInfoLine(
                                icon = Icons.TwoTone.AccessTime,
                                text = stringResource(R.string.scheduler_schedule_next_at_x, nextEtaText),
                            )
                        }
                        if (lastExecutedText != null) {
                            if (nextEtaText != null) Spacer(modifier = Modifier.padding(vertical = 2.dp))
                            ExecutionInfoLine(
                                icon = Icons.TwoTone.History,
                                text = stringResource(R.string.scheduler_schedule_last_at_x, lastExecutedText),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.scheduler_schedule_toggle_enabled
                        else R.string.scheduler_schedule_toggle_disabled,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            ToolToggleRow(
                label = stringResource(CommonR.string.corpsefinder_tool_name),
                checked = schedule.useCorpseFinder,
                enabled = rowEnabled,
                onCheckedChange = onToggleCorpseFinder,
            )
            ToolToggleRow(
                label = stringResource(CommonR.string.systemcleaner_tool_name),
                checked = schedule.useSystemCleaner,
                enabled = rowEnabled,
                onCheckedChange = onToggleSystemCleaner,
            )
            ToolToggleRow(
                label = stringResource(CommonR.string.appcleaner_tool_name),
                checked = schedule.useAppCleaner,
                enabled = rowEnabled,
                onCheckedChange = onToggleAppCleaner,
            )

            if (showCommands) {
                Spacer(modifier = Modifier.padding(vertical = 4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.padding(vertical = 4.dp))
                CommandsRow(
                    schedule = schedule,
                    enabled = rowEnabled,
                    onEditCommands = onEditCommands,
                )
            }

            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onRemove,
                    enabled = rowEnabled,
                ) {
                    Icon(
                        Icons.TwoTone.Cancel,
                        contentDescription = null,
                        tint = if (rowEnabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(CommonR.string.general_remove_action),
                        color = if (rowEnabled) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
                OutlinedButton(
                    onClick = onEdit,
                    enabled = rowEnabled,
                ) {
                    Icon(Icons.TwoTone.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.scheduler_edit_schedule_action))
                }
            }
        }
    }
}

@Composable
private fun ToolToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = { onCheckedChange() })
    }
}

@Composable
private fun CommandsRow(
    schedule: Schedule,
    enabled: Boolean,
    onEditCommands: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.scheduler_commands_after_schedule_label),
                style = MaterialTheme.typography.bodyMedium,
            )
            val commandsBody = if (schedule.commandsAfterSchedule.isNotEmpty()) {
                schedule.commandsAfterSchedule.mapIndexed { index, cmd -> "#$index: $cmd" }.joinToString("\n")
            } else {
                stringResource(R.string.scheduler_commands_after_schedule_desc)
            }
            Text(
                text = commandsBody,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        IconButton(
            onClick = onEditCommands,
            enabled = enabled,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.TwoTone.Edit,
                contentDescription = stringResource(CommonR.string.general_edit_action),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ExecutionInfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview2
@Composable
private fun ScheduleRowDisabledPreview() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            ScheduleRow(
                schedule = Schedule(
                    id = "preview",
                    label = "Daily clean",
                    hour = 22,
                    minute = 0,
                    useCorpseFinder = true,
                    useSystemCleaner = true,
                    useAppCleaner = false,
                ),
                showCommands = false,
                onEdit = {},
                onToggle = {},
                onRemove = {},
                onToggleCorpseFinder = {},
                onToggleSystemCleaner = {},
                onToggleAppCleaner = {},
                onEditCommands = {},
            )
        }
    }
}

@Preview2
@Composable
private fun ScheduleRowEnabledWithCommandsPreview() {
    PreviewWrapper {
        Box(modifier = Modifier.padding(16.dp)) {
            ScheduleRow(
                schedule = Schedule(
                    id = "preview-2",
                    label = "Weekly maintenance",
                    hour = 3,
                    minute = 30,
                    useCorpseFinder = true,
                    useSystemCleaner = false,
                    useAppCleaner = true,
                    scheduledAt = Instant.parse("2026-04-26T00:00:00Z"),
                    commandsAfterSchedule = listOf("reboot", "echo done"),
                ),
                showCommands = true,
                onEdit = {},
                onToggle = {},
                onRemove = {},
                onToggleCorpseFinder = {},
                onToggleSystemCleaner = {},
                onToggleAppCleaner = {},
                onEditCommands = {},
            )
        }
    }
}
