package eu.darken.sdmse.main.ui.dashboard.bottom

import android.content.Context
import android.text.format.DateUtils
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
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import eu.darken.sdmse.main.core.SDMTool
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant

class DashboardHeroCardTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun summary(timestamp: Instant? = null) = HeroSummary(
        mode = HeroSummary.Mode.FREEABLE,
        totalSize = 2L * 1024 * 1024 * 1024,
        itemCount = 37,
        tools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
            HeroSummary.ToolSlice(SDMTool.Type.SYSTEMCLEANER, 1L * 1024 * 1024 * 1024, 25),
        ),
        timestamp = timestamp,
    )

    private fun deleteState(
        hero: HeroSummary?,
        now: Instant = Instant.EPOCH,
    ) = BottomBarState(
        isReady = true,
        actionState = BottomBarState.Action.DELETE,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = hero,
        upgradeInfo = null,
        now = now,
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
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
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
        var clickedMode: HeroSummary.Mode? = null
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
            assertEquals(HeroSummary.Mode.FREEABLE, clickedMode)
            assertEquals(SDMTool.Type.CORPSEFINDER, clickedType)
        }
    }

    @Test
    fun `freed-mode chip click reports the freed mode`() {
        var clickedMode: HeroSummary.Mode? = null
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
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
        composeRule.runOnIdle { assertEquals(HeroSummary.Mode.FREED, clickedMode) }
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
    fun `freeable hero shows the discard button and tapping it invokes the callback`() {
        var discarded = 0
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
                    onDiscardResults = { discarded++ },
                )
            }
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_discard_action))
            .assertHasClickAction()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(1, discarded) }
    }

    @Test
    fun `freed-mode hero has no discard button`() {
        val freed = HeroSummary(
            mode = HeroSummary.Mode.FREED,
            totalSize = 1L * 1024 * 1024 * 1024,
            itemCount = 12,
            tools = listOf(
                HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
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
                    onDiscardResults = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(CommonR.string.general_discard_action)).assertDoesNotExist()
    }

    @Test
    fun `hero footer renders the relative result age`() {
        val now = Instant.parse("2026-06-10T12:00:00Z")
        val scannedAt = now.minusSeconds(5 * 60)
        // Same call the card makes — locale-proof expectation.
        val expected = DateUtils.getRelativeTimeSpanString(
            scannedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary(timestamp = scannedAt), now = now),
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
        composeRule.onNodeWithText(expected).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.dashboard_hero_scanned_timestamp_description, expected),
        ).assertExists()
    }

    @Test
    fun `hero footer shows no timestamp when the summary has none`() {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary(timestamp = null)),
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
        composeRule.onNodeWithText("ago", substring = true).assertDoesNotExist()
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
