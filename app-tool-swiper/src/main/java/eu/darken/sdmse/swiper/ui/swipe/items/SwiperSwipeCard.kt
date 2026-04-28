package eu.darken.sdmse.swiper.ui.swipe.items

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.OpenInNew
import androidx.compose.material.icons.twotone.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.swipe.SwipeOutcome
import eu.darken.sdmse.swiper.ui.swipe.decideSwipe
import kotlin.random.Random
import kotlinx.coroutines.launch

private data class StampSeeds(val keep: Int, val delete: Int, val skip: Int, val undo: Int)

@Composable
internal fun SwiperSwipeCard(
    item: SwipeItem,
    canUndo: Boolean,
    swapDirections: Boolean,
    showDetails: Boolean,
    sessionPosition: Int,
    totalItems: Int,
    onSwipeKeep: () -> Unit,
    onSwipeDelete: () -> Unit,
    onSwipeSkip: () -> Unit,
    onSwipeUndo: () -> Unit,
    onPreviewClick: () -> Unit,
    onOpenExternallyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var isCommitting by remember { mutableStateOf(false) }
    val animationScope = rememberCoroutineScope()
    val stampSeeds = remember(item.id) {
        StampSeeds(
            keep = Random.nextInt(0, 4),
            delete = Random.nextInt(0, 4),
            skip = Random.nextInt(0, 4),
            undo = Random.nextInt(0, 4),
        )
    }

    LaunchedEffect(item.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        isCommitting = false
    }

    val width = cardSize.width.toFloat().coerceAtLeast(1f)
    val height = cardSize.height.toFloat().coerceAtLeast(1f)
    val swipeThreshold = width * 0.4f
    val rotation = (offsetX.value / width.coerceAtLeast(1f)) * 15f

    val rightProgress = (offsetX.value / swipeThreshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val leftProgress = (-offsetX.value / swipeThreshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val skipProgress = (-offsetY.value / swipeThreshold.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val undoProgress =
        if (canUndo) (offsetY.value / swipeThreshold.coerceAtLeast(1f)).coerceIn(0f, 1f) else 0f

    val keepProgress = if (swapDirections) leftProgress else rightProgress
    val deleteProgress = if (swapDirections) rightProgress else leftProgress

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onSizeChanged { cardSize = it }
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = rotation
            }
            .pointerInput(item.id, canUndo, swapDirections) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (isCommitting) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.first()
                        if (!change.pressed) break
                        val drag = change.positionChange()
                        if (drag.x != 0f || drag.y != 0f) {
                            tracker.addPointerInputChange(change)
                            val targetX = offsetX.value + drag.x
                            val targetY = offsetY.value + drag.y
                            // Hop to the regular coroutine scope to call Animatable.snapTo;
                            // awaitEachGesture's RestrictsSuspension scope can't invoke it directly.
                            animationScope.launch {
                                offsetX.snapTo(targetX)
                                offsetY.snapTo(targetY)
                            }
                            change.consume()
                        }
                    }

                    val velocity = tracker.calculateVelocity()
                    val outcome = decideSwipe(
                        offsetX = offsetX.value,
                        offsetY = offsetY.value,
                        velocityX = velocity.x,
                        velocityY = velocity.y,
                        threshold = swipeThreshold,
                        canUndo = canUndo,
                        swapDirections = swapDirections,
                    )
                    isCommitting = (outcome != SwipeOutcome.SnapBack)
                    val w = width
                    val h = height
                    animationScope.launch {
                        when (outcome) {
                            SwipeOutcome.SnapBack -> {
                                offsetX.animateTo(0f, spring())
                                offsetY.animateTo(0f, spring())
                            }
                            SwipeOutcome.Keep -> {
                                offsetX.animateTo(w * 1.5f, tween(300))
                                onSwipeKeep()
                            }
                            SwipeOutcome.Delete -> {
                                offsetX.animateTo(-w * 1.5f, tween(300))
                                onSwipeDelete()
                            }
                            SwipeOutcome.Skip -> {
                                offsetY.animateTo(-h * 1.5f, tween(300))
                                onSwipeSkip()
                            }
                            SwipeOutcome.Undo -> {
                                offsetY.animateTo(h * 1.5f, tween(300))
                                onSwipeUndo()
                            }
                        }
                    }
                }
            },
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FilePreviewImage(
                    lookup = item.lookup,
                    contentScale = ContentScale.Fit,
                    contentDescription = item.lookup.name,
                    modifier = Modifier.fillMaxSize(),
                )

                CardActionRow(
                    onPreviewClick = onPreviewClick,
                    onOpenExternallyClick = onOpenExternallyClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )

                if (showDetails) {
                    FileInfoOverlay(
                        item = item,
                        sessionPosition = sessionPosition,
                        totalItems = totalItems,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }

                FileTypeChip(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                )

                // Existing decision indicator (faded stamp at 0.3 alpha)
                val existing = when (item.decision) {
                    SwipeDecision.KEEP -> Pair(StampDirection.KEEP_RIGHT, 0.3f).takeIf { !swapDirections }
                        ?: Pair(StampDirection.KEEP_LEFT, 0.3f)
                    SwipeDecision.DELETE -> Pair(StampDirection.DELETE_LEFT, 0.3f).takeIf { !swapDirections }
                        ?: Pair(StampDirection.DELETE_RIGHT, 0.3f)
                    else -> null
                }

                // Live drag stamps
                val keepDirection = if (swapDirections) StampDirection.KEEP_LEFT else StampDirection.KEEP_RIGHT
                val deleteDirection = if (swapDirections) StampDirection.DELETE_RIGHT else StampDirection.DELETE_LEFT

                Stamp(
                    direction = keepDirection,
                    text = stringResource(stampStringId(StampKind.KEEP, stampSeeds.keep)),
                    progress = keepProgress.coerceAtLeast(if (existing?.first == keepDirection) existing.second else 0f),
                )
                Stamp(
                    direction = deleteDirection,
                    text = stringResource(stampStringId(StampKind.DELETE, stampSeeds.delete)),
                    progress = deleteProgress.coerceAtLeast(if (existing?.first == deleteDirection) existing.second else 0f),
                )
                Stamp(
                    direction = StampDirection.SKIP_BOTTOM,
                    text = stringResource(stampStringId(StampKind.SKIP, stampSeeds.skip)),
                    progress = skipProgress,
                )
                Stamp(
                    direction = StampDirection.UNDO_TOP,
                    text = stringResource(stampStringId(StampKind.UNDO, stampSeeds.undo)),
                    progress = undoProgress,
                )
            }
        }
    }
}

@Composable
private fun CardActionRow(
    onPreviewClick: () -> Unit,
    onOpenExternallyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        FilledTonalIconButton(
            onClick = onPreviewClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            ),
        ) {
            Icon(
                imageVector = Icons.TwoTone.ZoomOutMap,
                contentDescription = stringResource(CommonR.string.general_view_action),
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        FilledTonalIconButton(
            onClick = onOpenExternallyClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.TwoTone.OpenInNew,
                contentDescription = stringResource(R.string.swiper_open_externally_action),
            )
        }
    }
}

@Composable
private fun FileInfoOverlay(
    item: SwipeItem,
    sessionPosition: Int,
    totalItems: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text(
                text = item.lookup.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.lookup.userReadablePath.get(context),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(
                    text = Formatter.formatFileSize(context, item.lookup.size),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (totalItems > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.swiper_item_position, sessionPosition, totalItems),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileTypeChip(
    item: SwipeItem,
    modifier: Modifier = Modifier,
) {
    val ext = item.lookup.name.substringAfterLast('.', "").lowercase()
    val display = if (ext.isEmpty()) "FILE" else ext.take(6).uppercase()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = display,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private enum class StampDirection { KEEP_RIGHT, KEEP_LEFT, DELETE_LEFT, DELETE_RIGHT, SKIP_BOTTOM, UNDO_TOP }

private enum class StampKind { KEEP, DELETE, SKIP, UNDO }

private fun stampStringId(kind: StampKind, seed: Int): Int {
    val index = seed.coerceIn(0, 3)
    return when (kind) {
        StampKind.KEEP -> when (index) {
            0 -> R.string.swiper_stamp_keep_1
            1 -> R.string.swiper_stamp_keep_2
            2 -> R.string.swiper_stamp_keep_3
            else -> R.string.swiper_stamp_keep_4
        }
        StampKind.DELETE -> when (index) {
            0 -> R.string.swiper_stamp_delete_1
            1 -> R.string.swiper_stamp_delete_2
            2 -> R.string.swiper_stamp_delete_3
            else -> R.string.swiper_stamp_delete_4
        }
        StampKind.SKIP -> when (index) {
            0 -> R.string.swiper_stamp_skip_1
            1 -> R.string.swiper_stamp_skip_2
            2 -> R.string.swiper_stamp_skip_3
            else -> R.string.swiper_stamp_skip_4
        }
        StampKind.UNDO -> when (index) {
            0 -> R.string.swiper_stamp_undo_1
            1 -> R.string.swiper_stamp_undo_2
            2 -> R.string.swiper_stamp_undo_3
            else -> R.string.swiper_stamp_undo_4
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Stamp(
    direction: StampDirection,
    text: String,
    progress: Float,
) {
    if (progress <= 0f) return
    val alpha = progress.coerceIn(0f, 1f)
    val scale = 0.5f + alpha * 0.5f
    val color = when (direction) {
        StampDirection.KEEP_RIGHT, StampDirection.KEEP_LEFT -> MaterialTheme.colorScheme.primary
        StampDirection.DELETE_LEFT, StampDirection.DELETE_RIGHT -> MaterialTheme.colorScheme.error
        StampDirection.SKIP_BOTTOM, StampDirection.UNDO_TOP -> MaterialTheme.colorScheme.tertiary
    }
    val rotationDeg = when (direction) {
        StampDirection.KEEP_RIGHT, StampDirection.DELETE_RIGHT -> -15f
        StampDirection.KEEP_LEFT, StampDirection.DELETE_LEFT -> 15f
        StampDirection.SKIP_BOTTOM, StampDirection.UNDO_TOP -> 0f
    }
    val align = when (direction) {
        StampDirection.KEEP_RIGHT, StampDirection.DELETE_RIGHT -> Alignment.TopEnd
        StampDirection.KEEP_LEFT, StampDirection.DELETE_LEFT -> Alignment.TopStart
        StampDirection.SKIP_BOTTOM -> Alignment.BottomCenter
        StampDirection.UNDO_TOP -> Alignment.TopCenter
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(4.dp, color),
        contentColor = color,
        modifier = Modifier
            .align(align)
            .padding(24.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                rotationZ = rotationDeg
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

