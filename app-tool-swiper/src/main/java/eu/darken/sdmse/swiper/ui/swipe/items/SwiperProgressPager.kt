package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem
import kotlin.math.abs

private val ITEM_SIZE = 64.dp

@Composable
internal fun SwiperProgressPager(
    items: List<SwipeItem>,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val density = LocalDensity.current
    val itemSizePx = with(density) { ITEM_SIZE.toPx() }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in items.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    BoxWithConstraints(modifier = modifier
        .fillMaxWidth()
        .height(ITEM_SIZE + 16.dp)) {
        val sidePadding = ((maxWidth - ITEM_SIZE) / 2).coerceAtLeast(0.dp)
        val centerOffset by remember(itemSizePx) {
            derivedStateOf {
                listState.firstVisibleItemIndex +
                    (listState.firstVisibleItemScrollOffset.toFloat() / itemSizePx)
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sidePadding, vertical = 8.dp),
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val distance = abs(index - centerOffset).coerceIn(0f, 2f)
                val proximity = (1f - distance).coerceIn(0f, 1f)
                val scale = 0.85f + proximity * 0.15f
                val alpha = 0.75f + proximity * 0.25f
                ProgressThumb(
                    item = item,
                    position = index + 1,
                    isCurrent = index == currentIndex,
                    onClick = { onItemClick(index) },
                    modifier = Modifier
                        .size(ITEM_SIZE)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                )
            }
        }
    }
}

@Composable
private fun ProgressThumb(
    item: SwipeItem,
    position: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val border =
        if (isCurrent) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            FilePreviewImage(
                lookup = item.lookup,
                contentScale = ContentScale.Crop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "#$position",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
            when (item.decision) {
                SwipeDecision.KEEP -> DecisionDot(
                    icon = Icons.TwoTone.Favorite,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(20.dp),
                )
                SwipeDecision.DELETE, SwipeDecision.DELETE_FAILED, SwipeDecision.DELETED -> DecisionDot(
                    icon = Icons.TwoTone.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(20.dp),
                )
                else -> Unit
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DecisionDot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Preview2
@Composable
private fun SwiperProgressPagerPreview() {
    PreviewWrapper {
        SwiperProgressPager(
            items = listOf(
                previewSwipeItem(id = 1, decision = SwipeDecision.KEEP),
                previewSwipeItem(id = 2, decision = SwipeDecision.UNDECIDED),
                previewSwipeItem(id = 3, decision = SwipeDecision.DELETE),
                previewSwipeItem(id = 4, decision = SwipeDecision.UNDECIDED),
            ),
            currentIndex = 1,
            onItemClick = {},
        )
    }
}
