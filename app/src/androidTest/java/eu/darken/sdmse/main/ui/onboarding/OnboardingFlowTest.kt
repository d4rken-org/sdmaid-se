package eu.darken.sdmse.main.ui.onboarding

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.darken.sdmse.R
import eu.darken.sdmse.main.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import testhelper.BaseAppFlowTest
import eu.darken.sdmse.common.R as CommonR

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest : BaseAppFlowTest() {

    @Test
    fun freshInstallWalksOnboardingIntoSetup() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            awaitOnboardingSetup()
        }
    }

    @Test
    fun completedOnboardingRelaunchesIntoDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            walkOnboarding()
            awaitOnboardingSetup()
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitNode(hasContentDescription(str(CommonR.string.general_scan_action)))
            composeRule.onAllNodesWithText(str(R.string.onboarding_welcome_title)).assertCountEquals(0)
        }
    }

    private fun awaitOnboardingSetup() {
        awaitListItem(hasText(str(R.string.setup_root_card_title)))
        awaitNode(hasContentDescription(str(CommonR.string.general_close_action)))
    }
}
