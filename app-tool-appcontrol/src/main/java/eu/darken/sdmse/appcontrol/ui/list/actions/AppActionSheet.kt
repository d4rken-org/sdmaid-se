package eu.darken.sdmse.appcontrol.ui.list.actions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.AcUnit
import androidx.compose.material.icons.twotone.Archive
import androidx.compose.material.icons.twotone.DoNotDisturb
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SaveAlt
import androidx.compose.material.icons.twotone.Shop
import androidx.compose.material.icons.twotone.Unarchive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import eu.darken.sdmse.appcontrol.R
import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.appcontrol.ui.list.AppInfoTagsRow
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionInfoSizeRow
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionInfoUsageRow
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItem
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionItemContext
import eu.darken.sdmse.appcontrol.ui.list.actions.items.AppActionRow
import eu.darken.sdmse.appcontrol.ui.list.actions.items.buildAppActionItems
import eu.darken.sdmse.appcontrol.ui.preview.previewAppInfo
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.ShieldAdd
import eu.darken.sdmse.common.compose.icons.ShieldEdit
import eu.darken.sdmse.common.compose.progress.ProgressOverlay
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.pkgs.features.SourceAvailable
import eu.darken.sdmse.common.pkgs.getSettingsIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val TAG = logTag("AppControl", "Action", "Sheet")

@Composable
fun AppActionSheetHost(
    installId: eu.darken.sdmse.common.pkgs.features.InstallId,
    vm: AppActionViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    LaunchedEffect(installId) { vm.setInstallId(installId) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()

    var pendingConfirm by remember { mutableStateOf<AppActionViewModel.Event?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        vm.onExportPathPicked(result.data?.data)
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is AppActionViewModel.Event.ShowResult -> snackScope.launch {
                    snackbarHostState.showSnackbar(
                        message = event.result.primaryInfo.get(context),
                        duration = SnackbarDuration.Short,
                    )
                }

                is AppActionViewModel.Event.SelectExportPath -> {
                    runCatching { exportLauncher.launch(event.intent) }
                        .onFailure { log(TAG, WARN) { "Documents app missing: $it" } }
                }

                is AppActionViewModel.Event.ConfirmUninstall,
                is AppActionViewModel.Event.ConfirmArchive,
                is AppActionViewModel.Event.ConfirmRestore -> pendingConfirm = event
            }
        }
    }

    AppActionSheet(
        stateSource = vm.state,
        snackbarHostState = snackbarHostState,
        onActionTapped = vm::onActionTapped,
        onIconLongPress = { appInfo ->
            runCatching { context.startActivity(appInfo.pkg.getSettingsIntent(context)) }
                .onFailure { log(TAG, WARN) { "Settings intent failed: $it" } }
        },
    )

    pendingConfirm?.let { ev ->
        val cancelAction = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = { pendingConfirm = null },
        )
        when (ev) {
            is AppActionViewModel.Event.ConfirmUninstall -> SdmConfirmDialog(
                title = stringResource(CommonR.string.general_delete_confirmation_title),
                message = vm.state.value.appInfo
                    ?.let { stringResource(CommonR.string.general_delete_confirmation_message_x, it.label.get(context)) }
                    ?: "",
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(CommonR.string.general_delete_action),
                    onClick = {
                        pendingConfirm = null
                        vm.onUninstallConfirmed()
                    },
                ),
                negative = cancelAction,
            )

            is AppActionViewModel.Event.ConfirmArchive -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_archive_confirmation_title),
                message = stringResource(R.string.appcontrol_archive_description),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(R.string.appcontrol_archive_action),
                    onClick = {
                        pendingConfirm = null
                        vm.onArchiveConfirmed()
                    },
                ),
                negative = cancelAction,
            )

            is AppActionViewModel.Event.ConfirmRestore -> SdmConfirmDialog(
                title = stringResource(R.string.appcontrol_restore_confirmation_title),
                message = stringResource(R.string.appcontrol_restore_description),
                onDismissRequest = { pendingConfirm = null },
                positive = SdmDialogAction(
                    label = stringResource(R.string.appcontrol_restore_action),
                    onClick = {
                        pendingConfirm = null
                        vm.onRestoreConfirmed()
                    },
                ),
                negative = cancelAction,
            )

            else -> Unit
        }
    }
}

@Composable
internal fun AppActionSheet(
    stateSource: StateFlow<AppActionViewModel.State> = MutableStateFlow(AppActionViewModel.State()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onActionTapped: (AppActionItem) -> Unit = {},
    onIconLongPress: (AppInfo) -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appInfo = state.appInfo

    Box(modifier = Modifier.fillMaxWidth()) {
        ProgressOverlay(
            data = state.progress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (appInfo == null) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp))
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(appInfo.pkg).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { onIconLongPress(appInfo) },
                                ),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appInfo.label.get(context),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = appInfo.pkg.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${appInfo.pkg.versionName ?: "?"} (${appInfo.pkg.versionCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val userLabel = appInfo.userProfile?.getHumanLabel()?.get(context)
                            if (userLabel != null) {
                                Text(
                                    text = userLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    AppInfoTagsRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        appInfo = appInfo,
                    )
                    Spacer(Modifier.height(8.dp))

                    val items = state.items.orEmpty()
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(items, key = { itemKey(it) }) { item ->
                            ActionItemRow(item, appInfo, onActionTapped)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ActionItemRow(
    item: AppActionItem,
    appInfo: AppInfo,
    onActionTapped: (AppActionItem) -> Unit,
) {
    val context = LocalContext.current
    when (item) {
        is AppActionItem.Info.Size -> AppActionInfoSizeRow(
            sizes = item.sizes,
            showAppLine = appInfo.pkg is SourceAvailable,
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Info.Usage -> AppActionInfoUsageRow(
            installedAt = item.installedAt,
            updatedAt = item.updatedAt,
            usage = item.usage,
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Launch -> AppActionRow(
            icon = rememberVectorPainter(Icons.AutoMirrored.TwoTone.OpenInNew),
            title = stringResource(R.string.appcontrol_app_launch_title),
            description = stringResource(R.string.appcontrol_app_launch_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.ForceStop -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.PowerSettingsNew),
            title = stringResource(R.string.appcontrol_app_forcestop_title),
            description = stringResource(R.string.appcontrol_app_forcestop_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.SystemSettings -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.Settings),
            title = stringResource(CommonR.string.appcontrol_systemsettings_open_title),
            description = stringResource(R.string.appcontrol_systemsettings_open_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.AppStore -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.Shop),
            title = stringResource(R.string.appcontrol_appstore_open_title),
            description = stringResource(R.string.appcontrol_appstore_open_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Exclude -> AppActionRow(
            icon = if (item.existingExclusionId == null) {
                rememberVectorPainter(SdmIcons.ShieldAdd)
            } else {
                rememberVectorPainter(SdmIcons.ShieldEdit)
            },
            title = if (item.existingExclusionId == null) {
                stringResource(R.string.appcontrol_app_exclude_add_title)
            } else {
                stringResource(R.string.appcontrol_app_exclude_edit_title)
            },
            description = if (item.existingExclusionId == null) {
                stringResource(R.string.appcontrol_app_exclude_add_description)
            } else {
                stringResource(R.string.appcontrol_app_exclude_edit_description)
            },
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Toggle -> AppActionRow(
            icon = if (item.isEnabled) {
                rememberVectorPainter(Icons.TwoTone.AcUnit)
            } else {
                rememberVectorPainter(Icons.TwoTone.DoNotDisturb)
            },
            title = if (item.isEnabled) {
                stringResource(R.string.appcontrol_toggle_app_disable_action)
            } else {
                stringResource(R.string.appcontrol_toggle_app_enable_action)
            },
            description = if (item.isEnabled) {
                stringResource(R.string.appcontrol_toggle_app_disable_description)
            } else {
                stringResource(R.string.appcontrol_toggle_app_enable_description)
            },
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Uninstall -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.Delete),
            title = stringResource(CommonR.string.general_delete_action),
            description = stringResource(R.string.appcontrol_app_uninstall_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Archive -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.Archive),
            title = stringResource(R.string.appcontrol_archive_action),
            description = stringResource(R.string.appcontrol_archive_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Restore -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.Unarchive),
            title = stringResource(R.string.appcontrol_restore_action),
            description = stringResource(R.string.appcontrol_restore_description),
            onClick = { onActionTapped(item) },
        )

        is AppActionItem.Action.Export -> AppActionRow(
            icon = rememberVectorPainter(Icons.TwoTone.SaveAlt),
            title = stringResource(R.string.appcontrol_export_title),
            description = stringResource(R.string.appcontrol_export_description),
            onClick = { onActionTapped(item) },
        )
    }
}

@Preview2
@Composable
private fun AppActionSheetPreview() {
    val appInfo = previewAppInfo()
    val items = buildAppActionItems(
        appInfo = appInfo,
        ctx = AppActionItemContext(
            isCurrentUser = true,
            launchAvailable = true,
            appStoreAvailable = true,
            canForceStop = true,
            canArchive = false,
            canRestore = false,
            canToggle = true,
            existingExclusionId = null,
        ),
    )
    PreviewWrapper {
        AppActionSheet(
            stateSource = MutableStateFlow(
                AppActionViewModel.State(appInfo = appInfo, items = items),
            ),
        )
    }
}

private fun itemKey(item: AppActionItem): String = when (item) {
    is AppActionItem.Info.Size -> "size"
    is AppActionItem.Info.Usage -> "usage"
    is AppActionItem.Action.Launch -> "launch"
    is AppActionItem.Action.ForceStop -> "forceStop"
    is AppActionItem.Action.SystemSettings -> "settings"
    is AppActionItem.Action.AppStore -> "appstore"
    is AppActionItem.Action.Exclude -> "exclude"
    is AppActionItem.Action.Toggle -> "toggle"
    is AppActionItem.Action.Uninstall -> "uninstall"
    is AppActionItem.Action.Archive -> "archive"
    is AppActionItem.Action.Restore -> "restore"
    is AppActionItem.Action.Export -> "export"
}

