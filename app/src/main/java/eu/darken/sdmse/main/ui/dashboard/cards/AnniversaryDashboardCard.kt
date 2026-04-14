package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.MascotView
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.main.ui.dashboard.items.AnniversaryCardVH
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun AnniversaryDashboardCard(item: AnniversaryCardVH.Item) {
    DashboardCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        onClick = { item.onShare(item.years) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(
                modifier = Modifier
                    .height(96.dp)
                    .padding(start = 4.dp),
                mascotMode = MascotView.MascotMode.PARTY,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(R.string.anniversary_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val formatter = remember {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                        .withZone(ZoneId.systemDefault())
                }
                Text(
                    text = stringResource(
                        R.string.anniversary_card_install_date,
                        formatter.format(item.installDate),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.anniversary_card_body,
                        item.years,
                        item.years,
                        item.spaceFreed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardFlatActionButton(onClick = item.onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
            Spacer(modifier = Modifier.weight(1f))
            DashboardFilledActionButton(onClick = { item.onShare(item.years) }) {
                Icon(
                    painter = painterResource(UiR.drawable.outline_share_24),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = stringResource(CommonR.string.general_share_action))
            }
        }
    }
}

@Preview2
@Composable
private fun AnniversaryDashboardCardPreview() {
    PreviewWrapper {
        AnniversaryDashboardCard(
            item = AnniversaryCardVH.Item(
                years = 4,
                installDate = Instant.now().minusSeconds(60L * 60L * 24L * 365L * 4L),
                spaceFreed = "42 GB",
                onShare = {},
                onDismiss = {},
            ),
        )
    }
}
