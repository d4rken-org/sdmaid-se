package eu.darken.sdmse.main.ui.dashboard.bottom

import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Stars
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import eu.darken.sdmse.main.ui.dashboard.cards.toolNameRes
import eu.darken.sdmse.main.ui.dashboard.showsUpgradeBlock
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
    summary: HeroSummary,
    now: Instant = Instant.now(),
    onDismiss: () -> Unit = {},
    onDiscard: (() -> Unit)? = null,
    onToolClick: (HeroSummary.Mode, SDMTool.Type) -> Unit = { _, _ -> },
    onLockedToolClick: (SDMTool.Type) -> Unit = {},
    onUpgrade: () -> Unit = {},
    entryFocusRequester: FocusRequester? = null,
) {
    // Explicit D-pad ladder through the card — spatial search is unreliable here (the Dismiss X
    // sits top-right outside most beams and picks vary by screen size): [entryFocusRequester]
    // (UP from the bar/FAB) lands on Discard when present, else on the X; UP from Discard and
    // the tool chips goes to the X; UP from the X falls out of the dock (back to the grid).
    val dismissFocusRequester = remember { FocusRequester() }
    // Colour tracks the action: destructive (red) while a deletion is pending, positive once freed,
    // neutral when the cleanup ran but came up empty (nothing was lost, nothing was gained) — and
    // neutral too when everything is locked, where the main action deletes nothing and the
    // destructive red would be a lie.
    val (containerColor, contentColor) = when (summary.mode) {
        HeroSummary.Mode.FREEABLE ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError

        HeroSummary.Mode.FREED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer

        HeroSummary.Mode.NOTHING_FREED, HeroSummary.Mode.LOCKED_ONLY ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.weight(1f),
                summary = summary,
                onDismiss = onDismiss,
                onToolClick = onToolClick,
                onLockedToolClick = onLockedToolClick,
                onUpgrade = onUpgrade,
                dismissFocusRequester = dismissFocusRequester,
                dismissEntryRequester = entryFocusRequester.takeIf { onDiscard == null },
            )
            HeroFooter(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DASHBOARD_CUTOUT_DEPTH),
                summary = summary,
                now = now,
                onDiscard = onDiscard,
                dismissFocusRequester = dismissFocusRequester,
                discardEntryRequester = entryFocusRequester.takeIf { onDiscard != null },
            )
        }
    }
}

@Composable
private fun HeroBody(
    modifier: Modifier = Modifier,
    summary: HeroSummary,
    onDismiss: () -> Unit,
    onToolClick: (HeroSummary.Mode, SDMTool.Type) -> Unit,
    onLockedToolClick: (SDMTool.Type) -> Unit = {},
    onUpgrade: () -> Unit = {},
    dismissFocusRequester: FocusRequester = FocusRequester(),
    dismissEntryRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val modeRes = summary.mode.resources
    // NOTHING_FREED carries no size and no item count, so its headline and caption are plain
    // sentences rather than templates wrapped around a number.
    val hasAmounts = summary.mode != HeroSummary.Mode.NOTHING_FREED
    val isLockedOnly = summary.mode == HeroSummary.Mode.LOCKED_ONLY
    val showsBlock = summary.showsUpgradeBlock
    // Without free chips there is nothing for a nested block to be additional to, so the locked
    // chips join the main row instead and the card keeps its normal height.
    val flatLocked = if (showsBlock) emptyList() else summary.lockedTools
    // Only LOCKED_ONLY turns its hint into a tappable unlock line: NOTHING_FREED's hint diagnoses
    // why the cleanup came up empty and must not be displaced.
    val showUnlockLine = flatLocked.isNotEmpty() && isLockedOnly
    Column(modifier = modifier) {
        // Header insets are split between the row and the text column so the two can differ without
        // either costing horizontal room. The dismiss button's inset is the row's (8.dp top, 8.dp
        // end), keeping it square in the corner; the headline's extra 4.dp is the column's, so it
        // still starts 12.dp below the card's top edge. Padding the *button* instead would come out
        // of the text column's weight(1f) share, narrowing the headline and caption on a small
        // display at a large font scale until the caption wraps onto a line the height has no room
        // for.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            ) {
                // One sentence with the size inline ("3.7 GB can be freed") — the smaller label
                // text is spanned around the headline-sized number. Split on the placeholder so
                // translations may put label text before and/or after the size.
                val template = stringResource(modeRes.headline)
                // LOCKED_ONLY's amounts live in the locked slices; totalSize is 0 there by design.
                val headlineSize = if (isLockedOnly) summary.lockedSize else summary.totalSize
                val sizeText = ByteFormatter.formatSize(context, headlineSize).first
                val labelSpan = MaterialTheme.typography.titleSmall.toSpanStyle()
                    .copy(color = LocalContentColor.current.copy(alpha = MUTED_ALPHA))
                Text(
                    text = buildAnnotatedString {
                        val sizeAt = template.indexOf(SIZE_ARG)
                        when {
                            !hasAmounts -> withStyle(labelSpan) { append(template) }
                            sizeAt == -1 -> append(sizeText)
                            else -> {
                                withStyle(labelSpan) { append(template.take(sizeAt)) }
                                append(sizeText)
                                withStyle(labelSpan) { append(template.substring(sizeAt + SIZE_ARG.length)) }
                            }
                        }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val captionCount = if (isLockedOnly) summary.lockedCount else summary.itemCount
                val captionText = when (val caption = modeRes.caption) {
                    is HeroCaption.Counted ->
                        pluralStringResource(caption.id, captionCount, captionCount)

                    is HeroCaption.Plain -> stringResource(caption.id)
                }
                Text(
                    // Two lines so the item-count result reads fully at large font scales (the column
                    // is narrow next to the dismiss button); the card height grows to make room.
                    text = captionText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // What the run left behind, when it left anything. Muted and under the caption: it
                // qualifies the freed total rather than competing with it.
                if (summary.residueSize > 0L) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_hero_freed_residue,
                            ByteFormatter.formatSize(context, summary.residueSize).first,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(dismissFocusRequester)
                    .then(dismissEntryRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Close,
                    contentDescription = stringResource(CommonR.string.general_dismiss_action),
                )
            }
        }

        // The body is sized for the worst case (two chip rows + two-line hint); in smaller
        // configurations the slack collects here so chips + hint stay anchored above the footer.
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            summary.tools.forEach { slice ->
                SdmInfoChip(
                    modifier = Modifier.focusProperties { up = dismissFocusRequester },
                    icon = slice.type.icon,
                    label = ByteFormatter.formatSize(context, slice.size).first,
                    // Neutral pills so they stay legible on either the error- or tertiary-tinted card.
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
                    // Tool name doubles as the clickable chip's accessible name (merged with the size).
                    iconContentDescription = stringResource(toolNameRes(slice.type)),
                    onClick = { onToolClick(summary.mode, slice.type) },
                )
            }
            flatLocked.forEach { slice ->
                LockedToolChip(
                    slice = slice,
                    dismissFocusRequester = dismissFocusRequester,
                    onLockedToolClick = onLockedToolClick,
                )
            }
        }

        // Directly under the chips it talks about ("or a chip to check first"). LOCKED_ONLY shows the
        // tappable unlock row below instead — the two are mutually exclusive, and are two separate
        // conditions rather than one if/else only so that each keeps its own side of the block.
        if (!showUnlockLine) {
            Text(
                modifier = Modifier.padding(top = 2.dp, start = 20.dp, end = 20.dp),
                text = stringResource(modeRes.hint),
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showsBlock) {
            UpgradeBlock(
                modifier = Modifier
                    .padding(top = 8.dp, start = 20.dp, end = 20.dp),
                lockedTools = summary.lockedTools,
                dismissFocusRequester = dismissFocusRequester,
                onLockedToolClick = onLockedToolClick,
                onUpgrade = onUpgrade,
            )
        }

        // LOCKED_ONLY's stand-in for the hint above: the same sentence, but the whole line is the
        // upgrade target. Last, right above the footer strip. It never shares the card with the
        // block — a LOCKED_ONLY summary has no free chips, so showsBlock is false whenever this is
        // true — which is why the two conditions above and here can't both fire.
        if (showUnlockLine) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp, start = 20.dp, end = 20.dp)
                    // The whole line is the target, not just the glyph run.
                    .fillMaxWidth()
                    // Reachable on TV, so it has to join the card's explicit D-pad ladder — a
                    // focusable row outside it is a dead end there.
                    .focusProperties { up = dismissFocusRequester }
                    .clickable(role = Role.Button, onClick = onUpgrade),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_hero_locked_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A Pro-gated finding, styled exactly like a free chip — same tool icon, same neutral pill. On device
 * a star in place of the tool icon made it read as a different *kind* of object from the free chips
 * beside it; what marks it as gated is where it sits (inside the [UpgradeBlock], or on a LOCKED_ONLY
 * card), not a different glyph.
 */
@Composable
private fun LockedToolChip(
    modifier: Modifier = Modifier,
    slice: HeroSummary.ToolSlice,
    dismissFocusRequester: FocusRequester,
    onLockedToolClick: (SDMTool.Type) -> Unit,
) {
    val context = LocalContext.current
    SdmInfoChip(
        modifier = modifier.focusProperties { up = dismissFocusRequester },
        icon = slice.type.icon,
        label = ByteFormatter.formatSize(context, slice.size).first,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
        iconContentDescription = stringResource(toolNameRes(slice.type)),
        // NOT onToolClick: in FREED mode that routes to a report, and a locked tool has none — it
        // was never cleaned.
        onClick = { onLockedToolClick(slice.type) },
    )
}

/**
 * The nested "more with Pro" block: a starred caption over its own row of locked tool chips. Shown
 * only when free findings exist too (see [showsUpgradeBlock]), which is what makes a second,
 * subordinate category meaningful.
 *
 * The caption carries no size — the chips sit directly below it and there is usually just one
 * (Deduplicator is off by default in one-tap, leaving AppCleaner alone), so a total here would print
 * the same figure twice.
 *
 * The whole block is one upgrade target; the chips inside stay individually clickable and consume
 * their own taps, so tapping a chip opens that tool instead of the upgrade screen.
 *
 * The container is the hero's own content colour at a low alpha rather than a palette colour: the
 * block has to read as nested on both the error-red FREEABLE card and the tertiary FREED one, and a
 * translucent wash of what the card already uses is the one thing that works on both.
 */
@Composable
private fun UpgradeBlock(
    modifier: Modifier = Modifier,
    lockedTools: List<HeroSummary.ToolSlice>,
    dismissFocusRequester: FocusRequester,
    onLockedToolClick: (SDMTool.Type) -> Unit,
    onUpgrade: () -> Unit,
) {
    val blockContentColor = LocalContentColor.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { up = dismissFocusRequester }
            .clickable(role = Role.Button, onClick = onUpgrade),
        color = blockContentColor.copy(alpha = BLOCK_CONTAINER_ALPHA),
        contentColor = blockContentColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = Icons.TwoTone.Stars,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.dashboard_hero_locked_block_caption),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                lockedTools.forEach { slice ->
                    LockedToolChip(
                        slice = slice,
                        dismissFocusRequester = dismissFocusRequester,
                        onLockedToolClick = onLockedToolClick,
                    )
                }
            }
        }
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
    summary: HeroSummary,
    now: Instant,
    onDiscard: (() -> Unit)?,
    dismissFocusRequester: FocusRequester = FocusRequester(),
    discardEntryRequester: FocusRequester? = null,
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
                val timestampDescription =
                    stringResource(summary.mode.resources.timestampDescription, relativeTime)
                Text(
                    modifier = Modifier.semantics { contentDescription = timestampDescription },
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
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
                    modifier = Modifier
                        .height(DASHBOARD_CUTOUT_DEPTH)
                        .focusProperties { up = dismissFocusRequester }
                        .then(discardEntryRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
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
private const val MUTED_ALPHA = 0.8f

/** Wash strength for the nested upgrade block; low enough to stay subordinate on every card colour. */
private const val BLOCK_CONTAINER_ALPHA = 0.14f

/**
 * A mode's caption is either driven by the item count or a plain sentence, and the two need different
 * resource types. Modelled rather than left as a bare id so a mode cannot be wired to the wrong one.
 */
private sealed interface HeroCaption {
    /** Must be a `<plurals>`: languages with verb agreement need a different form per quantity. */
    data class Counted(@PluralsRes val id: Int) : HeroCaption
    data class Plain(@StringRes val id: Int) : HeroCaption
}

/** Per-mode string resources, kept together so each mode is defined in one place. */
private data class HeroModeResources(
    @StringRes val headline: Int,
    val caption: HeroCaption,
    @StringRes val hint: Int,
    @StringRes val timestampDescription: Int,
)

private val HeroSummary.Mode.resources: HeroModeResources
    get() = when (this) {
        HeroSummary.Mode.FREEABLE -> HeroModeResources(
            headline = R.string.dashboard_hero_freeable_headline,
            caption = HeroCaption.Counted(R.plurals.dashboard_hero_freeable_x_items),
            hint = R.string.dashboard_hero_freeable_hint,
            timestampDescription = R.string.dashboard_hero_scanned_timestamp_description,
        )

        HeroSummary.Mode.FREED -> HeroModeResources(
            headline = R.string.dashboard_hero_freed_headline,
            caption = HeroCaption.Counted(R.plurals.dashboard_hero_freed_x_items),
            hint = R.string.dashboard_hero_freed_hint,
            timestampDescription = R.string.dashboard_hero_freed_timestamp_description,
        )

        HeroSummary.Mode.NOTHING_FREED -> HeroModeResources(
            headline = R.string.dashboard_hero_nothing_freed_headline,
            caption = HeroCaption.Plain(R.string.dashboard_hero_nothing_freed_caption),
            hint = R.string.dashboard_hero_nothing_freed_hint,
            timestampDescription = R.string.dashboard_hero_nothing_freed_timestamp_description,
        )

        HeroSummary.Mode.LOCKED_ONLY -> HeroModeResources(
            headline = R.string.dashboard_hero_locked_headline,
            caption = HeroCaption.Counted(R.plurals.dashboard_hero_locked_x_items),
            hint = R.string.dashboard_hero_locked_hint,
            // Scan data, same as FREEABLE — the timestamp means the same thing here.
            timestampDescription = R.string.dashboard_hero_scanned_timestamp_description,
        )
    }

private fun previewSummary(
    mode: HeroSummary.Mode = HeroSummary.Mode.FREEABLE,
    tools: List<HeroSummary.ToolSlice> = listOf(
        HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
        HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
        HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
    ),
) = HeroSummary(
    mode = mode,
    totalSize = tools.sumOf { it.size },
    itemCount = tools.sumOf { it.count },
    tools = tools,
    timestamp = Instant.now().minusSeconds(5 * 60),
)

@Composable
private fun HeroCardPreview(
    summary: HeroSummary,
    onDiscard: (() -> Unit)? = null,
) {
    PreviewWrapper {
        DashboardHeroCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(dashboardHeroCardHeight(summary.showsUpgradeBlock))
                .padding(horizontal = DASHBOARD_HERO_HORIZONTAL_MARGIN),
            summary = summary,
            onDiscard = onDiscard,
        )
    }
}

@Preview2
@Composable
private fun DashboardHeroCardFreeablePreview() {
    HeroCardPreview(
        summary = previewSummary(mode = HeroSummary.Mode.FREEABLE),
        onDiscard = {},
    )
}

@Preview2
@Composable
private fun DashboardHeroCardFreedPreview() {
    HeroCardPreview(summary = previewSummary(mode = HeroSummary.Mode.FREED))
}

@Preview2
@Composable
private fun DashboardHeroCardNothingFreedPreview() {
    HeroCardPreview(summary = previewSummary(mode = HeroSummary.Mode.NOTHING_FREED, tools = emptyList()))
}

// The one branch where locked chips render *without* the nested block: with no free chips there is
// nothing for the block to be additional to, so the chips stay flat in the main row. It is also the
// one mode whose hint is kept rather than turned into an unlock line — that sentence diagnoses why
// the cleanup came up empty, and an upsell in its place would drop the explanation.
@Preview2
@Composable
private fun DashboardHeroCardNothingFreedWithLockedPreview() {
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.NOTHING_FREED,
            totalSize = 0L,
            itemCount = 0,
            tools = emptyList(),
            timestamp = Instant.now().minusSeconds(60),
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
            ),
        ),
    )
}

@Preview2
@Composable
private fun DashboardHeroCardLockedOnlyPreview() {
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.LOCKED_ONLY,
            totalSize = 0L,
            itemCount = 0,
            tools = emptyList(),
            timestamp = Instant.now().minusSeconds(5 * 60),
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
            ),
        ),
    )
}

// The shape most non-Pro users actually get, and worth its own slot beside the two-tool variant: the
// Deduplicator is off by default in one-tap, so AppCleaner is usually the only locked tool and the
// card is carried by a single chip. A layout that only looks balanced with two chips fails here.
@Preview2
@Composable
private fun DashboardHeroCardLockedOnlySingleToolPreview() {
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.LOCKED_ONLY,
            totalSize = 0L,
            itemCount = 0,
            tools = emptyList(),
            timestamp = Instant.now().minusSeconds(5 * 60),
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
            ),
        ),
    )
}

// The nested-block layout at its fullest: a freed result with a leftover line, its own chip row, and
// the upgrade block below carrying two more chips. This is the case the taller
// DASHBOARD_HERO_CONTENT_HEIGHT_WITH_BLOCK is sized for.
@Preview2
@Composable
private fun DashboardHeroCardFreedWithLockedPreview() {
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1_024L * 1_024L * 1_724L,
            itemCount = 26,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
                HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
            ),
            timestamp = Instant.now().minusSeconds(60),
            residueSize = 1_024L * 1_024L * 5L,
            residueCount = 2,
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
            ),
        ),
    )
}

// The same block layout on the error-tinted FREEABLE card, where the nested wash has to read as
// subordinate against a very different background than FREED's.
//
// For a FREEABLE summary two free chips is the maximum alongside the block: the block only appears
// when the user is not Pro, and buildHeroSummary routes both Pro-gated tools (AppCleaner,
// Deduplicator) into lockedTools in exactly that case, leaving tools with CorpseFinder and
// SystemCleaner at most.
//
// That ceiling is FREEABLE-only — do not generalise it to the card. FREED summaries are not built by
// buildHeroSummary at all: accumulateFreed assembles their tools from whichever tools actually ran a
// cleanup, with no entitlement filter, and the settled path then attaches lockedTools filtered only
// against the tools the run submitted to. So a tool that ran despite the combine reporting non-Pro
// lands among the free chips while a still-gated tool that never ran stays locked — three free chips
// plus the block. See DashboardHeroCardFreedWithThreeFreeAndLockedPreview.
@Preview2
@Composable
private fun DashboardHeroCardFreeableWithLockedPreview() {
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.FREEABLE,
            totalSize = 1_024L * 1_024L * 1_724L,
            itemCount = 26,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
                HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
            ),
            timestamp = Instant.now().minusSeconds(5 * 60),
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
            ),
        ),
        onDiscard = {},
    )
}

// The most content the block layout can carry, and the reason the FREEABLE two-chip ceiling above
// must not be read as a card-wide invariant: three free chips, a leftover line, and the block.
//
// One reachable path: a Pro user cleans CorpseFinder, SystemCleaner and AppCleaner while
// Deduplicator is disabled in one-tap; afterward the entitlement lapses and Deduplicator is enabled
// while its findings remain. The three cleaned slices stay in tools, while the never-submitted
// Deduplicator enters lockedTools.
//
// Every clause of that is load-bearing. lockedSlices returns nothing at all while isPro, so the
// lapse alone is not enough; and had Deduplicator been enabled with findings during the cleanup, the
// DELETE branch would have submitted it, after which the settled path's filter against the run's
// submitted set drops it from lockedTools again.
//
// The fail-open isProForUi() read reaches the same shape, but only when the dashboard's own upgrade
// flow reports non-Pro AND Deduplicator independently satisfies lockedSlices: enabled, with
// findings, never submitted.
@Preview2
@Composable
private fun DashboardHeroCardFreedWithThreeFreeAndLockedPreview() {
    val tools = listOf(
        HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_023L, 12),
        HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
        HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 512L, 87),
    )
    HeroCardPreview(
        summary = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = tools.sumOf { it.size },
            itemCount = tools.sumOf { it.count },
            tools = tools,
            timestamp = Instant.now().minusSeconds(60),
            residueSize = 1_024L * 1_024L * 5L,
            residueCount = 2,
            lockedTools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
            ),
        ),
    )
}

// Worst case: all four tools (chips wrap to a second row) + the two-line freeable hint — validates
// card height.
@Preview2
@Composable
private fun DashboardHeroCardFreeableAllToolsPreview() {
    HeroCardPreview(
        summary = previewSummary(
            mode = HeroSummary.Mode.FREEABLE,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1_024L * 1_024L * 1_024L, 12),
                HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1_024L * 1_024L * 700L, 14),
                HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 1_024L * 1_024L * 1_024L, 5),
                HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 1_024L * 1_024L * 512L, 3),
            ),
        ),
        onDiscard = {},
    )
}
