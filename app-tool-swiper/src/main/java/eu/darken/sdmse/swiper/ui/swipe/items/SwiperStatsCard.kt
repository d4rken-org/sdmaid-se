package eu.darken.sdmse.swiper.ui.swipe.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.swiper.ui.swipe.SwiperSwipeViewModel

@Composable
internal fun SwiperStatsCard(
    state: SwiperSwipeViewModel.State,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .wrapContentWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProgressRing(percent = state.progressPercent)
            StatBadge(
                icon = Icons.AutoMirrored.TwoTone.HelpOutline,
                count = state.undecidedCount,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatBadge(
                icon = Icons.TwoTone.Favorite,
                count = state.keepCount,
                tint = MaterialTheme.colorScheme.primary,
            )
            StatBadge(
                icon = Icons.TwoTone.Delete,
                count = state.deleteCount,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProgressRing(percent: Int) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StatBadge(
    icon: ImageVector,
    count: Int,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = tint,
        )
    }
}
