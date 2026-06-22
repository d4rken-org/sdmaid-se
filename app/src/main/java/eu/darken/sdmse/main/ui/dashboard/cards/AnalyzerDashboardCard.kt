package eu.darken.sdmse.main.ui.dashboard.cards

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.analyzer.R as AnalyzerR
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.common.compose.shimmer.ShimmerLine
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton

import kotlin.math.absoluteValue

data class AnalyzerDashboardCardItem(
    val data: Analyzer.Data?,
    val progress: Progress.Data?,
    val combinedDelta: Long? = null,
    val isLoadingTrend: Boolean = false,
    val onViewDetails: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun AnalyzerDashboardCard(item: AnalyzerDashboardCardItem) {
    DashboardCard(onClick = item.onViewDetails) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.TwoTone.DataUsage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(CommonR.string.analyzer_tool_name),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(AnalyzerR.string.analyzer_explanation_short),
            style = MaterialTheme.typography.bodySmall,
        )

        // AnimatedVisibility so a resolved-but-absent trend (fresh installs) collapses smoothly
        // instead of snapping the card height after the shimmer.
        AnimatedVisibility(visible = item.isLoadingTrend || item.combinedDelta != null) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                val delta = item.combinedDelta
                if (delta == null) {
                    ShimmerLine(height = 14.dp)
                } else {
                    val absDelta = Formatter.formatShortFileSize(LocalContext.current, delta.absoluteValue)
                    val signedDelta = when {
                        delta > 0 -> "+$absDelta"
                        delta < 0 -> "-$absDelta"
                        else -> absDelta
                    }
                    Text(
                        text = stringResource(AnalyzerR.string.analyzer_storage_trend_delta_in_7d, signedDelta),
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            delta > 0 -> MaterialTheme.colorScheme.error
                            delta < 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardFlatActionButton(
            onClick = item.onViewDetails,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Visibility,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(text = stringResource(CommonR.string.general_open_action))
        }
    }
}

@Preview2
@Composable
private fun AnalyzerDashboardCardPreview() {
    PreviewWrapper {
        AnalyzerDashboardCard(
            item = AnalyzerDashboardCardItem(
                data = null,
                progress = null,
                combinedDelta = 512L * 1024L * 1024L,
                onViewDetails = {},
            ),
        )
    }
}

@Preview2
@Composable
private fun AnalyzerDashboardCardLoadingPreview() {
    PreviewWrapper {
        AnalyzerDashboardCard(
            item = AnalyzerDashboardCardItem(
                data = null,
                progress = null,
                isLoadingTrend = true,
                onViewDetails = {},
            ),
        )
    }
}
