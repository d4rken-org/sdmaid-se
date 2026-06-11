package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.R as AppCleanerR
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.debug.recorder.ui.ShortRecordingDialog
import eu.darken.sdmse.corpsefinder.R as CorpseFinderR
import eu.darken.sdmse.deduplicator.core.Duplicate
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionDialog
import eu.darken.sdmse.deduplicator.ui.dialogs.PreviewDeletionMode
import eu.darken.sdmse.systemcleaner.R as SystemCleanerR

internal sealed interface DashboardDialogState {
    data object CorpseFinderDelete : DashboardDialogState
    data object SystemCleanerDelete : DashboardDialogState
    data object AppCleanerDelete : DashboardDialogState
    data class DeduplicatorDelete(
        val clusters: List<Duplicate.Cluster>,
    ) : DashboardDialogState
    data object Todo : DashboardDialogState
    data object ShortRecordingWarning : DashboardDialogState
    data class MainActionDelete(
        val action: BottomBarState.Action,
    ) : DashboardDialogState

    data class UnknownFolders(
        val scannedCount: Int,
        val skippedCount: Int,
        val unknownPaths: List<String>,
    ) : DashboardDialogState
}

@Composable
internal fun DashboardEventDialogs(
    state: DashboardDialogState?,
    onDismiss: () -> Unit,
    onConfirmCorpseFinder: () -> Unit,
    onShowCorpseFinder: () -> Unit,
    onConfirmSystemCleaner: () -> Unit,
    onShowSystemCleaner: () -> Unit,
    onConfirmAppCleaner: () -> Unit,
    onShowAppCleaner: () -> Unit,
    onConfirmDeduplicator: () -> Unit,
    onShowDeduplicator: () -> Unit,
    onPreviewDeduplicator: (eu.darken.sdmse.common.previews.PreviewOptions) -> Unit,
    onStopShortRecording: () -> Unit,
    onConfirmMainAction: (BottomBarState.Action) -> Unit,
) {
    when (state) {
        null -> Unit

        DashboardDialogState.CorpseFinderDelete -> DeleteConfirmDialog(
            messageRes = CorpseFinderR.string.corpsefinder_delete_all_confirmation_message,
            onConfirm = { onConfirmCorpseFinder(); onDismiss() },
            onDetails = { onShowCorpseFinder(); onDismiss() },
            onDismiss = onDismiss,
        )

        DashboardDialogState.SystemCleanerDelete -> DeleteConfirmDialog(
            messageRes = SystemCleanerR.string.systemcleaner_delete_all_confirmation_message,
            onConfirm = { onConfirmSystemCleaner(); onDismiss() },
            onDetails = { onShowSystemCleaner(); onDismiss() },
            onDismiss = onDismiss,
        )

        DashboardDialogState.AppCleanerDelete -> DeleteConfirmDialog(
            messageRes = AppCleanerR.string.appcleaner_delete_all_confirmation_message,
            onConfirm = { onConfirmAppCleaner(); onDismiss() },
            onDetails = { onShowAppCleaner(); onDismiss() },
            onDismiss = onDismiss,
        )

        is DashboardDialogState.DeduplicatorDelete -> PreviewDeletionDialog(
            mode = PreviewDeletionMode.All(clusters = state.clusters),
            onConfirm = { onConfirmDeduplicator(); onDismiss() },
            onDismiss = onDismiss,
            onPreviewClick = onPreviewDeduplicator,
            onShowDetails = { onShowDeduplicator(); onDismiss() },
        )

        DashboardDialogState.Todo -> SdmConfirmDialog(
            message = stringResource(CommonR.string.general_todo_msg),
            onDismissRequest = onDismiss,
            positive = SdmDialogAction(
                label = stringResource(android.R.string.ok),
                onClick = onDismiss,
            ),
        )

        DashboardDialogState.ShortRecordingWarning -> ShortRecordingDialog(
            onContinue = {},
            onStopAnyway = onStopShortRecording,
            onDismiss = onDismiss,
        )

        is DashboardDialogState.MainActionDelete -> SdmConfirmDialog(
            title = stringResource(CommonR.string.general_delete_confirmation_title),
            message = stringResource(R.string.dashboard_delete_all_message),
            onDismissRequest = onDismiss,
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_delete_action),
                onClick = {
                    onConfirmMainAction(state.action)
                    onDismiss()
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_cancel_action),
                onClick = onDismiss,
            ),
        )

        is DashboardDialogState.UnknownFolders -> {
            val header = "Scanned ${state.scannedCount} dirs, skipped ${state.skippedCount}"
            val body = if (state.unknownPaths.isEmpty()) {
                "No unknown folders found."
            } else {
                "Found ${state.unknownPaths.size} unknown folder(s):\n\n${state.unknownPaths.joinToString("\n")}"
            }
            SdmConfirmDialog(
                title = "Unknown Folders",
                message = "$header\n\n$body",
                onDismissRequest = onDismiss,
                positive = SdmDialogAction(
                    label = stringResource(android.R.string.ok),
                    onClick = onDismiss,
                ),
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    messageRes: Int,
    onConfirm: () -> Unit,
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    SdmConfirmDialog(
        title = stringResource(CommonR.string.general_delete_confirmation_title),
        message = stringResource(messageRes),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_delete_action),
            onClick = onConfirm,
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_cancel_action),
            onClick = onDismiss,
        ),
        neutral = SdmDialogAction(
            label = stringResource(CommonR.string.general_show_details_action),
            onClick = onDetails,
        ),
    )
}
