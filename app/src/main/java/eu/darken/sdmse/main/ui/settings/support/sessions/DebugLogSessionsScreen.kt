package eu.darken.sdmse.main.ui.settings.support.sessions

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.main.ui.settings.support.sessions.items.DebugLogSessionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant

@Composable
fun DebugLogSessionsScreenHost(
    vm: DebugLogSessionsViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refresh()
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DebugLogSessionsViewModel.Event.LaunchRecorder -> {
                    val intent = RecorderActivity.getLaunchIntent(context, event.sessionId)
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    vm.navUp()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = vm::navUp,
        sheetState = sheetState,
    ) {
        DebugLogSessionsSheetContent(
            stateSource = vm.state,
            onOpenSession = vm::openSession,
            onDeleteSession = vm::delete,
            onClearAll = vm::deleteAll,
            onStopRecording = { vm.stopRecording() },
        )
    }
}

@Composable
internal fun DebugLogSessionsSheetContent(
    modifier: Modifier = Modifier,
    stateSource: StateFlow<DebugLogSessionsViewModel.State> =
        MutableStateFlow(DebugLogSessionsViewModel.State()),
    onOpenSession: (SessionId) -> Unit = {},
    onDeleteSession: (SessionId) -> Unit = {},
    onClearAll: () -> Unit = {},
    onStopRecording: () -> Unit = {},
) {
    val state by stateSource.collectAsStateWithLifecycle()

    var pendingDeleteId by remember { mutableStateOf<SessionId?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.support_debuglog_folder_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )

        if (state.sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.support_debuglog_folder_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                items(state.sessions, key = { it.id.value }) { session ->
                    DebugLogSessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { pendingDeleteId = session.id },
                        onStop = onStopRecording,
                    )
                }
            }

            TextButton(
                onClick = { showClearAllConfirm = true },
                enabled = state.hasDeletable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.support_debuglog_folder_delete_confirmation_title))
            }
        }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            text = {
                Text(stringResource(R.string.debug_debuglog_sessions_delete_confirmation_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(id)
                    pendingDeleteId = null
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.support_debuglog_folder_delete_confirmation_title)) },
            text = { Text(stringResource(R.string.support_debuglog_folder_delete_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearAllConfirm = false
                }) {
                    Text(stringResource(CommonR.string.general_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(CommonR.string.general_cancel_action))
                }
            },
        )
    }
}

@Preview2
@Composable
private fun DebugLogSessionsEmptyPreview() {
    PreviewWrapper {
        DebugLogSessionsSheetContent(
            stateSource = MutableStateFlow(DebugLogSessionsViewModel.State()),
        )
    }
}

@Preview2
@Composable
private fun DebugLogSessionsPopulatedPreview() {
    PreviewWrapper {
        DebugLogSessionsSheetContent(
            stateSource = MutableStateFlow(
                DebugLogSessionsViewModel.State(
                    sessions = listOf(
                        DebugLogSession.Recording(
                            id = SessionId("cache:rec"),
                            createdAt = Instant.now(),
                            logDir = File("/tmp/rec"),
                            diskSize = 1024L,
                        ),
                        DebugLogSession.Finished(
                            id = SessionId("ext:done"),
                            createdAt = Instant.now().minusSeconds(3600),
                            logDir = File("/tmp/done"),
                            diskSize = 8_000_000L,
                            zipFile = File("/tmp/done.zip"),
                            compressedSize = 1_024_000L,
                        ),
                    ),
                ),
            ),
        )
    }
}
