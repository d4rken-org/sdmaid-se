package eu.darken.sdmse.main.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.compose.SdmInfoChip
import eu.darken.sdmse.common.compose.icons.icon
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool
import java.time.Instant
import eu.darken.sdmse.common.R as CommonR

/**
 * Floating hero card surfacing the one-tap-actionable cleanup result. Drapes over the FAB via
 * [DashboardHeroCardShape]'s bottom notch — the body stays clear of that notch, while the
 * [DASHBOARD_CUTOUT_DEPTH]-tall shoulders beside it carry a footer: the result's age at the start,
 * and (when [onDiscard] is set) a Discard action at the end.
 */
@Composable
internal fun DashboardHeroCard(
    modifier: Modifier = Modifier,
    summary: DashboardViewModel.HeroSummary,
    now: Instant = Instant.now(),
    onDismiss: () -> Unit = {},
    onDiscard: (() -> Unit)? = null,
    onToolClick: (DashboardViewModel.HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
) {
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
        Column(modifier = Modifier.fillMaxSize()) {
            HeroBody(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp, top = 12.dp, end = 8.dp),
                summary = summary,
                onDismiss = onDismiss,
                onToolClick = onToolClick,
            )
            HeroFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DASHBOARD_CUTOUT_DEPTH),
                summary = summary,
                now = now,
                onDiscard = onDiscard,
            )
        }
    }
}

@Composable
private fun HeroBody(
    modifier: Modifier = Modifier,
    summary: DashboardViewModel.HeroSummary,
    onDismiss: () -> Unit,
    onToolClick: (DashboardViewModel.HeroSummary.Mode, SDMTool.Type) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // One sentence with the size inline ("3.7 GB can be freed") — the smaller label
                // text is spanned around the headline-sized number. Split on the placeholder so
                // translations may put label text before and/or after the size.
                val headlineRes = when (summary.mode) {
                    DashboardViewModel.HeroSummary.Mode.FREEABLE -> R.string.dashboard_hero_freeable_headline
                    DashboardViewModel.HeroSummary.Mode.FREED -> R.string.dashboard_hero_freed_headline
                }
                val template = stringResource(headlineRes)
                val sizeText = ByteFormatter.formatSize(context, summary.totalSize).first
                val labelSpan = MaterialTheme.typography.titleSmall.toSpanStyle()
                    .copy(color = LocalContentColor.current.copy(alpha = 0.8f))
                Text(
                    text = buildAnnotatedString {
                        val sizeAt = template.indexOf(SIZE_ARG)
                        if (sizeAt == -1) {
                            append(sizeText)
                        } else {
                            withStyle(labelSpan) { append(template.take(sizeAt)) }
                            append(sizeText)
                            withStyle(labelSpan) { append(template.substring(sizeAt + SIZE_ARG.length)) }
                        }
                    },
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
                    DashboardViewModel.HeroSummary.Mode.FREEABLE -> R.string.dashboard_hero_freeable_x_items
                    DashboardViewModel.HeroSummary.Mode.FREED -> R.string.dashboard_hero_freed_x_items
                }
                Text(
                    text = stringResource(captionRes, itemsText),
                    style = MaterialTheme.typography.bodyMedium,
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

        // The body is sized for the worst case (two chip rows + two-line hint); in smaller
        // configurations the slack collects here so chips + hint stay anchored above the footer.
        Spacer(modifier = Modifier.height(16.dp))

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

        // Last, right above the footer strip: the hint references its direct neighbours — "the
        // button below" and the category chips above.
        val hintRes = when (summary.mode) {
            DashboardViewModel.HeroSummary.Mode.FREEABLE -> R.string.dashboard_hero_freeable_hint
            DashboardViewModel.HeroSummary.Mode.FREED -> R.string.dashboard_hero_freed_hint
        }
        Text(
            modifier = Modifier.padding(top = 4.dp, end = 12.dp),
            text = stringResource(hintRes),
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip beside the FAB notch: relative result age at the start, Discard at the end. Two
 * equal-weight cells around a notch-wide spacer keep both clear of the cutout walls (and mirror
 * correctly in RTL).
 */
@Composable
private fun HeroFooter(
    modifier: Modifier = Modifier,
    summary: DashboardViewModel.HeroSummary,
    now: Instant,
    onDiscard: (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .padding(start = 24.dp, bottom = 8.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            summary.timestamp?.let { timestamp ->
                // Framework-localized ("5 min. ago") — short in every locale, no translatable string.
                val relativeTime = remember(timestamp, now) {
                    DateUtils.getRelativeTimeSpanString(
                        timestamp.toEpochMilli(),
                        now.toEpochMilli().coerceAtLeast(timestamp.toEpochMilli()),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                }
                val timestampDescription = stringResource(
                    when (summary.mode) {
                        DashboardViewModel.HeroSummary.Mode.FREEABLE ->
                            R.string.dashboard_hero_scanned_timestamp_description

                        DashboardViewModel.HeroSummary.Mode.FREED ->
                            R.string.dashboard_hero_freed_timestamp_description
                    },
                    relativeTime,
                )
                Text(
                    modifier = Modifier.semantics { contentDescription = timestampDescription },
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Keeps both cells clear of the notch walls; extra width covers the convex shoulder fillets.
        Spacer(modifier = Modifier.width(DASHBOARD_CUTOUT_WIDTH + 16.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (onDiscard != null) {
                TextButton(
                    modifier = Modifier.height(DASHBOARD_CUTOUT_DEPTH),
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(CommonR.string.general_discard_action),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val SIZE_ARG = "%1\$s"

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
    timestamp = Instant.now().minusSeconds(5 * 60),
)

@Preview2
@Composable
private fun DashboardHeroCardFreeablePreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(DASHBOARD_HERO_CARD_HEIGHT)
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(mode = DashboardViewModel.HeroSummary.Mode.FREEABLE),
            onDiscard = {},
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
                .height(DASHBOARD_HERO_CARD_HEIGHT)
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(mode = DashboardViewModel.HeroSummary.Mode.FREED),
        )
    }
}

// Worst case: all four tools (chips wrap to a second row) + the two-line freeable hint — validates
// card height.
@Preview2
@Composable
private fun DashboardHeroCardFreeableAllToolsPreview() {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(DASHBOARD_HERO_CARD_HEIGHT)
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = previewSummary(
                mode = DashboardViewModel.HeroSummary.Mode.FREEABLE,
                tools = listOf(
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                    DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
                ),
            ),
            onDiscard = {},
        )
    }
}
