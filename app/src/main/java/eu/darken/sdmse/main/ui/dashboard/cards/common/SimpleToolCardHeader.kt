package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun SimpleToolCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isInitializing: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (isInitializing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 3.dp,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Preview2
@Composable
private fun SimpleToolCardHeaderIdlePreview() {
    PreviewWrapper {
        SimpleToolCardHeader(
            icon = Icons.AutoMirrored.TwoTone.ViewList,
            title = "Sample Tool",
            subtitle = "A short subtitle that describes the tool's purpose.",
            isInitializing = false,
        )
    }
}

@Preview2
@Composable
private fun SimpleToolCardHeaderInitializingPreview() {
    PreviewWrapper {
        SimpleToolCardHeader(
            icon = Icons.AutoMirrored.TwoTone.ViewList,
            title = "Sample Tool",
            subtitle = "A short subtitle that describes the tool's purpose.",
            isInitializing = true,
        )
    }
}
