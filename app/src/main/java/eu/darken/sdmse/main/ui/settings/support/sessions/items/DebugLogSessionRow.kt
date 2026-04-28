package eu.darken.sdmse.main.ui.settings.support.sessions.items

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import eu.darken.sdmse.common.debug.recorder.core.DebugLogSession
import eu.darken.sdmse.common.debug.recorder.core.SessionId
import java.io.File
import java.time.Instant

@Composable
fun DebugLogSessionRow(
    session: DebugLogSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val clickable = session is DebugLogSession.Finished
    val rowModifier = if (clickable) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Row(
        modifier = rowModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionStatusIcon(session = session)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    session.createdAt.toEpochMilli(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when (session) {
                    is DebugLogSession.Recording ->
                        stringResource(R.string.debug_debuglog_recording_progress)

                    is DebugLogSession.Zipping ->
                        stringResource(R.string.debug_debuglog_sessions_zipping_label)

                    is DebugLogSession.Finished ->
                        Formatter.formatShortFileSize(context, session.compressedSize)

                    is DebugLogSession.Failed -> stringResource(
                        when (session.reason) {
                            DebugLogSession.Failed.Reason.EMPTY_LOG ->
                                R.string.debug_debuglog_sessions_failed_empty_log_label

                            DebugLogSession.Failed.Reason.MISSING_LOG ->
                                R.string.debug_debuglog_sessions_failed_missing_log_label

                            DebugLogSession.Failed.Reason.CORRUPT_ZIP ->
                                R.string.debug_debuglog_sessions_failed_corrupt_zip_label

                            DebugLogSession.Failed.Reason.ZIP_FAILED ->
                                R.string.debug_debuglog_sessions_failed_zip_error_label
                        },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (session) {
            is DebugLogSession.Recording -> IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.TwoTone.Stop,
                    contentDescription = stringResource(R.string.debug_debuglog_stop_action),
                )
            }

            is DebugLogSession.Zipping -> IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )
            }

            is DebugLogSession.Finished,
            is DebugLogSession.Failed -> IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.TwoTone.Delete,
                    contentDescription = stringResource(CommonR.string.general_delete_action),
                )
            }
        }
    }
}

@Composable
private fun SessionStatusIcon(session: DebugLogSession) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (session) {
            is DebugLogSession.Recording -> Icon(
                imageVector = Icons.Outlined.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is DebugLogSession.Zipping -> CircularProgressIndicator(
                strokeWidth = 2.dp,
            )

            is DebugLogSession.Finished -> Icon(
                imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is DebugLogSession.Failed -> Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview2
@Composable
private fun DebugLogSessionRowRecordingPreview() {
    PreviewWrapper {
        DebugLogSessionRow(
            session = DebugLogSession.Recording(
                id = SessionId("cache:sample"),
                createdAt = Instant.now(),
                logDir = File("/tmp/sample"),
                diskSize = 1024L,
            ),
            onClick = {},
            onDelete = {},
            onStop = {},
        )
    }
}

@Preview2
@Composable
private fun DebugLogSessionRowFinishedPreview() {
    PreviewWrapper {
        DebugLogSessionRow(
            session = DebugLogSession.Finished(
                id = SessionId("ext:sample"),
                createdAt = Instant.now().minusSeconds(3600),
                logDir = File("/tmp/sample"),
                diskSize = 8_000_000L,
                zipFile = File("/tmp/sample.zip"),
                compressedSize = 1_024_000L,
            ),
            onClick = {},
            onDelete = {},
            onStop = {},
        )
    }
}

@Preview2
@Composable
private fun DebugLogSessionRowFailedPreview() {
    PreviewWrapper {
        DebugLogSessionRow(
            session = DebugLogSession.Failed(
                id = SessionId("ext:sample"),
                createdAt = Instant.now().minusSeconds(7200),
                logDir = File("/tmp/sample"),
                diskSize = 0L,
                reason = DebugLogSession.Failed.Reason.ZIP_FAILED,
            ),
            onClick = {},
            onDelete = {},
            onStop = {},
        )
    }
}
