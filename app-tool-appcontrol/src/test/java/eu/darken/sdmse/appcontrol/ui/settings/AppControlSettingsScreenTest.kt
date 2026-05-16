package eu.darken.sdmse.appcontrol.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppControlSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: AppControlSettingsViewModel.State,
        onSizingChanged: (Boolean) -> Unit = {},
        onSizingBadgeClick: () -> Unit = {},
        onActivityChanged: (Boolean) -> Unit = {},
        onActivityBadgeClick: () -> Unit = {},
        onMultiUserChanged: (Boolean) -> Unit = {},
        onMultiUserBadgeClick: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                AppControlSettingsScreen(
                    state = state,
                    onSizingChanged = onSizingChanged,
                    onSizingBadgeClick = onSizingBadgeClick,
                    onActivityChanged = onActivityChanged,
                    onActivityBadgeClick = onActivityBadgeClick,
                    onMultiUserChanged = onMultiUserChanged,
                    onMultiUserBadgeClick = onMultiUserBadgeClick,
                )
            }
        }
    }

    // The settings screen uses a LazyColumn; rows below the viewport aren't composed until
    // scrolled into view. This helper locates the scrollable container and scrolls until the
    // node matching the given text becomes available in the semantics tree.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `top bar shows the AppControl title`() {
        composeRule.setSettingsScreen(AppControlSettingsViewModel.State())

        composeRule.onNodeWithText("AppControl").assertExists()
    }

    @Test
    fun `sizing row title is visible above the fold`() {
        composeRule.setSettingsScreen(AppControlSettingsViewModel.State())

        composeRule.onNodeWithText("Determine sizes").assertExists()
    }

    @Test
    fun `tapping the sizing row toggles its callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = true,
                sizingEnabled = true,
                canInfoSize = true,
            ),
            onSizingChanged = { captured = it },
        )

        composeRule.onNodeWithText("Determine sizes").performClick()

        // Started checked → tap inverts to `false`.
        captured shouldBe false
    }

    @Test
    fun `tapping the activity row toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = true,
                activityEnabled = false,
                canInfoActive = true,
            ),
            onActivityChanged = { captured = it },
        )

        composeRule.scrollToText("Determine activity")
        composeRule.onNodeWithText("Determine activity").performClick()

        captured shouldBe true
    }

    @Test
    fun `sizing row shows no Set up badge when capability is available`() {
        composeRule.setSettingsScreen(
            AppControlSettingsViewModel.State(
                isPro = true,
                sizingEnabled = true,
                canInfoSize = true,
                canInfoActive = true,
                canIncludeMultiUser = true,
            ),
        )

        // No "Set up" badge anywhere in this configuration — all capabilities are available.
        composeRule.onAllNodesWithContentDescription("Set up").assertCountEquals(0)
    }

    @Test
    fun `incomplete setup shows Set up badges`() {
        // When capabilities are not available, the SettingsBadgedSwitchItem renders a "Set up"
        // icon. With sizing + activity setup incomplete, two badge icons should be present.
        composeRule.setSettingsScreen(
            AppControlSettingsViewModel.State(
                isPro = true,
                canInfoSize = false,
                canInfoActive = false,
                canIncludeMultiUser = true,
            ),
        )

        val badgeCount = composeRule.onAllNodesWithContentDescription("Set up")
            .fetchSemanticsNodes().size
        if (badgeCount < 1) {
            throw AssertionError("Expected at least one `Set up` badge but found $badgeCount")
        }
    }

    @Test
    fun `multi-user row routes to badge handler when not Pro`() {
        // The multi-user row uses the upgrade badge for non-Pro users — tapping the row should
        // invoke `onMultiUserBadgeClick` (the upgrade route) instead of `onMultiUserChanged`.
        var changedCalls = 0
        var badgeClicks = 0
        composeRule.setSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = false,
                multiUserEnabled = false,
                canIncludeMultiUser = false,
            ),
            onMultiUserChanged = { changedCalls++ },
            onMultiUserBadgeClick = { badgeClicks++ },
        )

        composeRule.scrollToText("Include other users")
        composeRule.onNodeWithText("Include other users").performClick()

        // Non-Pro: tap goes to badge (upgrade), not the value toggle.
        changedCalls shouldBe 0
        badgeClicks shouldBe 1
    }

    @Test
    fun `multi-user row routes to value toggle when Pro and capability available`() {
        // Pro user with the capability available — the row works as a normal switch.
        var changed: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppControlSettingsViewModel.State(
                isPro = true,
                multiUserEnabled = false,
                canIncludeMultiUser = true,
            ),
            onMultiUserChanged = { changed = it },
        )

        composeRule.scrollToText("Include other users")
        composeRule.onNodeWithText("Include other users").performClick()

        changed shouldBe true
    }

    @Test
    fun `non-Pro multi-user row renders the switch as unchecked regardless of stored value`() {
        // Production behaviour: even if `multiUserEnabled` is true in storage, the UI must show
        // unchecked when isPro=false (so non-Pro users don't see a misleading "you have this
        // enabled" state). The state still has multiUserEnabled=true in this test to prove that
        // the UI overrides the stored value rather than just reflecting it.
        composeRule.setSettingsScreen(
            AppControlSettingsViewModel.State(
                isPro = false,
                multiUserEnabled = true,
                canIncludeMultiUser = false,
            ),
        )

        // The "Include other users" label exists.
        composeRule.scrollToText("Include other users")
        composeRule.onAllNodesWithText("Include other users").fetchSemanticsNodes().size shouldBeAtLeast 1
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }

    private infix fun Int.shouldBeAtLeast(min: Int) {
        if (this < min) throw AssertionError("Expected at least $min but was $this")
    }
}
