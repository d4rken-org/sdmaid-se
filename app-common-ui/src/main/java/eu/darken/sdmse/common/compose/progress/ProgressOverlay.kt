package eu.darken.sdmse.common.compose.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.common.progress.determinateFraction

private const val ENTRANCE_DURATION_MS = 180

// "Bold hero" ring: large, thick, rounded. The %/count and both progress messages live INSIDE the ring.
private val RING_MIN = 240.dp
private val RING_MAX = 300.dp
private const val RING_STROKE_FACTOR = 0.032f // stroke width as a fraction of the ring diameter (~9.6dp @ 300dp)
private const val RING_INNER_WIDTH_FACTOR = 0.64f // text column max width as a fraction of the ring diameter

// Nested sub-progress ring: the current item's own progress, drawn inside the item ring.
// At the 300dp maximum that's a 258dp diameter with a ~6.2dp stroke, so its inside edge (~245dp)
// clears the 192dp text column.
private const val RING_SUB_SIZE_FACTOR = 0.86f
private const val RING_SUB_STROKE_FACTOR = 0.024f

// Used when the theme's hero line height isn't expressed in sp (Density.toDp() throws for
// TextUnit.Unspecified and for em values).
private val HERO_HEIGHT_FALLBACK = 52.dp

// Must stay below the height of two bodySmall lines, otherwise the extra slot outgrows the two-line
// reservation it sits in and drives the block height itself, so the vertically centered column jumps
// when a payload appears. bodySmall's lineHeight is 16sp, so two lines are ~32dp at fontScale 1.0 and
// ~27dp at the 0.85 minimum.
private val PROGRESS_EXTRA_ICON_SIZE = 20.dp

/** Which count supplies the hero number: sub-progress when it is determinate, else the overall count. */
internal fun heroCount(count: Progress.Count, subCount: Progress.Count?): Progress.Count =
    if (subCount.determinateFraction() != null) subCount!! else count

/**
 * When [data] is non-null:
 *  - the wrapped [content] is hidden (alpha 0) so stale items are not visible,
 *  - the overlay panel consumes all pointer input so stale items are not tappable,
 *  - the overlay fades+scales in over 180ms.
 *
 * When [data] is null, the content is visible and the overlay is removed from the layout.
 */
@Composable
fun ProgressOverlay(
    data: Progress.Data?,
    modifier: Modifier = Modifier,
    extraSlot: (@Composable (extra: Any, modifier: Modifier) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Fade the content out as the overlay fades in (over the same window) instead of snapping it to
    // alpha 0, so there's no hard flicker when the overlay appears/disappears.
    val contentAlpha by animateFloatAsState(
        targetValue = if (data != null) 0f else 1f,
        animationSpec = tween(ENTRANCE_DURATION_MS),
        label = "progressContentAlpha",
    )
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha),
        ) {
            content()
        }
        AnimatedVisibility(
            visible = data != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(ENTRANCE_DURATION_MS, easing = EaseOutCubic)) +
                scaleIn(animationSpec = tween(ENTRANCE_DURATION_MS, easing = EaseOutCubic), initialScale = 0.94f),
            // No exit animation (matches legacy View.GONE): an exit transition keeps the panel — and
            // its pointer-swallowing input — composed for ~180ms after data becomes null, blocking
            // taps on the now-revealed content and briefly rendering empty Progress.Data().
            exit = ExitTransition.None,
        ) {
            val current = data ?: Progress.Data()
            ProgressOverlayPanel(data = current, extraSlot = extraSlot)
        }
    }
}

@Composable
private fun ProgressOverlayPanel(
    data: Progress.Data,
    modifier: Modifier = Modifier,
    extraSlot: (@Composable (extra: Any, modifier: Modifier) -> Unit)? = null,
) {
    val context = LocalContext.current
    val primary = data.primary.get(context)
    val secondary = data.secondary.get(context)

    val count = data.count
    val subCount = data.subCount
    val showRing = count !is Progress.Count.None
    // Only Counter/Percent with a known total drive a determinate arc + an inner number.
    // Indeterminate / Size / unknown-total fall back to a spinning ring with no number (legacy behavior).
    val fraction: Float? = count.determinateFraction()
    val hero = heroCount(count, subCount)
    val countText = if (hero.determinateFraction() != null) hero.displayValue(context) else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val ringSize = minOf(maxWidth, maxHeight).coerceIn(RING_MIN, RING_MAX)

            if (showRing) {
                ProgressRing(size = ringSize, progress = fraction)
            }
            if (subCount != null && subCount !is Progress.Count.None) {
                // A second CircularProgressIndicator publishes a second progress semantics node,
                // so TalkBack would announce two indistinguishable progress bars. The outer ring
                // stays the announced one.
                ProgressRing(
                    size = ringSize * RING_SUB_SIZE_FACTOR,
                    progress = subCount.determinateFraction(),
                    strokeFactor = RING_SUB_STROKE_FACTOR,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            Column(
                modifier = Modifier.widthIn(max = ringSize * RING_INNER_WIDTH_FACTOR),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!countText.isNullOrEmpty()) {
                    // Reserve the taller of the two hero styles. The style keys off the hero count,
                    // which flips (e.g. Indeterminate → Percent) mid-run the moment per-item progress
                    // becomes available — and because this column is vertically centered, a height
                    // change re-centers it and visibly jumps.
                    val heroLineHeight = MaterialTheme.typography.displayMedium.lineHeight
                    val heroHeight = with(LocalDensity.current) {
                        if (heroLineHeight.isSp) heroLineHeight.toDp() else HERO_HEIGHT_FALLBACK
                    }
                    Box(
                        modifier = Modifier.height(heroHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = countText,
                            // Percent is short → make it the dramatic focal point. Counters ("147/2000") can be
                            // long, so they get a smaller hero style that still fits within the inner circle.
                            style = if (hero is Progress.Count.Percent) {
                                MaterialTheme.typography.displayMedium
                            } else {
                                MaterialTheme.typography.headlineMedium
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Reserve fixed line counts (minLines == maxLines) and render both slots unconditionally so
                // the inner block stays a constant height. Otherwise a secondary path going 1↔2 lines — or a
                // message toggling empty↔present — changes the column height, and because the column is
                // vertically centered it re-centers and visibly jumps on every progress tick. Empty reserved
                // lines are invisible (no glyphs), so this costs nothing when a message is short or absent.
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (countText.isNullOrEmpty()) 0.dp else 12.dp),
                )
                // Height reservation and content are separated on purpose. The reservation is a glyph-free
                // two-line Text in the same style, so the block's height is constant (no re-centering jump,
                // same rationale as the note above) and exact at any density or fontScale without a
                // hand-computed dp value. The real row is centered inside it, so the icon lines up with the
                // label whether the label renders on one line or wraps to two.
                Box(
                    modifier = Modifier.padding(top = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.bodySmall,
                        minLines = 2,
                        maxLines = 2,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val extra = data.extra
                        if (extraSlot != null && extra != null) {
                            extraSlot(
                                extra,
                                Modifier
                                    .padding(end = 6.dp)
                                    .size(PROGRESS_EXTRA_ICON_SIZE),
                            )
                        }
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(
    size: Dp,
    progress: Float?,
    modifier: Modifier = Modifier,
    strokeFactor: Float = RING_STROKE_FACTOR,
) {
    val stroke = (size.value * strokeFactor).dp
    if (progress != null) {
        // Sweep the arc smoothly instead of snapping when the fraction jumps.
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 320, easing = EaseOutCubic),
            label = "progressArc",
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = modifier.size(size),
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
        )
    }
}

@Preview2
@Composable
private fun ProgressOverlayIndeterminatePreview() {
    PreviewWrapper {
        ProgressOverlay(
            data = Progress.Data(),
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}

@Preview2
@Composable
private fun ProgressOverlayPercentPreview() {
    PreviewWrapper {
        ProgressOverlay(
            data = Progress.Data(
                primary = "Scanning files".toCaString(),
                secondary = "/storage/emulated/0/Android/data".toCaString(),
                count = Progress.Count.Percent(42, 100),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}

@Preview2
@Composable
private fun ProgressOverlayCounterPreview() {
    PreviewWrapper {
        ProgressOverlay(
            data = Progress.Data(
                primary = "Processing apps".toCaString(),
                secondary = "Calculating cache sizes".toCaString(),
                count = Progress.Count.Counter(147, 2000),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}

private val previewExtraSlot: @Composable (extra: Any, modifier: Modifier) -> Unit = { _, modifier ->
    Icon(
        imageVector = Icons.TwoTone.Android,
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun ProgressOverlayExtraSample(appName: String) {
    PreviewWrapper {
        ProgressOverlay(
            data = Progress.Data(
                primary = "Scanning apps".toCaString(),
                secondary = appName.toCaString(),
                count = Progress.Count.Percent(42, 100),
                extra = "preview-payload",
            ),
            modifier = Modifier.fillMaxSize(),
            extraSlot = previewExtraSlot,
        ) {}
    }
}

@Preview2
@Composable
private fun ProgressOverlayExtraPreview() {
    ProgressOverlayExtraSample(appName = "SD Maid")
}

@Preview2
@Composable
private fun ProgressOverlayExtraLongLabelPreview() {
    ProgressOverlayExtraSample(
        appName = "Some Extremely Long Application Name That Keeps Going And Going Until It Ellipsizes",
    )
}

@Preview(showBackground = true, fontScale = 0.85f)
@Composable
private fun ProgressOverlayExtraSmallFontPreview() {
    ProgressOverlayExtraSample(appName = "SD Maid")
}

@Preview(showBackground = true, fontScale = 1.3f)
@Composable
private fun ProgressOverlayExtraLargeFontPreview() {
    ProgressOverlayExtraSample(appName = "SD Maid")
}

@Preview(showBackground = true, heightDp = 200)
@Composable
private fun ProgressOverlayIdlePreview() {
    PreviewWrapper {
        ProgressOverlay(
            data = null,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(text = "Content is visible when data == null")
        }
    }
}
