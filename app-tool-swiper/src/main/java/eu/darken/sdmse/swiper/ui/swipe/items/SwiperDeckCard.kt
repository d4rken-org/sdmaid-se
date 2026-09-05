package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import eu.darken.sdmse.swiper.ui.swipe.SwipeOutcome
import eu.darken.sdmse.swiper.ui.swipe.decideSwipe
import kotlinx.coroutines.launch

/**
 * One card in the swipe deck. Both the interactive top card and the static "peek" behind it are the
 * same composable, rendered in a single `key(item.id)` loop in the screen — so when the cursor
 * advances, Compose *moves* the next card's node (with its already-loaded Coil image) into the top
 * slot instead of recreating it. That preservation is what stops the preview from re-requesting (and
 * blinking) as the next item becomes current.
 *
 * [isTop] toggles the gesture, chrome, file-info overlay, and drag transform; the shared
 * [SwiperCardContent] (and its `FilePreviewImage`) stays at the same slot across the change so the
 * loaded bitmap survives the promotion. The outgoing card's fly-off is handled by [SwiperLeavingCard].
 */
@Composable
internal fun SwiperDeckCard(
    modifier: Modifier = Modifier,
    item: SwipeItem,
    isTop: Boolean,
    canUndo: Boolean,
    swapDirections: Boolean,
    showDetails: Boolean,
    sessionPosition: Int,
    totalItems: Int,
    onCommit: (outcome: SwipeOutcome, releaseX: Float, releaseY: Float) -> Unit,
    onPreviewClick: () -> Unit,
    onOpenExternallyClick: () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val chromeAlpha = remember { Animatable(0f) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var isCommitting by remember { mutableStateOf(false) }
    val animationScope = rememberCoroutineScope()
    val stampSeeds = remember(item.id) { stampSeedsFor(item.id) }

    LaunchedEffect(item.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        isCommitting = false
        chromeAlpha.snapTo(0f)
        chromeAlpha.animateTo(1f, tween(durationMillis = 300))
    }

    val width = cardSize.width.toFloat().coerceAtLeast(1f)
    val swipeThreshold = width * SWIPE_THRESHOLD_RATIO
    val rotation = (offsetX.value / width) * SWIPE_CARD_ROTATION_DEG

    val gestureModifier = if (isTop) {
        Modifier.pointerInput(item.id, canUndo, swapDirections) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (isCommitting) return@awaitEachGesture
                val tracker = VelocityTracker()
                tracker.addPointerInputChange(down)

                // Gesture-local source of truth. Animatable.snapTo is suspend and runs on the
                // animation scope, so reading offsetX.value back mid-gesture can miss deltas that
                // are still queued; this accumulates them synchronously instead. It is seeded in
                // the slop callback rather than here: a press landing on a running snap-back must
                // start from where the card has actually travelled to by the time the drag is
                // claimed, not from where it sat when the finger went down.
                var dragOffset = Offset.Zero

                // Below touch slop this consumes nothing, so a tap still reaches the clickable
                // corner buttons inside the card. It also cancels if another recognizer claims the
                // pointer and follows a pointer handoff when the tracked finger lifts.
                val slopChange = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                    change.consume()
                    tracker.addPointerInputChange(change)
                    dragOffset = Offset(offsetX.value, offsetY.value) + overSlop
                    val target = dragOffset
                    animationScope.launch {
                        offsetX.snapTo(target.x)
                        offsetY.snapTo(target.y)
                    }
                } ?: return@awaitEachGesture

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == slopChange.id }
                        ?: event.changes.first()
                    if (!change.pressed) break
                    val drag = change.positionChange()
                    if (drag.x != 0f || drag.y != 0f) {
                        tracker.addPointerInputChange(change)
                        dragOffset += drag
                        val target = dragOffset
                        // Hop to the regular coroutine scope to call Animatable.snapTo;
                        // awaitEachGesture's RestrictsSuspension scope can't invoke it directly.
                        animationScope.launch {
                            offsetX.snapTo(target.x)
                            offsetY.snapTo(target.y)
                        }
                        change.consume()
                    }
                }

                val velocity = tracker.calculateVelocity()
                val outcome = decideSwipe(
                    offsetX = dragOffset.x,
                    offsetY = dragOffset.y,
                    velocityX = velocity.x,
                    velocityY = velocity.y,
                    threshold = swipeThreshold,
                    canUndo = canUndo,
                    swapDirections = swapDirections,
                )
                when (outcome) {
                    SwipeOutcome.SnapBack -> animationScope.launch {
                        offsetX.animateTo(0f, spring())
                        offsetY.animateTo(0f, spring())
                    }
                    SwipeOutcome.Undo -> {
                        // Undo navigates backwards (no fly-off / leaving overlay): settle back
                        // and let the restored previous card rebind into the deck.
                        animationScope.launch {
                            offsetX.animateTo(0f, spring())
                            offsetY.animateTo(0f, spring())
                        }
                        onCommit(outcome, dragOffset.x, dragOffset.y)
                    }
                    else -> {
                        // Keep / Delete / Skip: hand the fly-off to a leaving overlay; this card is
                        // about to be dropped from the deck as the cursor advances.
                        isCommitting = true
                        onCommit(outcome, dragOffset.x, dragOffset.y)
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onSizeChanged { cardSize = it }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation
            }
            .then(gestureModifier),
    ) {
        // While this card is committing, its leaving overlay draws the fly-off; render nothing here so
        // the two don't briefly overlap before the cursor advances and drops this card.
        if (!(isTop && isCommitting)) {
            SwiperCardContent(
                item = item,
                offsetX = if (isTop) offsetX.value else 0f,
                offsetY = if (isTop) offsetY.value else 0f,
                cardWidth = width,
                swapDirections = swapDirections,
                showDetails = isTop && showDetails,
                sessionPosition = sessionPosition,
                totalItems = totalItems,
                chromeAlpha = if (isTop) chromeAlpha.value else 1f,
                canUndo = canUndo,
                interactive = isTop,
                stampSeeds = stampSeeds,
                onPreviewClick = onPreviewClick,
                onOpenExternallyClick = onOpenExternallyClick,
            )
        }
    }
}

@Preview2
@Composable
private fun SwiperDeckCardTopPreview() {
    PreviewWrapper {
        SwiperDeckCard(
            item = previewSwipeItem(),
            isTop = true,
            canUndo = true,
            swapDirections = false,
            showDetails = true,
            sessionPosition = 3,
            totalItems = 12,
            onCommit = { _, _, _ -> },
            onPreviewClick = {},
            onOpenExternallyClick = {},
            modifier = Modifier,
        )
    }
}
