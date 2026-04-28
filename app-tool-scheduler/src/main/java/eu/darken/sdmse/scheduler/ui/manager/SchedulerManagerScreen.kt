package eu.darken.sdmse.scheduler.ui.manager

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.debug.Bugs
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.scheduler.R
import eu.darken.sdmse.scheduler.core.Schedule
import eu.darken.sdmse.scheduler.core.ScheduleId
import eu.darken.sdmse.scheduler.ui.manager.items.AlarmHintRow
import eu.darken.sdmse.scheduler.ui.manager.items.BatteryHintRow
import eu.darken.sdmse.scheduler.ui.manager.items.CommandsEditDialog
import eu.darken.sdmse.scheduler.ui.manager.items.ScheduleRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private val TAG = logTag("Scheduler", "Manager", "Screen")

@Composable
fun SchedulerManagerScreenHost(
    vm: SchedulerManagerViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCommandsEdit by remember {
        mutableStateOf<SchedulerManagerViewModel.Event.EditCommands?>(null)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is SchedulerManagerViewModel.Event.EditCommands -> pendingCommandsEdit = event

                is SchedulerManagerViewModel.Event.ShowBatteryOptimizationSettings -> {
                    runCatching { context.startActivity(event.intent) }
                        .onFailure { error ->
                            log(TAG, WARN) { "Battery optimization intent failed: $error" }
                            vm.onBatteryIntentFailed(error as? ActivityNotFoundException ?: error)
                        }
                }
            }
        }
    }

    SchedulerManagerScreen(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onShowHelp = vm::showHelp,
        onDebugSchedule = vm::debugSchedule,
        onCreateNew = vm::createNew,
        onEditSchedule = vm::editSchedule,
        onToggleSchedule = vm::toggleSchedule,
        onRemoveSchedule = vm::removeSchedule,
        onToggleCorpseFinder = vm::toggleCorpseFinder,
        onToggleSystemCleaner = vm::toggleSystemCleaner,
        onToggleAppCleaner = vm::toggleAppCleaner,
        onEditCommands = vm::requestEditCommands,
        onFixBattery = vm::requestBatteryOptimizationSettings,
        onDismissBattery = vm::dismissBatteryHint,
    )

    pendingCommandsEdit?.let { ev ->
        CommandsEditDialog(
            initialText = ev.initialText,
            onConfirm = { newText ->
                vm.updateCommandsAfterSchedule(ev.scheduleId, newText)
                pendingCommandsEdit = null
            },
            onDismiss = { pendingCommandsEdit = null },
        )
    }
}

@Composable
internal fun SchedulerManagerScreen(
    stateSource: Flow<SchedulerManagerViewModel.State> = MutableStateFlow(SchedulerManagerViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onShowHelp: () -> Unit = {},
    onDebugSchedule: () -> Unit = {},
    onCreateNew: () -> Unit = {},
    onEditSchedule: (ScheduleId) -> Unit = {},
    onToggleSchedule: (ScheduleId) -> Unit = {},
    onRemoveSchedule: (ScheduleId) -> Unit = {},
    onToggleCorpseFinder: (ScheduleId) -> Unit = {},
    onToggleSystemCleaner: (ScheduleId) -> Unit = {},
    onToggleAppCleaner: (ScheduleId) -> Unit = {},
    onEditCommands: (ScheduleId) -> Unit = {},
    onFixBattery: () -> Unit = {},
    onDismissBattery: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle(initialValue = SchedulerManagerViewModel.State())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduler_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (Bugs.isDebug) {
                        IconButton(onClick = onDebugSchedule) {
                            Icon(
                                Icons.TwoTone.BugReport,
                                contentDescription = "Debug schedule",
                            )
                        }
                    }
                    IconButton(onClick = onShowHelp) {
                        Icon(
                            Icons.AutoMirrored.TwoTone.HelpOutline,
                            contentDescription = stringResource(CommonR.string.general_help_action),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNew) {
                Icon(
                    Icons.TwoTone.Add,
                    contentDescription = stringResource(R.string.scheduler_new_schedule_action),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        ProgressOverlay(
            data = if (state.isLoading) Progress.Data() else null,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ScheduleListContent(
                state = state,
                contentPadding = PaddingValues(vertical = 8.dp),
                onEditSchedule = onEditSchedule,
                onToggleSchedule = onToggleSchedule,
                onRemoveSchedule = onRemoveSchedule,
                onToggleCorpseFinder = onToggleCorpseFinder,
                onToggleSystemCleaner = onToggleSystemCleaner,
                onToggleAppCleaner = onToggleAppCleaner,
                onEditCommands = onEditCommands,
                onFixBattery = onFixBattery,
                onDismissBattery = onDismissBattery,
            )
        }
    }
}

@Composable
private fun ScheduleListContent(
    state: SchedulerManagerViewModel.State,
    contentPadding: PaddingValues,
    onEditSchedule: (ScheduleId) -> Unit,
    onToggleSchedule: (ScheduleId) -> Unit,
    onRemoveSchedule: (ScheduleId) -> Unit,
    onToggleCorpseFinder: (ScheduleId) -> Unit,
    onToggleSystemCleaner: (ScheduleId) -> Unit,
    onToggleAppCleaner: (ScheduleId) -> Unit,
    onEditCommands: (ScheduleId) -> Unit,
    onFixBattery: () -> Unit,
    onDismissBattery: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.showBatteryHint) {
            item("battery") {
                BatteryHintRow(
                    onFix = onFixBattery,
                    onDismiss = onDismissBattery,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(state.schedules, key = { it.id }) { schedule ->
            ScheduleRow(
                schedule = schedule,
                showCommands = state.showCommands,
                onEdit = { onEditSchedule(schedule.id) },
                onToggle = { onToggleSchedule(schedule.id) },
                onRemove = { onRemoveSchedule(schedule.id) },
                onToggleCorpseFinder = { onToggleCorpseFinder(schedule.id) },
                onToggleSystemCleaner = { onToggleSystemCleaner(schedule.id) },
                onToggleAppCleaner = { onToggleAppCleaner(schedule.id) },
                onEditCommands = { onEditCommands(schedule.id) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.showAlarmHint) {
            item("alarm") {
                AlarmHintRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item("spacer") {
            Box(modifier = Modifier.padding(40.dp))
        }
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenEmptyPreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = emptyList(),
                    isLoading = false,
                ),
            ),
        )
    }
}

@Preview2
@Composable
private fun SchedulerManagerScreenWithSchedulePreview() {
    PreviewWrapper {
        SchedulerManagerScreen(
            stateSource = MutableStateFlow(
                SchedulerManagerViewModel.State(
                    schedules = listOf(
                        Schedule(
                            id = "preview-1",
                            label = "Daily clean",
                            hour = 22,
                            minute = 0,
                        ),
                    ),
                    showAlarmHint = false,
                    showBatteryHint = false,
                    showCommands = false,
                    isLoading = false,
                ),
            ),
        )
    }
}

