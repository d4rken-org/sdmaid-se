package eu.darken.sdmse.main.ui.dashboard.cards

import android.app.Activity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.ui.R as UiR
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFilledActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton


data class ReviewDashboardCardItem(
    val onReview: (Activity) -> Unit,
    val onDismiss: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun ReviewDashboardCard(item: ReviewDashboardCardItem) {
    val activity = LocalContext.current as? Activity
    DashboardCard(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        onClick = { activity?.let(item.onReview) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.sdm_happy),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.review_app_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardFlatActionButton(onClick = item.onDismiss) {
                Text(text = stringResource(R.string.review_app_dismiss_action))
            }
            Spacer(modifier = Modifier.width(8.dp))
            DashboardFilledActionButton(
                modifier = Modifier.weight(1f),
                onClick = { activity?.let(item.onReview) },
                enabled = activity != null,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_google_play_24),
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
                Text(text = stringResource(R.string.review_app_review_action))
            }
        }
    }
}

@Preview2
@Composable
private fun ReviewDashboardCardPreview() {
    PreviewWrapper {
        ReviewDashboardCard(
            item = ReviewDashboardCardItem(
                onReview = {},
                onDismiss = {},
            ),
        )
    }
}
