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
import eu.darken.sdmse.common.compose.layout.SdmScaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.SdmSeLinks
import eu.darken.sdmse.common.compose.icons.Discord
import eu.darken.sdmse.common.compose.icons.Github
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.layout.SdmTooltipIconButton
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

    var showRecorderConsent by remember { mutableStateOf(false) }
    var showShortRecordingWarning by remember { mutableStateOf(false) }

    // Refresh on every resume (legacy onResume parity), so the debug-log session list is current
    // after returning from RecorderActivity or an external change — not just on first composition.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshSessions()
    }

    LaunchedEffect(vm, context) {
        vm.events.collect { event ->
            when (event) {
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
        onNavigateUp = vm::navUp,
        onContactClick = { vm.navTo(SupportFormRoute) },
        onDebugLogClick = { isRecording ->
            if (isRecording) {
                vm.stopDebugLog()
            } else {
                showRecorderConsent = true
            }
        },
        onDebugLogFolderClick = { vm.navTo(DebugLogSessionsRoute) },
        onDocumentationClick = { vm.openUrl(SdmSeLinks.WIKI) },
        onIssueTrackerClick = { vm.openUrl(SdmSeLinks.ISSUES) },
        onDiscordClick = { vm.openUrl(SdmSeLinks.DISCORD) },
    )
}

@Composable
internal fun SupportScreen(
    isRecordingSource: kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
    folderStatsSource: kotlinx.coroutines.flow.Flow<SupportViewModel.DebugLogFolderStats> = kotlinx.coroutines.flow.flowOf(SupportViewModel.DebugLogFolderStats()),
    onNavigateUp: () -> Unit = {},
    onContactClick: () -> Unit = {},
    onDebugLogClick: (Boolean) -> Unit = {},
    onDebugLogFolderClick: () -> Unit = {},
    onDocumentationClick: () -> Unit = {},
    onIssueTrackerClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {},
) {
    val isRecording by isRecordingSource.collectAsStateWithLifecycle(initialValue = false)
    val folderStats by folderStatsSource.collectAsStateWithLifecycle(initialValue = SupportViewModel.DebugLogFolderStats())
    val context = LocalContext.current

    SdmScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_support_label)) },
                navigationIcon = {
                    SdmTooltipIconButton(
                        icon = Icons.AutoMirrored.TwoTone.ArrowBack,
                        label = stringResource(CommonR.string.general_navigate_up_action),
                        onClick = onNavigateUp,
                    )
                },
            )
        },
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
                    icon = if (isRecording) Icons.TwoTone.Cancel else Icons.TwoTone.BugReport,
                    title = stringResource(
                        if (isRecording) R.string.debug_debuglog_stop_action
                        else R.string.debug_debuglog_record_action
                    ),
                    subtitle = stringResource(R.string.support_debuglog_desc),
                    onClick = { onDebugLogClick(isRecording) },
                    // Title flips with the recording state — a stable key keeps focus memory
                    // working across the toggle.
                    focusKey = "support.debuglog.toggle",
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

@Preview2
@Composable
private fun SupportScreenPreview() {
    PreviewWrapper {
        SupportScreen()
    }
}
