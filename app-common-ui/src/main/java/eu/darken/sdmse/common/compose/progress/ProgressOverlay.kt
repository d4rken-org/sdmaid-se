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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
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

private const val ENTRANCE_DURATION_MS = 180

// "Bold hero" ring: large, thick, rounded. The %/count and both progress messages live INSIDE the ring.
private val RING_MIN = 240.dp
private val RING_MAX = 300.dp
private const val RING_STROKE_FACTOR = 0.032f // stroke width as a fraction of the ring diameter (~9.6dp @ 300dp)
private const val RING_INNER_WIDTH_FACTOR = 0.64f // text column max width as a fraction of the ring diameter

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
            ProgressOverlayPanel(data = current)
        }
    }
}

@Composable
private fun ProgressOverlayPanel(
    data: Progress.Data,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val primary = data.primary.get(context)
    val secondary = data.secondary.get(context)

    val count = data.count
    val showRing = count !is Progress.Count.None
    // Only Counter/Percent with a known total drive a determinate arc + an inner number.
    // Indeterminate / Size / unknown-total fall back to a spinning ring with no number (legacy behavior).
    val fraction: Float? = when (count) {
        is Progress.Count.Counter,
        is Progress.Count.Percent,
        -> if (count.max > 0L) (count.current.toFloat() / count.max.toFloat()).coerceIn(0f, 1f) else null

        else -> null
    }
    val countText = if (fraction != null) count.displayValue(context) else null

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

            Column(
                modifier = Modifier.widthIn(max = ringSize * RING_INNER_WIDTH_FACTOR),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!countText.isNullOrEmpty()) {
                    Text(
                        text = countText,
                        // Percent is short → make it the dramatic focal point. Counters ("147/2000") can be
                        // long, so they get a smaller hero style that still fits within the inner circle.
                        style = if (count is Progress.Count.Percent) {
                            MaterialTheme.typography.displayMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (primary.isNotEmpty()) {
                    Text(
                        text = primary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (countText.isNullOrEmpty()) 0.dp else 12.dp),
                    )
                }
                if (secondary.isNotEmpty()) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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
) {
    val stroke = (size.value * RING_STROKE_FACTOR).dp
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
