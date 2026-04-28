package eu.darken.sdmse.main.ui.settings.cards.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import eu.darken.sdmse.main.core.DashboardCardType
import eu.darken.sdmse.main.core.icon
import eu.darken.sdmse.main.core.labelRes
import eu.darken.sdmse.common.ui.R as UiR

@Composable
fun DashboardCardConfigRow(
    type: DashboardCardType,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.outlinedCardColors(),
        elevation = CardDefaults.outlinedCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(type.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp),
            )
            Switch(
                checked = isVisible,
                onCheckedChange = { onToggleVisibility() },
                modifier = Modifier.padding(end = 8.dp),
            )
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                dragHandle()
            }
        }
    }
}

@Composable
fun DashboardCardConfigDragHandle(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(UiR.drawable.ic_drag_vertical_24),
        contentDescription = stringResource(CommonR.string.general_drag_handle_description),
        modifier = modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview2
@Composable
private fun DashboardCardConfigRowPreview() {
    PreviewWrapper {
        DashboardCardConfigRow(
            type = DashboardCardType.CORPSEFINDER,
            isVisible = true,
            onToggleVisibility = {},
            dragHandle = { DashboardCardConfigDragHandle() },
        )
    }
}

@Preview2
@Composable
private fun DashboardCardConfigRowHiddenPreview() {
    PreviewWrapper {
        DashboardCardConfigRow(
            type = DashboardCardType.ANALYZER,
            isVisible = false,
            onToggleVisibility = {},
            dragHandle = { DashboardCardConfigDragHandle() },
        )
    }
}
