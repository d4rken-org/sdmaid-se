package eu.darken.sdmse.scheduler.ui.manager.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AccessibilityNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.taskmanager.AcsScheduleRisk
import eu.darken.sdmse.scheduler.R

@Composable
internal fun AcsScreenLockedHintRow(
    modifier: Modifier = Modifier,
    risk: AcsScheduleRisk,
    onDismiss: () -> Unit,
) {
    val titleRes: Int
    val bodyRes: Int
    when (risk) {
        AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY -> {
            titleRes = R.string.scheduler_acs_screenlocked_hint_systemapps_title
            bodyRes = R.string.scheduler_acs_screenlocked_hint_systemapps_body
        }
        else -> {
            titleRes = R.string.scheduler_acs_screenlocked_hint_title
            bodyRes = R.string.scheduler_acs_screenlocked_hint_body
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.TwoTone.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
            }
        }
    }
}

@Preview2
@Composable
private fun AcsScreenLockedHintRowPreview() {
    PreviewWrapper {
        AcsScreenLockedHintRow(
            risk = AcsScheduleRisk.ACS_REQUIRED_ALL,
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview2
@Composable
private fun AcsScreenLockedHintRowSystemAppsPreview() {
    PreviewWrapper {
        AcsScreenLockedHintRow(
            risk = AcsScheduleRisk.ACS_REQUIRED_SYSTEM_APPS_ONLY,
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
