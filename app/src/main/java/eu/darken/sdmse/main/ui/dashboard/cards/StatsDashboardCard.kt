package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.stats.R as StatsR
import eu.darken.sdmse.common.ui.R as UiR

import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton
import eu.darken.sdmse.stats.core.StatsRepo

data class StatsDashboardCardItem(
    val state: StatsRepo.State,
    val showProRequirement: Boolean,
    val onViewAction: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun StatsDashboardCard(item: StatsDashboardCardItem) {
    val context = LocalContext.current
    val highlightColor = MaterialTheme.colorScheme.primary
    val bodyText = remember(item.state) {
        val hasCleanupData = item.state.reportsCount > 0 || item.state.totalSpaceFreed > 0L
        if (!hasCleanupData) return@remember null

        val (space, spaceQuantity) = ByteFormatter.formatSize(context, item.state.totalSpaceFreed)
        val spaceFormatted = context.resources.getQuantityString(
            StatsR.plurals.stats_dash_body_size,
            spaceQuantity,
            space,
        )
        val processed = item.state.itemsProcessed.toString()
        val processedFormatted = context.resources.getQuantityString(
            StatsR.plurals.stats_dash_body_count,
            item.state.itemsProcessed.toInt(),
            processed,
        )
        val wholeText = "$spaceFormatted $processedFormatted"
        buildAnnotatedString {
            append(wholeText)
            val firstStart = wholeText.indexOf(space)
            if (firstStart >= 0) {
                addStyle(SpanStyle(color = highlightColor), firstStart, firstStart + space.length)
            }
            val secondStart = wholeText.indexOf(processed, startIndex = (firstStart + space.length).coerceAtLeast(0))
            if (secondStart >= 0) {
                addStyle(SpanStyle(color = highlightColor), secondStart, secondStart + processed.length)
            }
        }
    }

    DashboardCard(onClick = item.onViewAction) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_chartbox_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(CommonR.string.stats_label),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = bodyText ?: buildAnnotatedString {
                append(stringResource(StatsR.string.stats_dash_body_snapshots_only))
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DashboardFlatActionButton(onClick = item.onViewAction) {
                if (item.showProRequirement) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_baseline_stars_24),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                }
                Text(text = stringResource(CommonR.string.general_view_action))
            }
        }
    }
}

@Preview2
@Composable
private fun StatsDashboardCardPreview() {
    PreviewWrapper {
        StatsDashboardCard(
            item = StatsDashboardCardItem(
                state = StatsRepo.State(
                    reportsCount = 12,
                    snapshotsCount = 18,
                    totalSpaceFreed = 5L * 1024L * 1024L * 1024L,
                    itemsProcessed = 320,
                    databaseSize = 256L * 1024L,
                ),
                showProRequirement = false,
                onViewAction = {},
            ),
        )
    }
}
