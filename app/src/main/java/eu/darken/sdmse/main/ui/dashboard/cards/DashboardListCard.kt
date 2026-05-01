package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.runtime.Composable


@Composable
internal fun DashboardListCard(item: DashboardItem) {
    when (item) {
        is TitleDashboardCardItem -> TitleDashboardCard(item)
        is SetupDashboardCardItem -> SetupDashboardCard(item)
        is UpdateDashboardCardItem -> UpdateDashboardCard(item)
        is UpgradeDashboardCardItem -> UpgradeDashboardCard(item)
        is ToolDashboardCardItem -> ToolDashboardCard(item)
        is AppControlDashboardCardItem -> AppControlDashboardCard(item)
        is AnalyzerDashboardCardItem -> AnalyzerDashboardCard(item)
        is SqueezerDashboardCardItem -> SqueezerDashboardCard(item)
        is SchedulerDashboardCardItem -> SchedulerDashboardCard(item)
        is DebugRecorderDashboardCardItem -> DebugRecorderDashboardCard(item)
        is MotdDashboardCardItem -> MotdDashboardCard(item)
        is ReviewDashboardCardItem -> ReviewDashboardCard(item)
        is AnniversaryDashboardCardItem -> AnniversaryDashboardCard(item)
        is StatsDashboardCardItem -> StatsDashboardCard(item)
        is SwiperDashboardCardItem -> SwiperDashboardCard(item)
        is DebugDashboardCardItem -> DebugDashboardCard(item)
        is ErrorDataAreaDashboardCardItem -> ErrorDataAreaDashboardCard(item)
    }
}
