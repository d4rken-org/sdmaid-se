package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
internal fun DashboardListCard(
    modifier: Modifier = Modifier,
    item: DashboardItem,
) {
    when (item) {
        is TitleDashboardCardItem -> TitleDashboardCard(item)
        is SetupDashboardCardItem -> SetupDashboardCard(modifier = modifier, item = item)
        is UpdateDashboardCardItem -> UpdateDashboardCard(item)
        is UpgradeDashboardCardItem -> UpgradeDashboardCard(item)
        is ToolDashboardCardItem -> ToolDashboardCard(modifier = modifier, item = item)
        is AppControlDashboardCardItem -> AppControlDashboardCard(item)
        is AnalyzerDashboardCardItem -> AnalyzerDashboardCard(item)
        is SqueezerDashboardCardItem -> SqueezerDashboardCard(item)
        is SchedulerDashboardCardItem -> SchedulerDashboardCard(item)
        is DebugRecorderDashboardCardItem -> DebugRecorderDashboardCard(item)
        is MotdDashboardCardItem -> MotdDashboardCard(item)
        is ReviewDashboardCardItem -> ReviewDashboardCard(item)
        is AnniversaryDashboardCardItem -> AnniversaryDashboardCard(item)
        is StatsDashboardCardItem -> StatsDashboardCard(item)
        is SwiperDashboardCardItem -> SwiperDashboardCard(modifier = modifier, item = item)
        is DebugDashboardCardItem -> DebugDashboardCard(item)
        is ErrorDataAreaDashboardCardItem -> ErrorDataAreaDashboardCard(item)
    }
}
