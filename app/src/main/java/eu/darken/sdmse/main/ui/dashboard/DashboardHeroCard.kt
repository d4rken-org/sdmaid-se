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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
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
    onToolClick: (DashboardViewModel.HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    // Colour tracks the action: destructive (red) while a deletion is pending, positive once freed.
    val (containerColor, contentColor) = when (summary.mode) {
        DashboardViewModel.HeroSummary.Mode.FREEABLE ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError

        DashboardViewModel.HeroSummary.Mode.FREED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val hintRes = when (summary.mode) {
                        DashboardViewModel.HeroSummary.Mode.FREEABLE -> R.string.dashboard_hero_freeable_hint
                        DashboardViewModel.HeroSummary.Mode.FREED -> R.string.dashboard_hero_freed_hint
                    }
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = stringResource(hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
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
                    .padding(top = 2.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                summary.tools.forEach { slice ->
                    SdmInfoChip(
                        icon = slice.type.icon,
                        label = ByteFormatter.formatSize(context, slice.size).first,
                        // Neutral pills so they stay legible on either the error- or tertiary-tinted card.
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
                        // Tool name doubles as the clickable chip's accessible name (merged with the size).
                        iconContentDescription = slice.type.toolName()?.let { stringResource(it) },
                        onClick = { onToolClick(summary.mode, slice.type) },
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

private fun previewSummary(
    mode: DashboardViewModel.HeroSummary.Mode = DashboardViewModel.HeroSummary.Mode.FREEABLE,
    tools: List<DashboardViewModel.HeroSummary.ToolSlice> = listOf(
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
        DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
    ),
) = DashboardViewModel.HeroSummary(
    mode = mode,
    totalSize = tools.sumOf { it.size },
    itemCount = tools.filter { it.type != SDMTool.Type.DEDUPLICATOR }.sumOf { it.count },
    tools = tools,
)

@Preview2
@Composable
private fun DashboardHeroCardFreeablePreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(mode = DashboardViewModel.HeroSummary.Mode.FREEABLE),
        )
    }
}

@Preview2
@Composable
private fun DashboardHeroCardFreedPreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(mode = DashboardViewModel.HeroSummary.Mode.FREED),
        )
    }
}

// Worst case: all four tools (chips wrap to a second row) + the hint line — validates card height.
@Preview2
@Composable
private fun DashboardHeroCardFreedAllToolsPreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(
                mode = DashboardViewModel.HeroSummary.Mode.FREED,
                tools = listOf(
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
                ),
            ),
        )
    }
}
