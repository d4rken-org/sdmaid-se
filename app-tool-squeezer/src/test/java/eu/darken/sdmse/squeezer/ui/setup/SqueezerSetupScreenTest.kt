package eu.darken.sdmse.squeezer.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.files.local.LocalPath
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Duration

// Note: SqueezerSetupScreen was refactored to take `minQuality` as a parameter (default = release
// value) so the screen does not reference BuildConfigWrap at composition time. Per-module unit
// tests can't initialize BuildConfigWrap (no app-module BuildConfig on classpath → NPE in static
// init), and lifting the reference into the Host avoids needing to mock that here.
class SqueezerSetupScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSetupScreen(
        state: SqueezerSetupViewModel.State,
        onPathsClick: () -> Unit = {},
        onStartScan: () -> Unit = {},
        onShowExample: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                SqueezerSetupScreen(
                    stateSource = MutableStateFlow(state),
                    onPathsClick = onPathsClick,
                    onStartScan = onStartScan,
                    onShowExample = onShowExample,
                )
            }
        }
    }

    @Test
    fun `top bar shows the tool name`() {
        composeRule.setSetupScreen(SqueezerSetupViewModel.State())

        composeRule.onNodeWithText("Media Squeeze").assertExists()
    }

    @Test
    fun `paths card shows default prompt when no scan paths are configured`() {
        composeRule.setSetupScreen(SqueezerSetupViewModel.State(scanPaths = emptyList()))

        composeRule.onNodeWithText("Select folders to search").assertExists()
    }

    @Test
    fun `paths card renders the configured path summary`() {
        // userReadablePath on a LocalPath formats to a backslash-prefixed form. The exact text is
        // implementation-defined; rather than pin a fragile string, we assert the default prompt
        // is NOT shown — meaning the path-bound branch executed.
        composeRule.setSetupScreen(
            SqueezerSetupViewModel.State(scanPaths = listOf(LocalPath.build("storage", "dcim"))),
        )

        composeRule.onAllNodesWithText("Select folders to search").assertCountEquals(0)
    }

    @Test
    fun `Start Scan button is disabled when canStartScan is false`() {
        composeRule.setSetupScreen(SqueezerSetupViewModel.State(canStartScan = false))

        composeRule.onNodeWithText("Start Scan").assertIsNotEnabled()
    }

    @Test
    fun `Start Scan button is enabled when canStartScan is true`() {
        composeRule.setSetupScreen(
            SqueezerSetupViewModel.State(
                scanPaths = listOf(LocalPath.build("storage", "dcim")),
                canStartScan = true,
            ),
        )

        composeRule.onNodeWithText("Start Scan").assertIsEnabled()
    }

    @Test
    fun `Start Scan click triggers onStartScan when enabled`() {
        var clicked = 0
        composeRule.setSetupScreen(
            state = SqueezerSetupViewModel.State(
                scanPaths = listOf(LocalPath.build("storage", "dcim")),
                canStartScan = true,
            ),
            onStartScan = { clicked++ },
        )

        // The Button merges its inner Icon/Text into its button-role node. Filter on hasText +
        // hasClickAction to land on the clickable Button itself rather than the inner Text node
        // (which has no click action of its own). The button sits at the bottom of a Column
        // inside a verticalScroll; under Robolectric's compact default viewport it may not be
        // composed at first frame — call performScrollTo() first to materialise it.
        val button = composeRule.onNode(
            androidx.compose.ui.test.hasText("Start Scan")
                .and(androidx.compose.ui.test.hasClickAction()),
        )
        button.performScrollTo()
        button.performClick()
        composeRule.waitForIdle()

        clicked shouldBe 1
    }

    @Test
    fun `paths card click triggers onPathsClick`() {
        var clicked = 0
        composeRule.setSetupScreen(
            state = SqueezerSetupViewModel.State(scanPaths = emptyList()),
            onPathsClick = { clicked++ },
        )

        // The whole card is clickable — its title "Search locations" is part of the same row, so
        // tapping the title text propagates to the card's clickable modifier.
        composeRule.onNodeWithText("Search locations").performClick()

        clicked shouldBe 1
    }

    @Test
    fun `quality slider hint reflects current quality - very low under 40`() {
        // squeezer_quality_hint_very_low fires for `q < 40`. The text "unusable" is specific to
        // that branch — switching to the next branch would surface "saves more space" instead.
        composeRule.setSetupScreen(SqueezerSetupViewModel.State(quality = 25))

        composeRule.onAllNodesWithText("unusable", substring = true)
            .fetchSemanticsNodes().size shouldBeAtLeastInt 1
    }

    @Test
    fun `estimatedSavingsPercent renders when non-null and quality below 100`() {
        // 50% savings → string is "Estimated ~50% savings for JPEG files".
        composeRule.setSetupScreen(
            SqueezerSetupViewModel.State(quality = 65, estimatedSavingsPercent = 50),
        )

        composeRule.onAllNodesWithText("savings", substring = true)
            .fetchSemanticsNodes().size shouldBeAtLeastInt 1
    }

    @Test
    fun `age card shows the current minAge in days`() {
        composeRule.setSetupScreen(
            SqueezerSetupViewModel.State(minAge = Duration.ofDays(90)),
        )

        // squeezer_min_age_x_days plural renders "90 days" for quantity 90.
        composeRule.onAllNodesWithText("90", substring = true)
            .fetchSemanticsNodes().size shouldBeAtLeastInt 1
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }

    private infix fun Int.shouldBeAtLeastInt(min: Int) {
        if (this < min) throw AssertionError("Expected at least $min but was $this")
    }
}
