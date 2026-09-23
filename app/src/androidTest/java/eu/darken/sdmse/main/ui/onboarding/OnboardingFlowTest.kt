package eu.darken.sdmse.main.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.darken.sdmse.R
import eu.darken.sdmse.common.BuildConfigWrap
import eu.darken.sdmse.main.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import testhelper.BaseUITest
import eu.darken.sdmse.common.R as CommonR

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest : BaseUITest() {

    @get:Rule val composeRule = createEmptyComposeRule()

    private fun str(@StringRes id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun freshInstallWalksOnboardingIntoSetup() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            awaitSetupCard(R.string.setup_root_card_title)
        }
    }

    @Test
    fun completedOnboardingRelaunchesIntoDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            awaitSetupCard(R.string.setup_root_card_title)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitNode(hasContentDescription(str(CommonR.string.general_scan_action)))
            composeRule.onAllNodesWithText(str(R.string.onboarding_welcome_title)).assertCountEquals(0)
        }
    }

    private fun walkOnboarding() {
        awaitNode(hasText(str(R.string.onboarding_welcome_title)))
        composeRule.onNodeWithText(str(R.string.onboarding_welcome_continue_action)).performClick()

        awaitNode(hasText(str(R.string.onboarding_privacy_title)))
        awaitGone(hasText(str(R.string.onboarding_welcome_title)))
        // MOTD and update checks would reach the network from the dashboard.
        switchOff(R.string.motd_setting_enabled_label)
        if (BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS) {
            switchOff(R.string.updatecheck_setting_enabled_label)
        }
        composeRule.onNodeWithText(str(R.string.onboarding_welcome_continue_action)).performClick()

        awaitNode(hasText(str(R.string.onboarding_setup_continue_action)))
        awaitGone(hasText(str(R.string.onboarding_privacy_title)))
        // Tours would overlay Setup and Dashboard.
        switchOff(R.string.onboarding_setup_tours_enabled_label)
        composeRule.onNodeWithText(str(R.string.onboarding_setup_continue_action)).performClick()
        awaitGone(hasText(str(R.string.onboarding_setup_continue_action)))
    }

    private fun switchOff(@StringRes label: Int) {
        val row = hasText(str(label)) and isToggleable()
        awaitNode(row)
        composeRule.onNode(row).performScrollTo().performClick()
        awaitNode(row and isOff())
    }

    private fun awaitSetupCard(@StringRes title: Int) {
        val card = hasText(str(title))
        composeRule.waitUntil("setup card '${str(title)}'", TIMEOUT_MS) {
            runCatching {
                composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(card)
            }.isSuccess
        }
    }

    private fun awaitNode(matcher: SemanticsMatcher) =
        composeRule.waitUntil(matcher.description, TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }

    private fun awaitGone(matcher: SemanticsMatcher) =
        composeRule.waitUntil("gone: ${matcher.description}", TIMEOUT_MS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
        }

    companion object {
        private const val TIMEOUT_MS = 30_000L
    }
}
