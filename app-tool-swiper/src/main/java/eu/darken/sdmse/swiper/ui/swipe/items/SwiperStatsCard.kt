package eu.darken.sdmse.swiper.ui.swipe.items

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.swiper.ui.swipe.SwiperSwipeViewModel

@Composable
internal fun SwiperStatsCard(
    state: SwiperSwipeViewModel.State,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatColumn(
                    icon = Icons.Filled.Favorite,
                    tint = MaterialTheme.colorScheme.primary,
                    count = state.keepCount,
                    sizeBytes = state.keepSize,
                    context = context,
                )
                StatColumn(
                    icon = Icons.Filled.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    count = state.deleteCount,
                    sizeBytes = state.deleteSize,
                    context = context,
                )
                StatColumn(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    tint = MaterialTheme.colorScheme.tertiary,
                    count = state.undecidedCount,
                    sizeBytes = state.undecidedSize,
                    context = context,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun StatColumn(
    icon: ImageVector,
    tint: Color,
    count: Int,
    sizeBytes: Long,
    context: android.content.Context,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .padding(bottom = 2.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = Formatter.formatFileSize(context, sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
