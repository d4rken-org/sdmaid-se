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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress

private const val ENTRANCE_DURATION_MS = 180

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
    val countText = data.count.displayValue(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            }
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (val count = data.count) {
            is Progress.Count.None -> {
                // No indicator — text-only progress state.
            }

            is Progress.Count.Indeterminate,
            is Progress.Count.Size -> IndeterminateIndicator()

            is Progress.Count.Counter -> DeterminateIndicator(
                currentValue = count.current,
                maxValue = count.max,
                text = countText,
            )

            is Progress.Count.Percent -> DeterminateIndicator(
                currentValue = count.current,
                maxValue = count.max,
                text = countText,
            )
        }
        if (primary.isNotEmpty()) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun IndeterminateIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(56.dp),
    )
}

@Composable
private fun DeterminateIndicator(
    currentValue: Long,
    maxValue: Long,
    text: String?,
) {
    // Only indeterminate when the total is unknown; current==0 with a known max is a valid 0% start.
    val useIndeterminate = maxValue == 0L
    Box(modifier = Modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        if (useIndeterminate) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp))
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                progress = { (currentValue.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) },
                trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
            )
        }
        if (!text.isNullOrEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
            data = Progress.Data(count = Progress.Count.Percent(42, 100)),
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
