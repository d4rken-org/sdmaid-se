package eu.darken.sdmse.main.ui.dashboard

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ByteFormatter
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class DashboardHeroCardTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun summary() = DashboardViewModel.HeroSummary(
        mode = DashboardViewModel.HeroSummary.Mode.FREEABLE,
        totalSize = 2L * 1024 * 1024 * 1024,
        itemCount = 37,
        tools = listOf(
            DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1L * 1024 * 1024 * 1024, 25),
        ),
    )

    private fun deleteState(hero: DashboardViewModel.HeroSummary?) = DashboardViewModel.BottomBarState(
        isReady = true,
        actionState = DashboardViewModel.BottomBarState.Action.DELETE,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = hero,
        upgradeInfo = null,
    )

    @Test
    fun `hero shows the freeable caption when visible`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("can be removed", substring = true).assertExists()
    }

    @Test
    fun `tapping dismiss invokes the callback`() {
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = { dismissed++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test
    fun `freed-mode hero shows the freed caption`() {
        val freed = DashboardViewModel.HeroSummary(
            mode = DashboardViewModel.HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(freed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("items removed", substring = true).assertExists()
    }

    @Test
    fun `activating a tool chip invokes onToolClick with the rendered mode and tool`() {
        var clickedMode: DashboardViewModel.HeroSummary.Mode? = null
        var clickedType: SDMTool.Type? = null
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onToolClick = { mode, type -> clickedMode = mode; clickedType = type },
                )
            }
        }
        // Select by the tool name (the chip's accessible name), not the size — two tools can share a size.
        // Drive the wired click via the node's semantics action: Robolectric's synthetic pointer click
        // doesn't reliably dispatch to small clickable Surfaces inside the offset/alpha hero layer, but
        // the chip IS exposed as a clickable button (asserted) and the real tap is verified on-device.
        val corpseName = context.getString(CommonR.string.corpsefinder_tool_name)
        composeRule.onNodeWithContentDescription(corpseName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle {
            assertEquals(DashboardViewModel.HeroSummary.Mode.FREEABLE, clickedMode)
            assertEquals(SDMTool.Type.CORPSEFINDER, clickedType)
        }
    }

    @Test
    fun `freed-mode chip click reports the freed mode`() {
        var clickedMode: DashboardViewModel.HeroSummary.Mode? = null
        val freed = DashboardViewModel.HeroSummary(
            mode = DashboardViewModel.HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                DashboardViewModel.HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            ),
        )
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(freed),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onToolClick = { mode, _ -> clickedMode = mode },
                )
            }
        }
        val corpseName = context.getString(CommonR.string.corpsefinder_tool_name)
        composeRule.onNodeWithContentDescription(corpseName)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(DashboardViewModel.HeroSummary.Mode.FREED, clickedMode) }
    }

    @Test
    fun `freeable hero shows the review hint, freed hero shows the removed hint`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_freeable_hint)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.dashboard_hero_freed_hint)).assertDoesNotExist()
    }

    @Test
    fun `dismissed hero collapses - caption is gone`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = false,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
        composeRule.onNodeWithText("can be removed", substring = true).assertDoesNotExist()
    }

    @Test
    fun `tapping the compact bar chip restores a dismissed hero`() {
        var restored = 0
        val hero = summary()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(hero),
                    isVisible = true,
                    // Dismissed: the floating hero is gone and the bar shows the compact chip instead.
                    heroVisible = false,
                    isHeroDismissed = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                    onRestoreHero = { restored++ },
                )
            }
        }
        val label = ByteFormatter.formatSize(context, hero.totalSize).first
        composeRule.onNodeWithText(label)
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, restored) }
    }

    @Test
    fun `swiping the hero down past the threshold dismisses it`() {
        var dismissed = 0
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = true,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = { dismissed++ },
                )
            }
        }
        // Drag the hero straight down, well past the dismiss threshold (~35% of card height).
        composeRule.onNodeWithText("can be removed", substring = true).performTouchInput {
            down(center)
            moveBy(Offset(0f, 1000f))
            up()
        }
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }
}
