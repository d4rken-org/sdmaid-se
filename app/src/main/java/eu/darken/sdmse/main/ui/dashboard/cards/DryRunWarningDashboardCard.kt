package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton


data class DryRunWarningDashboardCardItem(
    val onDisable: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun DryRunWarningDashboardCard(item: DryRunWarningDashboardCardItem) {
    DashboardCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = stringResource(R.string.dashboard_dryrun_warning_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dashboard_dryrun_warning_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        DashboardFlatActionButton(
            onClick = item.onDisable,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                imageVector = Icons.TwoTone.Cancel,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(text = stringResource(R.string.dashboard_dryrun_warning_disable_action))
        }
    }
}

@Preview2
@Composable
private fun DryRunWarningDashboardCardPreview() {
    PreviewWrapper {
        DryRunWarningDashboardCard(
            item = DryRunWarningDashboardCardItem(
                onDisable = {},
            ),
        )
    }
}
