package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import eu.darken.sdmse.swiper.ui.swipe.SwipeOutcome
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val EXIT_DURATION_MS = 300

/**
 * A committed card that is flying off-screen, rendered on top of the live deck. It is purely visual:
 * it carries no pointer input, click handlers, or guided-tour targets (see [interactive] = false in
 * [SwiperCardContent]), so the freshly promoted card beneath it is immediately interactive.
 *
 * Seeded from the gesture's release offset so the fly-off continues smoothly from where the finger
 * lifted; for action-bar button commits the release offset is zero and the direction falls back to
 * the action's side of the deck.
 */
internal data class LeavingCard(
    val key: Int,
    val item: SwipeItem,
    val outcome: SwipeOutcome,
    val releaseX: Float,
    val releaseY: Float,
    val swapDirections: Boolean,
    val showDetails: Boolean,
    val totalItems: Int,
)

@Composable
internal fun SwiperLeavingCard(
    modifier: Modifier = Modifier,
    card: LeavingCard,
    onExitDone: () -> Unit,
) {
    val offsetX = remember(card.key) { Animatable(card.releaseX) }
    val offsetY = remember(card.key) { Animatable(card.releaseY) }
    var cardSize by remember(card.key) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(card.key, cardSize) {
        if (cardSize == IntSize.Zero) return@LaunchedEffect
        val w = cardSize.width.toFloat()
        val h = cardSize.height.toFloat()
        coroutineScope {
            when (card.outcome) {
                SwipeOutcome.Skip -> {
                    launch { offsetX.animateTo(0f, tween(EXIT_DURATION_MS)) }
                    offsetY.animateTo(-h * 1.5f, tween(EXIT_DURATION_MS))
                }
                else -> {
                    // Keep / Delete fly horizontally. Follow the finger when available, else the
                    // side of the deck the action lives on (button commits have a zero release).
                    val flyRight = when {
                        card.releaseX != 0f -> card.releaseX > 0f
                        card.outcome == SwipeOutcome.Keep -> !card.swapDirections
                        else -> card.swapDirections
                    }
                    launch { offsetY.animateTo(0f, tween(EXIT_DURATION_MS)) }
                    offsetX.animateTo(if (flyRight) w * 1.5f else -w * 1.5f, tween(EXIT_DURATION_MS))
                }
            }
        }
        onExitDone()
    }

    val width = cardSize.width.toFloat().coerceAtLeast(1f)
    val rotation = (offsetX.value / width) * SWIPE_CARD_ROTATION_DEG
    val stampSeeds = remember(card.item.id) { stampSeedsFor(card.item.id) }

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onSizeChanged { cardSize = it }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation
            },
    ) {
        SwiperCardContent(
            item = card.item,
            offsetX = offsetX.value,
            offsetY = offsetY.value,
            cardWidth = width,
            swapDirections = card.swapDirections,
            showDetails = card.showDetails,
            sessionPosition = card.item.itemIndex + 1,
            totalItems = card.totalItems,
            chromeAlpha = 1f,
            canUndo = false,
            interactive = false,
            stampSeeds = stampSeeds,
        )
    }
}

@Preview2
@Composable
private fun SwiperLeavingCardPreview() {
    PreviewWrapper {
        SwiperLeavingCard(
            card = LeavingCard(
                key = 0,
                item = previewSwipeItem(),
                outcome = SwipeOutcome.Delete,
                releaseX = 0f,
                releaseY = 0f,
                swapDirections = false,
                showDetails = true,
                totalItems = 10,
            ),
            onExitDone = {},
        )
    }
}
