package eu.darken.sdmse.main.ui.dashboard.cards.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun SimpleToolCardHeader(
    iconRes: Int,
    title: String,
    subtitle: String,
    isInitializing: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
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
            iconRes = CommonR.drawable.ic_baseline_view_list_24,
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
            iconRes = CommonR.drawable.ic_baseline_view_list_24,
            title = "Sample Tool",
            subtitle = "A short subtitle that describes the tool's purpose.",
            isInitializing = true,
        )
    }
}
