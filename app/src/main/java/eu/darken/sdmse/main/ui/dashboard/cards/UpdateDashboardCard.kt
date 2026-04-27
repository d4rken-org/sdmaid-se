package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.common.updater.UpdateChecker
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledTonalActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton


data class UpdateDashboardCardItem(
    val update: UpdateChecker.Update,
    val onDismiss: () -> Unit,
    val onViewUpdate: () -> Unit,
    val onUpdate: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun UpdateDashboardCard(item: UpdateDashboardCardItem) {
    DashboardCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = item.onViewUpdate,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(UiR.drawable.ic_new_box_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.updates_dashcard_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.updates_dashcard_body,
                "v${BuildConfigWrap.VERSION_NAME}",
                item.update.versionName,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardFlatActionButton(onClick = item.onViewUpdate) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_eye_24),
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            DashboardFilledTonalActionButton(onClick = item.onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
            Spacer(modifier = Modifier.width(8.dp))
            DashboardFilledTonalActionButton(onClick = item.onUpdate) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_download_24),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = stringResource(CommonR.string.general_update_action))
            }
        }
    }
}

@Preview2
@Composable
private fun UpdateDashboardCardPreview() {
    val update = object : UpdateChecker.Update {
        override val channel: UpdateChecker.Channel = UpdateChecker.Channel.BETA
        override val versionName: String = "2.0.0-rc1"
    }

    PreviewWrapper {
        UpdateDashboardCard(
            item = UpdateDashboardCardItem(
                update = update,
                onDismiss = {},
                onViewUpdate = {},
                onUpdate = {},
            ),
        )
    }
}
