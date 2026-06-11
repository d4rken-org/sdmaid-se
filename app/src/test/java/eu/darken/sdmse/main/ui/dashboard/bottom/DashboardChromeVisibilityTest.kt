package eu.darken.sdmse.main.ui.dashboard.bottom

import android.content.Context
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.main.core.SDMTool
import eu.darken.sdmse.main.ui.dashboard.BottomBarState
import eu.darken.sdmse.main.ui.dashboard.HeroSummary
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import eu.darken.sdmse.common.R as CommonR

/**
 * Hidden chrome slides off-screen via offset() but stays composed for its exit animation — these
 * tests pin down that it is unreachable while hidden: absent from the accessibility tree (which
 * also covers TalkBack) and not focusable, while visible chrome stays fully interactive.
 */
class DashboardChromeVisibilityTest : BaseComposeRobolectricTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun summary() = HeroSummary(
        mode = HeroSummary.Mode.FREEABLE,
        totalSize = 2L * 1024 * 1024 * 1024,
        itemCount = 37,
        tools = listOf(
            HeroSummary.ToolSlice(SDMTool.Type.CORPSEFINDER, 1L * 1024 * 1024 * 1024, 12),
        ),
    )

    private fun deleteState(hero: HeroSummary?) = BottomBarState(
        isReady = true,
        actionState = BottomBarState.Action.DELETE,
        activeTasks = 0,
        queuedTasks = 0,
        heroSummary = hero,
        upgradeInfo = null,
    )

    private fun setChrome(isVisible: Boolean) {
        composeRule.setContent {
            PreviewWrapper {
                BottomBar(
                    state = deleteState(summary()),
                    isVisible = isVisible,
                    heroVisible = true,
                    onMainAction = {},
                    onMainActionLongClick = {},
                    onSettings = {},
                    onUpgrade = {},
                    onDismissHero = {},
                )
            }
        }
    }

    @Test
    fun `hidden chrome is absent from the accessibility tree`() {
        setChrome(isVisible = false)

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_settings_title))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_delete_action))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_dismiss_action))
            .assertDoesNotExist()
    }

    @Test
    fun `visible chrome exposes its controls`() {
        setChrome(isVisible = true)

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_settings_title))
            .assertExists()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_delete_action))
            .assertExists()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_dismiss_action))
            .assertExists()
    }

    @Test
    fun `visible bar controls accept focus`() {
        setChrome(isVisible = true)

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_settings_title))
            .requestFocus()
            .assertIsFocused()
    }

    @Test
    fun `visible hero controls accept focus`() {
        setChrome(isVisible = true)

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.general_dismiss_action))
            .requestFocus()
            .assertIsFocused()
    }
}
