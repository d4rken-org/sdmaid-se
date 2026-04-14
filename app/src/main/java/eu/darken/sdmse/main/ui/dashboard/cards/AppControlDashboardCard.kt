package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.appcontrol.R as AppControlR
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.main.ui.dashboard.items.AppControlDashCardVH

@Composable
internal fun AppControlDashboardCard(item: AppControlDashCardVH.Item) {
    DashboardCard(onClick = item.onViewDetails.takeIf { !item.isInitializing }) {
        SimpleToolCardHeader(
            iconRes = CommonR.drawable.ic_apps,
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
private fun AppControlDashboardCardPreview() {
    PreviewWrapper {
        AppControlDashboardCard(
            item = AppControlDashCardVH.Item(
                data = null,
                isInitializing = false,
                progress = null,
                onViewDetails = {},
            ),
        )
    }
}
