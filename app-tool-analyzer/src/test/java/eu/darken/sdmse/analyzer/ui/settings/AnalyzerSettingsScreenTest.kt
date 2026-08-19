package eu.darken.sdmse.analyzer.ui.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.stats.core.LowStorage
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class AnalyzerSettingsScreenTest : BaseComposeRobolectricTest() {

    private fun ComposeContentTestRule.setSettingsScreen(
        state: AnalyzerSettingsViewModel.State,
        onThresholdChanged: (Long?) -> Unit = {},
        onNotificationChanged: (Boolean) -> Unit = {},
        onUpgradeClick: () -> Unit = {},
    ) {
        setContent {
            PreviewWrapper {
                AnalyzerSettingsScreen(
                    state = state,
                    onThresholdChanged = onThresholdChanged,
                    onNotificationChanged = onNotificationChanged,
                    onUpgradeClick = onUpgradeClick,
                )
            }
        }
    }

    @Test
    fun `top bar shows the tool name`() {
        composeRule.setSettingsScreen(AnalyzerSettingsViewModel.State())

        composeRule.onNodeWithText("StorageAnalyzer").assertExists()
    }

    @Test
    fun `the threshold row is visible at startup`() {
        composeRule.setSettingsScreen(AnalyzerSettingsViewModel.State())

        composeRule.onNodeWithText("Low storage threshold").assertExists()
    }

    @Test
    fun `an unknown capacity renders a bare Automatic`() {
        composeRule.setSettingsScreen(
            AnalyzerSettingsViewModel.State(
                customThresholdBytes = null,
                primaryCapacityBytes = null,
                effectiveThresholdBytes = null,
            ),
        )

        composeRule.onNodeWithText("Automatic").assertExists()
    }

    @Test
    fun `a known capacity renders the resolved automatic value`() {
        composeRule.setSettingsScreen(
            AnalyzerSettingsViewModel.State(
                customThresholdBytes = null,
                primaryCapacityBytes = 128_000_000_000L,
                effectiveThresholdBytes = LowStorage.AUTO_MAX_BYTES,
            ),
        )

        composeRule.onNode(hasText("Automatic (currently", substring = true)).assertExists()
    }

    @Test
    fun `tapping the threshold row opens the size dialog`() {
        composeRule.setSettingsScreen(
            AnalyzerSettingsViewModel.State(
                customThresholdBytes = 10_000_000_000L,
                primaryCapacityBytes = 128_000_000_000L,
                effectiveThresholdBytes = 10_000_000_000L,
            ),
        )

        // Before the tap the dialog's buttons are not in the tree.
        composeRule.onNodeWithText("Save").assertDoesNotExist()

        composeRule.onNodeWithText("Low storage threshold").performClick()

        composeRule.onNodeWithText("Save").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun `the dialog's neutral button resets to automatic`() {
        // The neutral button reads "Automatic" here instead of the shared "Reset" label.
        var captured: Long? = -1L
        var calls = 0
        composeRule.setSettingsScreen(
            state = AnalyzerSettingsViewModel.State(
                customThresholdBytes = 10_000_000_000L,
                primaryCapacityBytes = 128_000_000_000L,
                effectiveThresholdBytes = 10_000_000_000L,
            ),
            onThresholdChanged = {
                captured = it
                calls++
            },
        )

        composeRule.onNodeWithText("Low storage threshold").performClick()
        composeRule.onNodeWithText("Automatic").performClick()

        calls shouldBe 1
        captured shouldBe null
        // The dialog closed again.
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `cancelling the dialog changes nothing`() {
        var calls = 0
        composeRule.setSettingsScreen(
            state = AnalyzerSettingsViewModel.State(
                customThresholdBytes = 10_000_000_000L,
                primaryCapacityBytes = 128_000_000_000L,
                effectiveThresholdBytes = 10_000_000_000L,
            ),
            onThresholdChanged = { calls++ },
        )

        composeRule.onNodeWithText("Low storage threshold").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        calls shouldBe 0
        composeRule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `the low space warning row is visible`() {
        composeRule.setSettingsScreen(AnalyzerSettingsViewModel.State())

        composeRule.onNodeWithText("Low space warning").assertExists()
    }

    @Test
    fun `a non-Pro tap opens the upgrade flow instead of toggling`() {
        var toggles = 0
        var upgrades = 0
        composeRule.setSettingsScreen(
            state = AnalyzerSettingsViewModel.State(isPro = false, notificationEnabled = false),
            onNotificationChanged = { toggles++ },
            onUpgradeClick = { upgrades++ },
        )

        composeRule.onNodeWithText("Low space warning").performClick()

        toggles shouldBe 0
        upgrades shouldBe 1
    }

    @Test
    fun `a Pro tap toggles the warning`() {
        var captured: Boolean? = null
        var upgrades = 0
        composeRule.setSettingsScreen(
            state = AnalyzerSettingsViewModel.State(isPro = true, notificationEnabled = false),
            onNotificationChanged = { captured = it },
            onUpgradeClick = { upgrades++ },
        )

        composeRule.onNodeWithText("Low space warning").performClick()

        captured shouldBe true
        upgrades shouldBe 0
    }

    private infix fun <T> T.shouldBe(expected: T) {
        if (this != expected) throw AssertionError("Expected <$expected> but was <$this>")
    }
}
