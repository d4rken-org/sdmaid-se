package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.swiper.core.SwipeItem
import eu.darken.sdmse.swiper.ui.preview.previewSwipeItem

@Composable
internal fun SwiperFileTypeChip(
    item: SwipeItem,
    modifier: Modifier = Modifier,
) {
    val ext = item.lookup.name.substringAfterLast('.', "").lowercase()
    val display = if (ext.isEmpty()) "FILE" else ext.take(6).uppercase()
    Surface(
        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 4.dp, topEnd = 0.dp, bottomStart = 0.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Text(
            text = display,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Preview2
@Composable
private fun SwiperFileTypeChipPreview() {
    PreviewWrapper {
        SwiperFileTypeChip(item = previewSwipeItem())
    }
}
