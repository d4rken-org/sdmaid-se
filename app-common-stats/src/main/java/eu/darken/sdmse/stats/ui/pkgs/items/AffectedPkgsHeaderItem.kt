package eu.darken.sdmse.stats.ui.pkgs.items

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.toSystemTimezone
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.stats.core.Report
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun AffectedPkgsHeaderItem(
    modifier: Modifier = Modifier,
    report: Report,
    rowCount: Int,
) {
    val context = LocalContext.current
    val formattedDate = remember(report.startAt) {
        report.startAt.toSystemTimezone().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    }
    val formattedDuration = remember(report.duration) {
        DateUtils.formatElapsedTime(report.duration.toSeconds())
    }
    val subtitleText = "$formattedDate ~ ($formattedDuration)"

    val countText = report.affectedCount?.let {
        pluralStringResource(CommonR.plurals.result_x_items, it, it)
    } ?: "?"

    val sizeText = report.affectedSpace?.let { Formatter.formatShortFileSize(context, it) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = report.tool.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(report.tool.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.size(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(CommonR.string.general_count_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (sizeText != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(CommonR.string.general_size_label),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Box {}
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AffectedPkgsHeaderItemPreview() {
    PreviewWrapper {
        AffectedPkgsHeaderItem(
            report = PreviewReport(
                tool = SDMTool.Type.APPCONTROL,
                startAt = Instant.now().minusSeconds(120),
                duration = Duration.ofSeconds(90),
                affectedCount = 42,
                affectedSpace = 12_345_678L,
            ),
            rowCount = 42,
        )
    }
}

@Preview2
@Composable
private fun AffectedPkgsHeaderItemNoSizePreview() {
    PreviewWrapper {
        AffectedPkgsHeaderItem(
            report = PreviewReport(
                tool = SDMTool.Type.APPCONTROL,
                startAt = Instant.now().minusSeconds(300),
                duration = Duration.ofSeconds(45),
                affectedCount = 12,
                affectedSpace = null,
            ),
            rowCount = 12,
        )
    }
}

private fun PreviewReport(
    tool: SDMTool.Type,
    startAt: Instant,
    duration: Duration,
    affectedCount: Int?,
    affectedSpace: Long?,
): Report = object : Report {
    override val reportId = java.util.UUID.randomUUID()
    override val startAt = startAt
    override val endAt = startAt.plus(duration)
    override val tool = tool
    override val status = Report.Status.SUCCESS
    override val primaryMessage = null
    override val secondaryMessage = null
    override val errorMessage = null
    override val affectedCount = affectedCount
    override val affectedSpace = affectedSpace
    override val extra = null
}
