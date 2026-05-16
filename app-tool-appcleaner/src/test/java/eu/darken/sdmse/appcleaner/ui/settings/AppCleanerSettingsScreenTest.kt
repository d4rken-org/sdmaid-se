package eu.darken.sdmse.appcleaner.ui.settings

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AppCleanerSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: AppCleanerSettingsViewModel.State,
        onIncludeSystemAppsChanged: (Boolean) -> Unit = {},
        onFilterAdvertisementChanged: (Boolean) -> Unit = {},
        onFilterBugreportingChanged: (Boolean) -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                AppCleanerSettingsScreen(
                    state = state,
                    onIncludeSystemAppsChanged = onIncludeSystemAppsChanged,
                    onFilterAdvertisementChanged = onFilterAdvertisementChanged,
                    onFilterBugreportingChanged = onFilterBugreportingChanged,
                )
            }
        }
    }

    // Settings is a LazyColumn — rows below the viewport aren't composed until scrolled into view.
    private fun ComposeContentTestRule.scrollToText(text: String) {
        this.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `top bar shows the AppCleaner title`() {
        composeRule.setSettingsScreen(AppCleanerSettingsViewModel.State())

        composeRule.onNodeWithText("AppCleaner").assertExists()
    }

    @Test
    fun `top section includes the system apps switch`() {
        composeRule.setSettingsScreen(AppCleanerSettingsViewModel.State())

        // String comes from R.string.appcleaner_include_systemapps_label
        // → "Include system apps"
        composeRule.onAllNodesWithText("Include system apps").fetchSemanticsNodes().size shouldBeAtLeast 1
    }

    @Test
    fun `tapping the system apps row toggles the callback with the inverted value`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppCleanerSettingsViewModel.State(includeSystemApps = true),
            onIncludeSystemAppsChanged = { captured = it },
        )

        composeRule.scrollToText("Include system apps")
        composeRule.onNodeWithText("Include system apps").performClick()

        // Started checked → tap inverts to `false`.
        captured shouldBe false
    }

    @Test
    fun `acs section is hidden when isAcsRequired is false`() {
        composeRule.setSettingsScreen(
            AppCleanerSettingsViewModel.State(isAcsRequired = false),
        )

        // The acs-only row's title — "Include inaccessible caches" or similar. The exact resolved
        // string is R.string.appcleaner_include_inaccessible_label; we check that the visible
        // settings list has no node matching the title when acs is not required.
        // (Settings text strings can be long — we check for the substring used elsewhere.)
        composeRule.onAllNodesWithText("Include inaccessible caches")
            .fetchSemanticsNodes().size shouldBe 0
    }

    @Test
    fun `acs section appears when isAcsRequired is true`() {
        composeRule.setSettingsScreen(
            AppCleanerSettingsViewModel.State(isAcsRequired = true, isInaccessibleCacheAvailable = true),
        )

        composeRule.scrollToText("Include inaccessible caches")
        composeRule.onNodeWithText("Include inaccessible caches").assertExists()
    }

    @Test
    fun `tapping the advertisement filter toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppCleanerSettingsViewModel.State(filterAdvertisement = true),
            onFilterAdvertisementChanged = { captured = it },
        )

        composeRule.scrollToText("Advertisement data")
        composeRule.onNodeWithText("Advertisement data").performClick()

        captured shouldBe false
    }

    @Test
    fun `tapping the bug reporting filter toggles its callback`() {
        var captured: Boolean? = null
        composeRule.setSettingsScreen(
            state = AppCleanerSettingsViewModel.State(filterBugreporting = false),
            onFilterBugreportingChanged = { captured = it },
        )

        composeRule.scrollToText("Bug reporting")
        composeRule.onNodeWithText("Bug reporting").performClick()

        captured shouldBe true
    }

    private infix fun Int.shouldBeAtLeast(min: Int) {
        if (this < min) throw AssertionError("Expected at least $min but was $this")
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}
