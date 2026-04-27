package eu.darken.sdmse.main.ui.dashboard.cards

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.progress.Progress

import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.NewBadge
import eu.darken.sdmse.squeezer.core.Squeezer
import eu.darken.sdmse.squeezer.R as SqueezerR

data class SqueezerDashboardCardItem(
    val data: Squeezer.Data?,
    val isInitializing: Boolean,
    val isNew: Boolean,
    val progress: Progress.Data?,
    val onViewDetails: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun SqueezerDashboardCard(item: SqueezerDashboardCardItem) {
    DashboardCard(onClick = item.onViewDetails.takeIf { !item.isInitializing }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_image_compress_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(CommonR.string.squeezer_tool_name),
                style = MaterialTheme.typography.titleMedium,
            )
            if (item.isNew) {
                Spacer(modifier = Modifier.width(4.dp))
                NewBadge()
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item.isInitializing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(SqueezerR.string.squeezer_explanation_short),
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.height(8.dp))

        DashboardFlatActionButton(
            onClick = item.onViewDetails,
            enabled = !item.isInitializing,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_eye_24),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(text = stringResource(CommonR.string.general_view_action))
        }
    }
}

@Preview2
@Composable
private fun SqueezerDashboardCardPreview() {
    PreviewWrapper {
        SqueezerDashboardCard(
            item = SqueezerDashboardCardItem(
                data = null,
                isInitializing = false,
                isNew = true,
                progress = null,
                onViewDetails = {},
            ),
        )
    }
}
