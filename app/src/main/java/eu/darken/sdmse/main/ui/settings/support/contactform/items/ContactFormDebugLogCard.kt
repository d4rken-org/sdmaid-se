package eu.darken.sdmse.main.ui.settings.support.contactform.items

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.LogPickerState
import eu.darken.sdmse.main.ui.settings.support.contactform.SupportContactFormViewModel.LogSessionItem
import java.io.File

@Composable
fun ContactFormDebugLogCard(
    pickerState: LogPickerState,
    onSelectSession: (SessionId) -> Unit,
    onDeleteSession: (SessionId) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.support_contact_debuglog_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.support_contact_debuglog_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (pickerState.sessions.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    pickerState.sessions.forEach { session ->
                        LogSessionRow(
                            session = session,
                            selected = pickerState.selectedSessionId == session.sessionId,
                            onClick = { onSelectSession(session.sessionId) },
                            onDelete = { onDeleteSession(session.sessionId) },
                        )
                    }
                }
            } else if (!pickerState.isRecording) {
                Text(
                    text = stringResource(R.string.support_contact_debuglog_picker_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = if (pickerState.isRecording) onStopRecording else onStartRecording,
                ) {
                    Icon(
                        imageVector = if (pickerState.isRecording) Icons.TwoTone.Cancel else Icons.TwoTone.BugReport,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 4.dp),
                    )
                    Text(
                        stringResource(
                            if (pickerState.isRecording) R.string.debug_debuglog_stop_action
                            else R.string.debug_debuglog_record_action,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogSessionRow(
    session: LogSessionItem,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    session.lastModified,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = Formatter.formatShortFileSize(context, session.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.TwoTone.Delete,
                contentDescription = stringResource(CommonR.string.general_delete_action),
            )
        }
    }
}

@Preview2
@Composable
private fun ContactFormDebugLogCardPreview() {
    PreviewWrapper {
        ContactFormDebugLogCard(
            pickerState = LogPickerState(
                sessions = listOf(
                    LogSessionItem(
                        sessionId = SessionId("ext:sample"),
                        zipFile = File("/tmp/sample.zip"),
                        size = 1_024_000L,
                        lastModified = System.currentTimeMillis() - 3600_000L,
                    ),
                ),
                selectedSessionId = SessionId("ext:sample"),
            ),
            onSelectSession = {},
            onDeleteSession = {},
            onStartRecording = {},
            onStopRecording = {},
        )
    }
}
