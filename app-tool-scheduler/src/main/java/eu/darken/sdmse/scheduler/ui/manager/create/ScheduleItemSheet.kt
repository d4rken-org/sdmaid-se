package eu.darken.sdmse.scheduler.ui.manager.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessTime
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.scheduler.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration

@Composable
fun ScheduleItemSheetHost(
    scheduleId: String,
    vm: ScheduleItemViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(scheduleId) { vm.setScheduleId(scheduleId) }

    ScheduleItemSheet(
        stateSource = vm.state,
        onLabelChanged = vm::updateLabel,
        onTimePicked = vm::updateTime,
        onIncreaseDays = vm::increaseDays,
        onDecreaseDays = vm::decreaseDays,
        onRepeatDaysChanged = vm::setRepeatDays,
        onSave = vm::saveSchedule,
    )
}

@Composable
internal fun ScheduleItemSheet(
    stateSource: Flow<ScheduleItemViewModel.State> = MutableStateFlow(ScheduleItemViewModel.State()),
    onLabelChanged: (String) -> Unit = {},
    onTimePicked: (Int, Int) -> Unit = { _, _ -> },
    onIncreaseDays: () -> Unit = {},
    onDecreaseDays: () -> Unit = {},
    onRepeatDaysChanged: (Int) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = ScheduleItemViewModel.State())
    var showTimePicker by remember { mutableStateOf(false) }

    var timeText by remember { mutableStateOf(formatTime(state.hour, state.minute)) }
    LaunchedEffect(state.hour, state.minute) {
        val parsed = parseTime(timeText)
        if (parsed?.first != state.hour || parsed?.second != state.minute) {
            timeText = formatTime(state.hour, state.minute)
        }
    }

    var daysText by remember { mutableStateOf(state.repeatInterval.toDays().toString()) }
    LaunchedEffect(state.repeatInterval) {
        val stateDays = state.repeatInterval.toDays().toInt()
        val typed = daysText.toIntOrNull()
        if (typed != null && typed != stateDays) {
            daysText = stateDays.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        OutlinedTextField(
            value = state.label.orEmpty(),
            onValueChange = onLabelChanged,
            label = { Text(stringResource(R.string.scheduler_schedule_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = timeText,
                onValueChange = { newText ->
                    timeText = newText
                    parseTime(newText)?.let { (h, m) -> onTimePicked(h, m) }
                },
                label = { Text(stringResource(R.string.scheduler_schedule_time_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    // Re-sync the field text from the parsed state, so a partial entry collapses to
                    // the last valid value when the user dismisses the keyboard.
                    timeText = formatTime(state.hour, state.minute)
                }),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = { showTimePicker = true }) {
                Icon(
                    imageVector = Icons.TwoTone.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(CommonR.string.general_edit_action))
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.scheduler_schedule_repeat_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val days = state.repeatInterval.toDays().toInt()
                Text(
                    text = pluralStringResource(R.plurals.scheduler_schedule_repeat_x_days, days, days),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = onDecreaseDays) {
                Icon(Icons.TwoTone.Remove, contentDescription = "−1")
            }
            OutlinedTextField(
                value = daysText,
                onValueChange = { newText ->
                    if (newText.isEmpty() || newText.matches(DAYS_INPUT_REGEX)) {
                        daysText = newText
                        newText.toIntOrNull()?.let(onRepeatDaysChanged)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    daysText = state.repeatInterval.toDays().toString()
                }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(72.dp),
            )
            IconButton(onClick = onIncreaseDays) {
                Icon(Icons.TwoTone.Add, contentDescription = "+1")
            }
        }

        Spacer(modifier = Modifier.padding(vertical = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onSave, enabled = state.canSave) {
                Text(stringResource(CommonR.string.general_save_action))
            }
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
    }

    if (showTimePicker) {
        ScheduleItemTimePickerDialog(
            initialHour = state.hour ?: 22,
            initialMinute = state.minute ?: 0,
            onConfirm = { h, m ->
                onTimePicked(h, m)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

private fun formatTime(hour: Int?, minute: Int?): String {
    if (hour == null || minute == null) return ""
    return "%02d:%02d".format(hour, minute)
}

private val TIME_INPUT_REGEX = Regex("^(\\d{1,2}):(\\d{2})$")
private val DAYS_INPUT_REGEX = Regex("^\\d{1,2}$")

private fun parseTime(text: String): Pair<Int, Int>? {
    val match = TIME_INPUT_REGEX.matchEntire(text.trim()) ?: return null
    val h = match.groupValues[1].toIntOrNull() ?: return null
    val m = match.groupValues[2].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}

@Preview2
@Composable
private fun ScheduleItemSheetPreview() {
    PreviewWrapper {
        Box {
            ScheduleItemSheet(
                stateSource = MutableStateFlow(
                    ScheduleItemViewModel.State(
                        label = "Daily clean",
                        hour = 22,
                        minute = 0,
                        repeatInterval = Duration.ofDays(3),
                        canSave = true,
                        isReady = true,
                    ),
                ),
            )
        }
    }
}

@Preview2
@Composable
private fun ScheduleItemSheetEmptyPreview() {
    PreviewWrapper {
        Box {
            ScheduleItemSheet(
                stateSource = MutableStateFlow(
                    ScheduleItemViewModel.State(isReady = true),
                ),
            )
        }
    }
}
