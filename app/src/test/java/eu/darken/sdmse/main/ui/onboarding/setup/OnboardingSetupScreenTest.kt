package eu.darken.sdmse.main.ui.onboarding.setup

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class OnboardingSetupScreenTest : BaseComposeRobolectricTest() {

    @Test
    fun `toggling guided tours invokes callback with the flipped value`() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            // Inspection mode skips the entry animation (isVisible starts true), so the toggle is
            // immediately laid out — same as @Preview does.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewWrapper {
                    OnboardingSetupScreen(
                        state = OnboardingSetupViewModel.State(isGuidedToursEnabled = true),
                        onGuidedToursChanged = { changes.add(it) },
                    )
                }
            }
        }

        // The toggle row sits below the fold in the test window — scroll it into view before clicking.
        // The toggle action lives on the toggleable Row (Role=Switch), not the text leaf.
        composeRule.onNode(isToggleable()).performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(listOf(false), changes) }
    }
}
