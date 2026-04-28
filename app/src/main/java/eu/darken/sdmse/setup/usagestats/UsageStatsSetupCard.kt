package eu.darken.sdmse.setup.usagestats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.setup.SetupCardContainer
import eu.darken.sdmse.setup.SetupCardItem
import eu.darken.sdmse.setup.notification.GrantStateLabel
import eu.darken.sdmse.common.R as CommonR

data class UsageStatsSetupCardItem(
    override val state: UsageStatsSetupModule.Result,
    val onGrantAction: () -> Unit,
    val onHelp: () -> Unit,
) : SetupCardItem

@Composable
internal fun UsageStatsSetupCard(
    item: UsageStatsSetupCardItem,
    modifier: Modifier = Modifier,
) {
    SetupCardContainer(
        icon = Icons.TwoTone.BarChart,
        title = stringResource(R.string.setup_usagestats_title),
        modifier = modifier,
        onHelp = item.onHelp,
    ) {
        Text(
            text = stringResource(R.string.setup_usagestats_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        if (item.state.missingPermission.isEmpty()) {
            GrantStateLabel(
                text = stringResource(R.string.setup_permission_granted_label),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (!item.state.isComplete) {
            Button(
                onClick = item.onGrantAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(CommonR.string.general_grant_access_action))
            }
        }
        Text(
            text = stringResource(R.string.setup_usagestats_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Preview2
@Composable
private fun UsageStatsSetupCardPreview() {
    PreviewWrapper {
        UsageStatsSetupCard(
            item = UsageStatsSetupCardItem(
                state = UsageStatsSetupModule.Result(
                    missingPermission = setOf(Permission.PACKAGE_USAGE_STATS),
                ),
                onGrantAction = {},
                onHelp = {},
            ),
        )
    }
}
