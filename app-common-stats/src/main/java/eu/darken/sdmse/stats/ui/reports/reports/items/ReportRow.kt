package eu.darken.sdmse.stats.ui.reports.items

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.stats.R
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.Report
import eu.darken.sdmse.stats.ui.reports.ReportsViewModel
import java.time.Instant
import java.util.UUID

@Composable
fun ReportRow(
    modifier: Modifier = Modifier,
    row: ReportsViewModel.Row,
    now: Instant,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val relativeTime = remember(row.endAt, now) {
        DateUtils.getRelativeTimeSpanString(
            row.endAt.toEpochMilli(),
            now.toEpochMilli().coerceAtLeast(row.endAt.toEpochMilli()),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    val statusTint = when (row.status) {
        Report.Status.SUCCESS -> MaterialTheme.colorScheme.primary
        Report.Status.PARTIAL_SUCCESS -> MaterialTheme.colorScheme.secondary
        Report.Status.FAILURE -> MaterialTheme.colorScheme.error
    }

    val primaryText: String = when (row.status) {
        Report.Status.SUCCESS -> row.primaryMessage.orEmpty()
        Report.Status.PARTIAL_SUCCESS -> stringResource(R.string.stats_report_status_partial_success)
        Report.Status.FAILURE -> stringResource(R.string.stats_report_status_partial_failure)
    }
    val secondaryText: String = when (row.status) {
        Report.Status.SUCCESS -> row.secondaryMessage.orEmpty()
        Report.Status.PARTIAL_SUCCESS -> row.primaryMessage.orEmpty()
        Report.Status.FAILURE -> row.errorMessage.orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = row.tool.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(row.tool.labelRes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = relativeTime,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            when (row.status) {
                Report.Status.SUCCESS -> Icon(
                    imageVector = Icons.TwoTone.CheckCircle,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp),
                )
                Report.Status.PARTIAL_SUCCESS -> Icon(
                    imageVector = Icons.TwoTone.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp),
                )
                Report.Status.FAILURE -> Icon(
                    imageVector = Icons.TwoTone.WarningAmber,
                    contentDescription = null,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (primaryText.isNotEmpty()) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
        }
        if (secondaryText.isNotEmpty()) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
            )
        }
    }
}

@Preview2
@Composable
private fun ReportRowSuccessPreview() {
    PreviewWrapper {
        ReportRow(
            row = ReportsViewModel.Row(
                reportId = UUID.randomUUID(),
                tool = SDMTool.Type.CORPSEFINDER,
                status = Report.Status.SUCCESS,
                endAt = Instant.now().minusSeconds(120),
                primaryMessage = "Freed 12 MB",
                secondaryMessage = "Removed 42 items",
                errorMessage = null,
            ),
            now = Instant.now(),
            onClick = {},
        )
    }
}

@Preview2
@Composable
private fun ReportRowPartialPreview() {
    PreviewWrapper {
        ReportRow(
            row = ReportsViewModel.Row(
                reportId = UUID.randomUUID(),
                tool = SDMTool.Type.APPCLEANER,
                status = Report.Status.PARTIAL_SUCCESS,
                endAt = Instant.now().minusSeconds(3600),
                primaryMessage = "Freed 4 MB, 2 items could not be removed",
                secondaryMessage = null,
                errorMessage = null,
            ),
            now = Instant.now(),
            onClick = {},
        )
    }
}

@Preview2
@Composable
private fun ReportRowFailurePreview() {
    PreviewWrapper {
        ReportRow(
            row = ReportsViewModel.Row(
                reportId = UUID.randomUUID(),
                tool = SDMTool.Type.DEDUPLICATOR,
                status = Report.Status.FAILURE,
                endAt = Instant.now().minusSeconds(86400),
                primaryMessage = null,
                secondaryMessage = null,
                errorMessage = "Storage access revoked mid-operation",
            ),
            now = Instant.now(),
            onClick = {},
        )
    }
}
