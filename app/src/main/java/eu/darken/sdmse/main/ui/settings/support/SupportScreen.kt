package eu.darken.sdmse.main.ui.settings.support

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.automirrored.twotone.ContactSupport
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.FolderOpen
import androidx.compose.material.icons.twotone.PermIdentity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.compose.icons.Discord
import eu.darken.sdmse.common.compose.icons.Github
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.settings.SettingsCategoryHeader
import eu.darken.sdmse.common.compose.settings.SettingsPreferenceItem
import eu.darken.sdmse.common.debug.recorder.ui.RecorderConsentDialog
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.main.ui.navigation.DebugLogSessionsRoute
import eu.darken.sdmse.main.ui.navigation.SupportFormRoute
import eu.darken.sdmse.common.R as CommonR

@Composable
fun SupportScreenHost(
    vm: SupportViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showRecorderConsent by remember { mutableStateOf(false) }
    var showShortRecordingWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshSessions()
    }

    LaunchedEffect(vm, context, snackbarHostState) {
        vm.events.collect { event ->
            when (event) {
                is SupportViewModel.SupportEvents.ShowInstallId -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.installId,
                        actionLabel = context.getString(CommonR.string.general_copy_action),
                        duration = SnackbarDuration.Indefinite,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        vm.copyToClipboard(event.installId)
                    }
                }

                is SupportViewModel.SupportEvents.LaunchRecorderActivity -> {
                    context.startActivity(event.intent)
                }

                is SupportViewModel.SupportEvents.ShowShortRecordingWarning -> {
                    showShortRecordingWarning = true
                }
            }
        }
    }

    if (showRecorderConsent) {
        RecorderConsentDialog(
            onStartRecording = vm::startDebugLog,
            onOpenPrivacyPolicy = { vm.openUrl(SdmSeLinks.PRIVACY_POLICY) },
            onDismiss = { showRecorderConsent = false },
        )
    }

    if (showShortRecordingWarning) {
        ShortRecordingDialog(
            onContinue = {},
            onStopAnyway = vm::confirmStopDebugLog,
            onDismiss = { showShortRecordingWarning = false },
        )
    }

    SupportScreen(
        isRecordingSource = vm.isRecording,
        folderStatsSource = vm.debugLogFolderStats,
        snackbarHostState = snackbarHostState,
        onNavigateUp = vm::navUp,
        onContactClick = { vm.navTo(SupportFormRoute) },
        onInstallIdClick = vm::copyInstallID,
        onDebugLogClick = { isRecording ->
            if (isRecording) {
                vm.stopDebugLog()
            } else {
                showRecorderConsent = true
            }
        },
        onDebugLogFolderClick = { vm.navTo(DebugLogSessionsRoute) },
        onDocumentationClick = { vm.openUrl("https://github.com/d4rken-org/sdmaid-se/wiki") },
        onIssueTrackerClick = { vm.openUrl("https://github.com/d4rken-org/sdmaid-se/issues") },
        onDiscordClick = { vm.openUrl("https://discord.gg/8Fjy6PTfXu") },
    )
}

@Composable
internal fun SupportScreen(
    isRecordingSource: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
    folderStatsSource: kotlinx.coroutines.flow.Flow<SupportViewModel.DebugLogFolderStats> = kotlinx.coroutines.flow.flowOf(SupportViewModel.DebugLogFolderStats()),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateUp: () -> Unit = {},
    onContactClick: () -> Unit = {},
    onInstallIdClick: () -> Unit = {},
    onDebugLogClick: (Boolean) -> Unit = {},
    onDebugLogFolderClick: () -> Unit = {},
    onDocumentationClick: () -> Unit = {},
    onIssueTrackerClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {},
) {
    val isRecording by isRecordingSource.collectAsStateWithLifecycle(initialValue = false)
    val folderStats by folderStatsSource.collectAsStateWithLifecycle(initialValue = SupportViewModel.DebugLogFolderStats())
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_support_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
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
                    icon = Icons.AutoMirrored.TwoTone.MenuBook,
                    title = stringResource(R.string.documentation_label),
                    onClick = onDocumentationClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = SdmIcons.Github,
                    title = stringResource(R.string.issue_tracker_label),
                    subtitle = stringResource(R.string.issue_tracker_description),
                    onClick = onIssueTrackerClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = SdmIcons.Discord,
                    title = stringResource(R.string.discord_label),
                    subtitle = stringResource(R.string.discord_description),
                    onClick = onDiscordClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = Icons.AutoMirrored.TwoTone.ContactSupport,
                    title = stringResource(R.string.support_contact_label),
                    subtitle = stringResource(R.string.support_contact_desc),
                    onClick = onContactClick,
                )
            }

            // Other
            item { SettingsCategoryHeader(text = stringResource(R.string.settings_category_other_label)) }

            item {
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.PermIdentity,
                    title = stringResource(R.string.support_installid_label),
                    subtitle = stringResource(R.string.support_installid_desc),
                    onClick = onInstallIdClick,
                )
            }
            item {
                SettingsPreferenceItem(
                    icon = if (isRecording) Icons.TwoTone.Cancel else Icons.TwoTone.BugReport,
                    title = stringResource(
                        if (isRecording) R.string.debug_debuglog_stop_action
                        else R.string.debug_debuglog_record_action
                    ),
                    subtitle = stringResource(R.string.support_debuglog_desc),
                    onClick = { onDebugLogClick(isRecording) },
                )
            }
            item {
                val folderSummary = if (folderStats.sessionCount == 0) {
                    stringResource(R.string.support_debuglog_folder_empty_desc)
                } else {
                    val formattedSize = Formatter.formatShortFileSize(context, folderStats.totalSizeBytes)
                    context.resources.getQuantityString(
                        R.plurals.support_debuglog_folder_desc,
                        folderStats.sessionCount,
                        folderStats.sessionCount,
                        formattedSize,
                    )
                }
                SettingsPreferenceItem(
                    icon = Icons.TwoTone.FolderOpen,
                    title = stringResource(R.string.support_debuglog_folder_label),
                    subtitle = folderSummary,
                    enabled = !isRecording,
                    onClick = onDebugLogFolderClick,
                )
            }
        }
    }
}
