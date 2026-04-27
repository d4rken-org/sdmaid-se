package eu.darken.sdmse.stats.ui.spacehistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.core.iconRes
import eu.darken.sdmse.main.core.labelRes

@Composable
internal fun SpaceHistoryTooltipContent(
    tool: SDMTool.Type,
    detailText: String,
) {
    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(tool.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = stringResource(tool.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview2
@Composable
private fun SpaceHistoryTooltipContentPreview() {
    PreviewWrapper {
        SpaceHistoryTooltipContent(
            tool = SDMTool.Type.APPCLEANER,
            detailText = "Freed 1.2 GB · 2:30 PM",
        )
    }
}
