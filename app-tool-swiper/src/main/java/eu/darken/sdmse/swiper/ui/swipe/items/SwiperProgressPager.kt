package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import eu.darken.sdmse.swiper.core.SwipeDecision
import eu.darken.sdmse.swiper.core.SwipeItem
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
                val scale = 0.8f + proximity * 0.2f
                val alpha = 0.5f + proximity * 0.5f
                ProgressThumb(
                    item = item,
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
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                Icon(
                    imageVector = Icons.TwoTone.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp)
                        .graphicsLayer {
                            shadowElevation = 8f
                        },
                )
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
