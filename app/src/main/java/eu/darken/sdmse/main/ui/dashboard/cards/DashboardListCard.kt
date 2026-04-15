package eu.darken.sdmse.main.ui.dashboard.cards

import androidx.compose.runtime.Composable
import eu.darken.sdmse.common.debug.recorder.ui.DebugRecorderCardVH
import eu.darken.sdmse.main.ui.dashboard.DashboardAdapter
import eu.darken.sdmse.main.ui.dashboard.DashboardToolCard
import eu.darken.sdmse.main.ui.dashboard.items.AnalyzerDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.AnniversaryCardVH
import eu.darken.sdmse.main.ui.dashboard.items.AppControlDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.DebugCardVH
import eu.darken.sdmse.main.ui.dashboard.items.ErrorDataAreaVH
import eu.darken.sdmse.main.ui.dashboard.items.ReviewCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SchedulerDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SetupCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SqueezerDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.StatsDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.SwiperDashCardVH
import eu.darken.sdmse.main.ui.dashboard.items.TitleCardVH
import eu.darken.sdmse.main.ui.dashboard.items.UpdateCardVH
import eu.darken.sdmse.main.ui.dashboard.items.UpgradeCardVH

@Composable
internal fun DashboardListCard(item: DashboardAdapter.Item) {
    when (item) {
        is TitleCardVH.Item -> TitleDashboardCard(item)
        is SetupCardVH.Item -> SetupDashboardCard(item)
        is UpdateCardVH.Item -> UpdateDashboardCard(item)
        is UpgradeCardVH.Item -> UpgradeDashboardCard(item)
        is DashboardToolCard.Item -> ToolDashboardCard(item)
        is AppControlDashCardVH.Item -> AppControlDashboardCard(item)
        is AnalyzerDashCardVH.Item -> AnalyzerDashboardCard(item)
        is SqueezerDashCardVH.Item -> SqueezerDashboardCard(item)
        is SchedulerDashCardVH.Item -> SchedulerDashboardCard(item)
        is DebugRecorderCardVH.Item -> DebugRecorderDashboardCard(item)
        is MotdDashboardCardItem -> MotdDashboardCard(item)
        is ReviewCardVH.Item -> ReviewDashboardCard(item)
        is AnniversaryCardVH.Item -> AnniversaryDashboardCard(item)
        is StatsDashCardVH.Item -> StatsDashboardCard(item)
        is SwiperDashCardVH.Item -> SwiperDashboardCard(item)
        is DebugCardVH.Item -> DebugDashboardCard(item)
        is ErrorDataAreaVH.Item -> ErrorDataAreaDashboardCard(item)
        else -> UnknownDashboardCard(item)
    }
}
