package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.progress.Progress
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardActionIconSpacing
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardCard
import eu.darken.sdmse.main.ui.dashboard.cards.common.DashboardFlatActionButton
import eu.darken.sdmse.main.ui.dashboard.cards.common.NewBadge

import eu.darken.sdmse.swiper.R as SwiperR
import eu.darken.sdmse.swiper.core.SessionState
import eu.darken.sdmse.swiper.core.SwipeSession
import eu.darken.sdmse.swiper.core.Swiper
import java.time.Duration
import java.time.Instant

data class SwiperDashboardCardItem(
    val sessionsWithStats: List<Swiper.SessionWithStats>,
    val progress: Progress.Data?,
    val showProRequirement: Boolean,
    val onViewDetails: () -> Unit,
) : DashboardItem {
    override val stableId: Long = this.javaClass.hashCode().toLong()
}

@Composable
internal fun SwiperDashboardCard(item: SwiperDashboardCardItem) {
    val context = LocalContext.current
    val subtitle = when {
        item.sessionsWithStats.isEmpty() -> stringResource(SwiperR.string.swiper_dashcard_description)
        item.sessionsWithStats.size == 1 -> {
            val sessionWithStats = item.sessionsWithStats.first()
            val session = sessionWithStats.session
            val daysAgo = Duration.between(session.lastModifiedAt, Instant.now()).toDays().toInt()
            val finished = sessionWithStats.keepCount + sessionWithStats.deleteCount
            val percent = if (session.totalItems > 0) (finished * 100 / session.totalItems) else 0
            context.resources.getQuantityString(
                SwiperR.plurals.swiper_dashcard_session_context,
                daysAgo,
                daysAgo,
                percent,
            )
        }

        else -> {
            val totalUndecided = item.sessionsWithStats.sumOf { it.undecidedCount }
            val sessionsText = context.resources.getQuantityString(
                SwiperR.plurals.swiper_dashcard_x_sessions,
                item.sessionsWithStats.size,
                item.sessionsWithStats.size,
            )
            val undecidedText = context.resources.getQuantityString(
                SwiperR.plurals.swiper_dashcard_x_undecided_items,
                totalUndecided,
                totalUndecided,
            )
            "$sessionsText, $undecidedText"
        }
    }
    val actionText = if (item.sessionsWithStats.isEmpty()) {
        stringResource(SwiperR.string.swiper_start_action)
    } else {
        stringResource(CommonR.string.general_view_action)
    }

    DashboardCard(onClick = item.onViewDetails) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_baseline_swipe_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(SwiperR.string.swiper_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.width(6.dp))
            NewBadge()
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
        )

        if (item.progress != null) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))

        DashboardFlatActionButton(
            onClick = item.onViewDetails,
            modifier = Modifier.align(Alignment.End),
        ) {
            Icon(
                painter = painterResource(CommonR.drawable.ic_baseline_swipe_24),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(DashboardActionIconSpacing))
            Text(text = actionText)
        }
    }
}

@Preview2
@Composable
private fun SwiperDashboardCardPreview() {
    PreviewWrapper {
        SwiperDashboardCard(
            item = SwiperDashboardCardItem(
                sessionsWithStats = listOf(
                    Swiper.SessionWithStats(
                        session = SwipeSession(
                            sessionId = "preview",
                            sourcePaths = emptyList(),
                            currentIndex = 42,
                            totalItems = 100,
                            createdAt = Instant.now().minusSeconds(60L * 60L * 24L * 2L),
                            lastModifiedAt = Instant.now().minusSeconds(60L * 60L * 24L),
                            state = SessionState.READY,
                        ),
                        keepCount = 28,
                        deleteCount = 14,
                        undecidedCount = 58,
                        deletedCount = 0,
                        deleteFailedCount = 0,
                    ),
                ),
                progress = null,
                showProRequirement = false,
                onViewDetails = {},
            ),
        )
    }
}
