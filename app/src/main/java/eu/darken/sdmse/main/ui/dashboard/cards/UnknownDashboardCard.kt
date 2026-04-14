package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

@Composable
internal fun UnknownDashboardCard(item: DashboardAdapter.Item) {
    DashboardCard {
        Text(
            text = item::class.simpleName ?: "Unknown card",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview2
@Composable
private fun UnknownDashboardCardPreview() {
    PreviewWrapper {
        UnknownDashboardCard(
            item = object : DashboardAdapter.Item {
                override val stableId: Long = 1
            },
        )
    }
}
