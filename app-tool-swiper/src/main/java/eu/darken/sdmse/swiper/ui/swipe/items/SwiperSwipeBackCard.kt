package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.coil.FilePreviewImage
import eu.darken.sdmse.swiper.core.SwipeItem

@Composable
internal fun SwiperSwipeBackCard(
    item: SwipeItem,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .graphicsLayer {
                scaleX = 0.94f
                scaleY = 0.94f
                alpha = 0.7f
            },
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            FilePreviewImage(
                lookup = item.lookup,
                contentScale = ContentScale.Fit,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
