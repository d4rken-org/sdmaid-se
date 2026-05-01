package eu.darken.sdmse.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.semantics.SemanticsActions
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class FossUpgradeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `renders redesigned foss content without duplicated app bar title`() {
        composeRule.setUpgradeContent {
            UpgradeScreen()
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_why_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(firstFeatureLine(context, R.string.upgrade_screen_why_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_sponsor_action_hint)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
    }

    @Test
    fun `sponsor button invokes callback`() {
        var clicked = false

        composeRule.setUpgradeContent {
            UpgradeScreen(onGithubSponsors = { clicked = true })
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.FOSS_SPONSOR).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.FOSS_SPONSOR).performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}

private fun ComposeContentTestRule.setUpgradeContent(
    content: @Composable () -> Unit,
) {
    setContent {
        PreviewWrapper {
            content()
        }
    }
}

private fun firstFeatureLine(context: Context, resId: Int): String = context.getString(resId)
    .lineSequence()
    .map { it.trim() }
    .first { it.startsWith("•") }
    .removePrefix("•")
    .trim()
