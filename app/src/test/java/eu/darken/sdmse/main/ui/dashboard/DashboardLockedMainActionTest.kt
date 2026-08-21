package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalTourTargetRegistry
import eu.darken.sdmse.common.compose.tour.TourTargetRegistry
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.cards.ToolDashboardCardItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

/**
 * The main action button for a LOCKED_ONLY hero must navigate to the upgrade screen, never re-enter
 * the engine. The card's verdict comes from the dashboard's upgrade flow, while the engine's
 * branches re-check `isProForUi()`, which fails open to `true` on any exception — so handing the tap
 * back could reach a real deletion on a screen that deliberately skipped the confirmation dialog.
 */
class DashboardLockedMainActionTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val deleteLabel get() = context.getString(CommonR.string.general_delete_action)

    private fun items() = listOf(
        ToolDashboardCardItem(
            toolType = SDMTool.Type.CORPSEFINDER,
            isInitializing = false,
            result = null,
            progress = null,
            showProRequirement = false,
            onScan = {},
            onDelete = {},
            onViewTool = {},
            onViewDetails = {},
            onCancel = {},
            onDismissResult = null,
        ),
    )

    private fun barState(hero: HeroSummary) = BottomBarState(
        isReady = true,
        actionState = BottomBarState.Action.DELETE,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = hero,
        upgradeInfo = null,
    )

    private fun lockedOnlyHero() = HeroSummary(
        mode = HeroSummary.Mode.LOCKED_ONLY,
        totalSize = 0L,
        itemCount = 0,
        tools = emptyList(),
        lockedTools = listOf(HeroSummary.ToolSlice(SDMTool.Type.APPCLEANER, 512L * 1024 * 1024, 9)),
    )

    private fun freeableHero() = HeroSummary(
        mode = HeroSummary.Mode.FREEABLE,
        totalSize = 1024L * 1024L,
        itemCount = 3,
        tools = listOf(HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1024L * 1024L, 3)),
    )

    private fun setContent(
        hero: HeroSummary,
        onMainAction: () -> Unit,
        onUpgrade: () -> Unit,
    ) {
        val controller = mockk<GuidedTourController>(relaxed = true).also {
            coEvery { it.shouldStart(any()) } returns false
            every { it.session } returns MutableStateFlow(null)
        }
        composeRule.setContent {
            PreviewWrapper {
                CompositionLocalProvider(
                    LocalTourTargetRegistry provides TourTargetRegistry(),
                    LocalGuidedTourController provides controller,
                ) {
                    DashboardScreen(
                        listState = DashboardViewModel.ListState(items = items()),
                        bottomBarState = barState(hero),
                        isHeroExpanded = true,
                        onMainAction = onMainAction,
                        onUpgrade = onUpgrade,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the main action upsells instead of deleting when everything is locked`() {
        var mainActions = 0
        var upgrades = 0
        setContent(lockedOnlyHero(), onMainAction = { mainActions++ }, onUpgrade = { upgrades++ })

        composeRule.onNodeWithContentDescription(deleteLabel)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(1, upgrades)
            // Not even the confirmation dialog: there is nothing here this user can delete.
            assertEquals(0, mainActions)
        }
    }

    @Test
    fun `the main action still deletes when there is something freeable`() {
        var mainActions = 0
        var upgrades = 0
        setContent(freeableHero(), onMainAction = { mainActions++ }, onUpgrade = { upgrades++ })

        composeRule.onNodeWithContentDescription(deleteLabel)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertEquals(1, mainActions)
            assertEquals(0, upgrades)
        }
    }
}
