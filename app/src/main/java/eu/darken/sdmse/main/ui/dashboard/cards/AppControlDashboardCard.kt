package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R as AppControlR
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.appcontrol.core.AppControl
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.SimpleToolCardHeader


data class AppControlDashboardCardItem(
    val data: AppControl.Data?,
    val isInitializing: Boolean,
    val progress: Progress.Data?,
    val onViewDetails: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun AppControlDashboardCard(item: AppControlDashboardCardItem) {
    DashboardCard(onClick = item.onViewDetails.takeIf { !item.isInitializing }) {
        SimpleToolCardHeader(
            icon = Icons.Outlined.Apps,
            title = stringResource(CommonR.string.appcontrol_tool_name),
            subtitle = stringResource(AppControlR.string.appcontrol_explanation_short),
            isInitializing = item.isInitializing,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardFlatActionButton(
            onClick = item.onViewDetails,
            enabled = !item.isInitializing,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(text = stringResource(CommonR.string.general_view_action))
        }
    }
}

@Preview2
@Composable
private fun AppControlDashboardCardPreview() {
    PreviewWrapper {
        AppControlDashboardCard(
            item = AppControlDashboardCardItem(
                data = null,
                isInitializing = false,
                progress = null,
                onViewDetails = {},
            ),
        )
    }
}
