package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool

/**
 * Floating hero card surfacing the one-tap-actionable cleanup result. Drapes over the FAB via
 * [DashboardHeroCardShape]'s bottom notch — content is kept clear of that notch by reserving
 * [DASHBOARD_CUTOUT_DEPTH] of bottom padding.
 */
@Composable
internal fun DashboardHeroCard(
    modifier: Modifier = Modifier,
    summary: DashboardViewModel.HeroSummary,
    onDismiss: () -> Unit = {},
    onToolClick: (SDMTool.Type) -> Unit = {},
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        // Same colour as the bottom bar so the chrome reads as one cohesive surface.
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = DashboardHeroCardShape,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 20.dp,
                    top = 12.dp,
                    end = 8.dp,
                    bottom = DASHBOARD_CUTOUT_DEPTH,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ByteFormatter.formatSize(context, summary.totalSize).first,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val itemsText = pluralStringResource(
                        CommonR.plurals.result_x_items,
                        summary.itemCount,
                        summary.itemCount,
                    )
                    val captionRes = when (summary.mode) {
                        DashboardViewModel.HeroSummary.Mode.FREEABLE -> R.string.dashboard_hero_will_be_freed_x_items
                        DashboardViewModel.HeroSummary.Mode.FREED -> R.string.dashboard_hero_freed_x_items
                    }
                    Text(
                        text = stringResource(captionRes, itemsText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.TwoTone.Close,
                        contentDescription = stringResource(CommonR.string.general_dismiss_action),
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                summary.tools.forEach { slice ->
                    SdmInfoChip(
                        icon = slice.type.icon,
                        label = ByteFormatter.formatSize(context, slice.size).first,
                        // Darker pills so the chips stay legible on the primary-coloured card.
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // Tool name doubles as the clickable chip's accessible name (merged with the size).
                        iconContentDescription = slice.type.toolName()?.let { stringResource(it) },
                        onClick = { onToolClick(slice.type) },
                    )
                }
            }
        }
    }
}

private fun SDMTool.Type.toolName(): Int? = when (this) {
    SDMTool.Type.CORPSEFINDER -> CommonR.string.corpsefinder_tool_name
    SDMTool.Type.SYSTEMCLEANER -> CommonR.string.systemcleaner_tool_name
    SDMTool.Type.APPCLEANER -> CommonR.string.appcleaner_tool_name
    SDMTool.Type.DEDUPLICATOR -> CommonR.string.deduplicator_tool_name
    else -> null
}

private fun previewSummary() = DashboardViewModel.HeroSummary(
    mode = DashboardViewModel.HeroSummary.Mode.FREEABLE,
    totalSize = 1_024L * 1_024L * 1_024L * 2L + 1_024L * 1_024L * 800L,
    itemCount = 31,
    tools = listOf(
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
    ),
)

@Preview2
@Composable
private fun DashboardHeroCardPreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(),
        )
    }
}
