package eu.darken.sdmse.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class GplayUpgradeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `loading state shows progress and hides actions`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(uiState = GplayUpgradeUiState.Loading)
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.LOADING).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.ACTIONS).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
    }

    @Test
    fun `loaded state shows trial before iap and hides loading`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.TRIAL,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                ),
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.LOADING).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.ACTIONS).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_trial_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_iap_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_action_hint, "$12.99")).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_iap_action_hint, "$24.99")).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_restore_purchase_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offer_recommended)).assertCountEquals(1)

        val subscriptionButtonTop = composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).getUnclippedBoundsInRoot().top
        val iapButtonTop = composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).getUnclippedBoundsInRoot().top

        check(subscriptionButtonTop < iapButtonTop) {
            "Expected subscription action to appear above IAP action, but got top=$subscriptionButtonTop and top=$iapButtonTop"
        }
    }

    @Test
    fun `loaded state keeps unavailable actions visible but disabled`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.UNAVAILABLE,
                    subscriptionEnabled = false,
                    subscriptionPrice = null,
                    iapEnabled = false,
                    iapPrice = null,
                ),
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.upgrade_screen_subscription_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.upgrade_screen_iap_action)).assertIsNotEnabled()
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_restore_purchase_action)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_action_hint, "$12.99")).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_iap_action_hint, "$24.99")).assertCountEquals(0)
    }

    @Test
    fun `unavailable state hides loading and purchase actions while keeping static content`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Unavailable(
                    error = RuntimeException("Google Play services unavailable"),
                ),
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.LOADING).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_IAP).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_UNAVAILABLE).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offers_unavailable_message)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
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
