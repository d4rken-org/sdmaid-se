package eu.darken.sdmse.main.ui.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.motd.Motd
import eu.darken.sdmse.main.core.motd.MotdState
import eu.darken.sdmse.main.ui.dashboard.items.MotdCardVH
import eu.darken.sdmse.common.ui.R as UiR
import java.util.Locale
import java.util.UUID

@Composable
internal fun MotdDashboardCard(item: MotdCardVH.Item) {
    DashboardCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.dashboard_motd_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (item.state.allowTranslation) {
                IconButton(onClick = item.onTranslate) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_g_translate_24),
                        contentDescription = null,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        MotdBody(item.state)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardFlatActionButton(onClick = item.onDismiss) {
                Text(text = stringResource(CommonR.string.general_dismiss_action))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (item.state.motd.primaryLink != null) {
                DashboardFilledTonalActionButton(onClick = item.onPrimary) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_eye_24),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                    Text(text = stringResource(CommonR.string.general_show_details_action))
                }
            }
        }
    }
}

@Preview2
@Composable
private fun MotdDashboardCardPreview() {
    PreviewWrapper {
        MotdDashboardCard(
            item = MotdCardVH.Item(
                state = MotdState(
                    motd = Motd(
                        id = UUID.randomUUID(),
                        message = "Compose dashboard cards now live here. This is a preview message.",
                        primaryLink = "https://example.com",
                    ),
                    locale = Locale.ENGLISH,
                ),
                onPrimary = {},
                onTranslate = {},
                onDismiss = {},
            ),
        )
    }
}
