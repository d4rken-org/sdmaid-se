package eu.darken.sdmse.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerScanTask
import eu.darken.sdmse.common.WebpageTool
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.corpsefinder.core.tasks.CorpseFinderScanTask
import eu.darken.sdmse.deduplicator.core.tasks.DeduplicatorScanTask
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.DashboardScreen
import eu.darken.sdmse.main.ui.dashboard.DashboardViewModel
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import eu.darken.sdmse.main.ui.dashboard.cards.TitleDashboardCardItem
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import eu.darken.sdmse.systemcleaner.core.tasks.SystemCleanerScanTask

private const val KB = 1024L
private const val MB = 1024L * KB

private fun toolCard(
    type: SDMTool.Type,
    result: SDMTool.Task.Result,
) = ToolDashboardCardItem(
    toolType = type,
    isInitializing = false,
    result = result,
    progress = null,
    showProRequirement = false,
    onScan = {},
    onDelete = {},
    onViewTool = {},
    onViewDetails = {},
    onCancel = {},
)

@PreviewTest
@PlayStoreLocales
@Composable
fun DashboardScreenshot() {
    val context = LocalContext.current
    PreviewWrapper {
        DashboardScreen(
            listState = DashboardViewModel.ListState(
                items = listOf(
                    TitleDashboardCardItem(
                        upgradeInfo = null,
                        isWorking = false,
                        onRibbonClicked = {},
                        webpageTool = WebpageTool(context),
                        onMascotTriggered = {},
                    ),
                    toolCard(SDMTool.Type.CORPSEFINDER, CorpseFinderScanTask.Success(itemCount = 12, recoverableSpace = 129 * KB)),
                    toolCard(SDMTool.Type.SYSTEMCLEANER, SystemCleanerScanTask.Success(itemCount = 51, recoverableSpace = 852 * MB)),
                    toolCard(SDMTool.Type.APPCLEANER, AppCleanerScanTask.Success(itemCount = 498, recoverableSpace = 87 * MB)),
                    toolCard(SDMTool.Type.DEDUPLICATOR, DeduplicatorScanTask.Success(itemCount = 60, recoverableSpace = 32 * MB)),
                ),
            ),
            bottomBarState = BottomBarState(
                isReady = true,
                actionState = BottomBarState.Action.DELETE,
                activeTasks = 0,
                queuedTasks = 0,
                heroSummary = HeroSummary(
                    mode = HeroSummary.Mode.FREEABLE,
                    totalSize = 971 * MB,
                    itemCount = 621,
                    tools = listOf(
                        HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 129 * KB, 12),
                        HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 852 * MB, 51),
                        HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 87 * MB, 498),
                        HeroSummary.ToolSlice(SDMTool.Type.DEDUPLICATOR, 32 * MB, 60),
                    ),
                ),
                upgradeInfo = null,
            ),
        )
    }
}
