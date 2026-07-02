package eu.darken.sdmse.main.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.History
import androidx.compose.material.icons.twotone.SettingsBackupRestore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.backup.RestoreMode
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreScreenHost(
    vm: BackupRestoreViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        vm.performExport(result.data?.data)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        vm.onImportPicked(result.data?.data)
    }

    var restoreConfirm by remember { mutableStateOf<BackupRestoreViewModel.RestoreConfirmInfo?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is BackupRestoreViewModel.Event.PickExportTarget -> exportLauncher.launch(event.intent)
                is BackupRestoreViewModel.Event.PickImportSource -> importLauncher.launch(event.intent)
                is BackupRestoreViewModel.Event.ConfirmRestore -> restoreConfirm = event.info
                is BackupRestoreViewModel.Event.ExportDone -> scope.launch {
                    val msg = if (event.failedSections.isEmpty()) {
                        context.getString(R.string.backup_restore_export_success)
                    } else {
                        context.getString(
                            R.string.backup_restore_export_partial,
                            event.failedSections.joinToString(", "),
                        )
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is BackupRestoreViewModel.Event.RestoreDone -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.backup_restore_restore_success))
                }
                is BackupRestoreViewModel.Event.RestoreFailed -> scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.backup_restore_restore_failed,
                            event.failedSections.joinToString(", "),
                        ),
                        actionLabel = if (event.canUndo) {
                            context.getString(R.string.backup_restore_undo_action)
                        } else {
                            null
                        },
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoRestore()
                }
            }
        }
    }

    restoreConfirm?.let { info ->
        RestoreConfirmSheet(
            info = info,
            onConfirm = { mode ->
                restoreConfirm = null
                vm.confirmRestore(mode)
            },
            onCancel = {
                restoreConfirm = null
                vm.cancelRestore()
            },
        )
    }

    BackupRestoreScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onExport = vm::requestExport,
        onImport = vm::requestImport,
        onRecover = vm::requestRecovery,
        onUpgrade = { vm.navTo(UpgradeRoute()) },
    )
}

@Composable
internal fun BackupRestoreScreen(
    modifier: Modifier = Modifier,
    state: BackupRestoreViewModel.State = BackupRestoreViewModel.State(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onRecover: () -> Unit = {},
    onUpgrade: () -> Unit = {},
) {
    SdmScaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
        ) {
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.Backup,
                    title = stringResource(R.string.backup_restore_export_title),
                    subtitle = stringResource(R.string.backup_restore_export_desc),
                    onClick = onExport,
                    requiresUpgrade = state.isPro == false,
                    onUpgrade = onUpgrade,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.SettingsBackupRestore,
                    title = stringResource(R.string.backup_restore_import_title),
                    subtitle = stringResource(R.string.backup_restore_import_desc),
                    onClick = onImport,
                )
            }
            if (state.recoveryBackup != null) {
                item {
                    SettingsPreferenceItem(
                        icon = Icons.TwoTone.History,
                        title = stringResource(R.string.backup_restore_recovery_title),
                        subtitle = stringResource(R.string.backup_restore_recovery_desc),
                        onClick = onRecover,
                    )
                }
            }
        }
    }
}

private data class RestoreWarning(val id: String, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreConfirmSheet(
    info: BackupRestoreViewModel.RestoreConfirmInfo,
    onConfirm: (RestoreMode) -> Unit,
    onCancel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // One-shot: the first of {confirm, cancel, swipe/scrim/back dismiss} wins; later exits are ignored.
    // This guarantees confirm is never followed by cancel (which would drop the staged backup and skip
    // the restore).
    var handled by remember { mutableStateOf(false) }
    val finish: (() -> Unit) -> Unit = { action ->
        if (!handled) {
            handled = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) action() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!handled) { handled = true; onCancel() } },
        sheetState = sheetState,
    ) {
        RestoreConfirmContent(
            info = info,
            onConfirm = { mode -> finish { onConfirm(mode) } },
            onCancel = { finish(onCancel) },
        )
    }
}

@Composable
private fun RestoreConfirmContent(
    info: BackupRestoreViewModel.RestoreConfirmInfo,
    onConfirm: (RestoreMode) -> Unit,
    onCancel: () -> Unit,
) {
    val warnings = listOf(
        Triple("version", R.string.backup_restore_warn_version_body, info.version),
        Triple("android", R.string.backup_restore_warn_android_body, info.android),
        Triple("device", R.string.backup_restore_warn_device_body, info.device),
        Triple("flavor", R.string.backup_restore_warn_flavor_body, info.flavor),
    )
        .filter { (_, _, diff) -> diff.differs }
        .map { (id, res, diff) -> RestoreWarning(id, stringResource(res, diff.source, diff.current)) }

    var acked by remember(info) { mutableStateOf(emptySet<String>()) }
    // Recovery snapshots must be re-applied verbatim — mode is locked to REPLACE.
    var mode by remember(info) { mutableStateOf(if (info.isRecovery) RestoreMode.REPLACE else RestoreMode.MERGE) }
    val allAcked = warnings.all { acked.contains(it.id) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.backup_restore_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.backup_restore_confirm_created, info.createdAt),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.backup_restore_confirm_source,
                info.version.source, info.device.source, info.android.source,
            ),
            style = MaterialTheme.typography.bodySmall,
        )

        warnings.forEach { warning ->
            val checked = acked.contains(warning.id)
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = checked,
                            onClick = {
                                acked = if (checked) acked - warning.id else acked + warning.id
                            },
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = warning.body, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = stringResource(R.string.backup_restore_ack_understand),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        if (!info.isRecovery) {
            Text(
                text = stringResource(R.string.backup_restore_mode_label),
                style = MaterialTheme.typography.titleSmall,
            )
            RestoreModeOption(
                selected = mode == RestoreMode.MERGE,
                title = stringResource(R.string.backup_restore_mode_merge),
                description = stringResource(R.string.backup_restore_mode_merge_desc),
                onClick = { mode = RestoreMode.MERGE },
            )
            RestoreModeOption(
                selected = mode == RestoreMode.REPLACE,
                title = stringResource(R.string.backup_restore_mode_replace),
                description = stringResource(R.string.backup_restore_mode_replace_desc),
                onClick = { mode = RestoreMode.REPLACE },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
            TextButton(onClick = { onConfirm(mode) }, enabled = allAcked) {
                Text(stringResource(R.string.backup_restore_action_restore))
            }
        }
    }
}

@Composable
private fun RestoreModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview2
@Composable
private fun BackupRestoreScreenPreview() {
    PreviewWrapper {
        BackupRestoreScreen(state = BackupRestoreViewModel.State(isPro = false))
    }
}

@Preview2
@Composable
private fun RestoreConfirmContentPreview() {
    PreviewWrapper {
        RestoreConfirmContent(
            info = BackupRestoreViewModel.RestoreConfirmInfo(
                createdAt = "Jun 22, 2026, 12:00",
                version = BackupRestoreViewModel.Diff("1.2.3", "1.3.0"),
                android = BackupRestoreViewModel.Diff("13", "14"),
                device = BackupRestoreViewModel.Diff("Samsung SM-G991B", "Google Pixel 7"),
                flavor = BackupRestoreViewModel.Diff("GPLAY", "FOSS"),
            ),
            onConfirm = {},
            onCancel = {},
        )
    }
}
