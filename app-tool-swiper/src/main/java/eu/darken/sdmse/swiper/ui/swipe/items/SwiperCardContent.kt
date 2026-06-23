package eu.darken.sdmse.swiper.ui.swipe.items

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.guidedTourTarget
import eu.darken.sdmse.swiper.R
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import eu.darken.sdmse.swiper.ui.swipe.tour.SwiperSwipeTour
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

internal data class StampSeeds(val keep: Int, val delete: Int, val skip: Int, val undo: Int)

/**
 * Stamp wording variants derived deterministically from the item id, so the same item shows the same
 * stamp while dragging (interactive card) and while flying off (leaving overlay) — two separate
 * composables that would otherwise roll independent random text.
 */
internal fun stampSeedsFor(itemId: Long): StampSeeds {
    val base = abs(itemId)
    return StampSeeds(
        keep = (base % 4).toInt(),
        delete = ((base / 4) % 4).toInt(),
        skip = ((base / 16) % 4).toInt(),
        undo = ((base / 64) % 4).toInt(),
    )
}

/** Fraction of the card width a drag must cross (or a stamp to fully show) to count as a swipe. */
internal const val SWIPE_THRESHOLD_RATIO = 0.4f

/** Max card tilt (degrees) at full horizontal displacement. */
internal const val SWIPE_CARD_ROTATION_DEG = 15f

/**
 * The visual body of a swipe card, shared by [SwiperDeckCard] (interactive top + static peek) and the
 * non-interactive [SwiperLeavingCard] overlay. The caller drives translation/rotation via its own
 * `graphicsLayer`; this composable only renders the card surface plus the swipe stamps whose opacity
 * is derived from [offsetX]/[offsetY].
 *
 * When [interactive] is false (the leaving overlay) the chrome buttons, their click handlers, and
 * guided-tour targets are omitted entirely so the card never participates in pointer hit-testing.
 */
@Composable
internal fun SwiperCardContent(
    modifier: Modifier = Modifier,
    item: SwipeItem,
    offsetX: Float,
    offsetY: Float,
    cardWidth: Float,
    swapDirections: Boolean,
    showDetails: Boolean,
    sessionPosition: Int,
    totalItems: Int,
    chromeAlpha: Float,
    canUndo: Boolean,
    interactive: Boolean,
    stampSeeds: StampSeeds,
    onPreviewClick: () -> Unit = {},
    onOpenExternallyClick: () -> Unit = {},
) {
    val swipeThreshold = (cardWidth * SWIPE_THRESHOLD_RATIO).coerceAtLeast(1f)
    val rightProgress = (offsetX / swipeThreshold).coerceIn(0f, 1f)
    val leftProgress = (-offsetX / swipeThreshold).coerceIn(0f, 1f)
    val skipProgress = (-offsetY / swipeThreshold).coerceIn(0f, 1f)
    val undoProgress = if (canUndo) (offsetY / swipeThreshold).coerceIn(0f, 1f) else 0f

    val keepProgress = if (swapDirections) leftProgress else rightProgress
    val deleteProgress = if (swapDirections) rightProgress else leftProgress

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FilePreviewImage(
                lookup = item.lookup,
                contentScale = ContentScale.Fit,
                contentDescription = item.lookup.name,
                // The next card is preloaded as the back card, so its bitmap is memory-cached by the
                // time it's promoted — keep the loading slot transparent so it doesn't flash the
                // tonal placeholder for a frame before the cached image resolves.
                placeholderWhileLoading = false,
                modifier = Modifier.fillMaxSize(),
            )

            if (interactive) {
                FilledTonalIconButton(
                    onClick = onOpenExternallyClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .graphicsLayer { alpha = chromeAlpha }
                        .guidedTourTarget(SwiperSwipeTour.OPEN_IN_TARGET),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.OpenInNew,
                        contentDescription = stringResource(R.string.swiper_open_externally_action),
                    )
                }
                FilledTonalIconButton(
                    onClick = onPreviewClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .graphicsLayer { alpha = chromeAlpha }
                        .guidedTourTarget(SwiperSwipeTour.FULLSCREEN_PREVIEW_TARGET),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.ZoomOutMap,
                        contentDescription = stringResource(CommonR.string.general_view_action),
                    )
                }
            }

            SwiperFileTypeChip(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )

            if (showDetails) {
                FileInfoOverlay(
                    item = item,
                    sessionPosition = sessionPosition,
                    totalItems = totalItems,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha },
                )
            }

            // Stamps sit on the trailing edge of the swipe direction so they stay visible
            // as the card moves off-screen (e.g. swiping right → stamp on the left).
            val keepDirection = if (swapDirections) StampDirection.KEEP_RIGHT else StampDirection.KEEP_LEFT
            val deleteDirection = if (swapDirections) StampDirection.DELETE_LEFT else StampDirection.DELETE_RIGHT

            // Existing decision indicator (faded stamp at 0.3 alpha, fades in with chrome)
            val existing = when (item.decision) {
                SwipeDecision.KEEP -> keepDirection to 0.3f
                SwipeDecision.DELETE -> deleteDirection to 0.3f
                else -> null
            }

            val existingKeep = if (existing?.first == keepDirection) existing.second * chromeAlpha else 0f
            val existingDelete = if (existing?.first == deleteDirection) existing.second * chromeAlpha else 0f

            Stamp(
                direction = keepDirection,
                text = stringResource(stampStringId(StampKind.KEEP, stampSeeds.keep)),
                progress = keepProgress.coerceAtLeast(existingKeep),
            )
            Stamp(
                direction = deleteDirection,
                text = stringResource(stampStringId(StampKind.DELETE, stampSeeds.delete)),
                progress = deleteProgress.coerceAtLeast(existingDelete),
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

@Preview2
@Composable
private fun SwiperCardContentPreview() {
    PreviewWrapper {
        SwiperCardContent(
            item = previewSwipeItem(),
            offsetX = 0f,
            offsetY = 0f,
            cardWidth = 400f,
            swapDirections = false,
            showDetails = true,
            sessionPosition = 1,
            totalItems = 10,
            chromeAlpha = 1f,
            canUndo = true,
            interactive = true,
            stampSeeds = stampSeedsFor(1L),
        )
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
    val fullPath = item.lookup.userReadablePath.get(context)
    val pathOnly = fullPath.removeSuffix(item.lookup.name)
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }
    val date = remember(item.id) {
        item.lookup.modifiedAt.atZone(ZoneId.systemDefault()).format(dateFormatter)
    }
    val size = Formatter.formatFileSize(context, item.lookup.size)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.lookup.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (totalItems > 0) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.swiper_item_position, sessionPosition, totalItems),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = pathOnly,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$size • $date",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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
private fun BoxScope.Stamp(
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
    // Keep stamps clear of the surrounding chrome: the round corner buttons sit at the top
    // (≈48dp tall incl. padding) and the file-info overlay occupies the bottom (~90dp).
    val padding = when (direction) {
        StampDirection.KEEP_RIGHT, StampDirection.DELETE_RIGHT,
        StampDirection.KEEP_LEFT, StampDirection.DELETE_LEFT,
        StampDirection.UNDO_TOP,
            -> PaddingValues(start = 24.dp, top = 72.dp, end = 24.dp, bottom = 24.dp)
        StampDirection.SKIP_BOTTOM -> PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 100.dp)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(4.dp, color),
        contentColor = color,
        modifier = Modifier
            .align(align)
            .padding(padding)
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
