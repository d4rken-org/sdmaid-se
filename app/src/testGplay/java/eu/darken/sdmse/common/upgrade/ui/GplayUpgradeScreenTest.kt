package eu.darken.sdmse.common.upgrade.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.ProductDetails
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class GplayUpgradeScreenTest : BaseComposeRobolectricTest() {

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
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body_no_trial)).assertCountEquals(1)
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
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body_no_trial)).assertCountEquals(1)
    }

    @Test
    fun `returning buyer sees the restore banner and can trigger restore`() {
        var restoreClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.STANDARD,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                    wasPreviouslyPro = true,
                ),
                onRestore = { restoreClicks++ },
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER_ACTION).performClick()
        composeRule.runOnIdle { check(restoreClicks == 1) { "expected 1 restore click, got $restoreClicks" } }
    }

    @Test
    fun `banner is hidden without a prior purchase on this device`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.STANDARD,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                    wasPreviouslyPro = false,
                ),
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER).assertCountEquals(0)
    }

    @Test
    fun `both restore affordances are disabled while a restore is running`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.STANDARD,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                    wasPreviouslyPro = true,
                    restoreInProgress = true,
                ),
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertIsNotEnabled()
    }

    @Test
    fun `a running restore pauses the buy actions too`() {
        // toLoadedState computes the enabled flags: a restore in progress (manual or the invisible
        // already-owned recovery) must gate them even when offers are available -- a buy tap would
        // just race the restore into ITEM_ALREADY_OWNED.
        val iapOffer = mockk<ProductDetails.OneTimePurchaseOfferDetails>(relaxed = true)
        val iapDetails = mockk<ProductDetails>(relaxed = true).apply {
            every { oneTimePurchaseOfferDetails } returns iapOffer
        }
        val subOffer = mockk<ProductDetails.SubscriptionOfferDetails>(relaxed = true).apply {
            every { basePlanId } returns OurSku.Sub.PRO_UPGRADE.BASE_OFFER.basePlanId
            every { offerId } returns null
        }
        val subDetails = mockk<ProductDetails>(relaxed = true).apply {
            every { subscriptionOfferDetails } returns listOf(subOffer)
        }

        val loaded = toLoadedState(
            iap = SkuDetails(OurSku.Iap.PRO_UPGRADE, iapDetails),
            sub = SkuDetails(OurSku.Sub.PRO_UPGRADE, subDetails),
            hasIap = false,
            hasSub = false,
            restoreInProgress = true,
        )

        check(!loaded.iapEnabled) { "IAP buy must be disabled during a restore" }
        check(!loaded.subscriptionEnabled) { "Subscription buy must be disabled during a restore" }

        // Same offers without a running restore: both buys are available.
        val idle = toLoadedState(
            iap = SkuDetails(OurSku.Iap.PRO_UPGRADE, iapDetails),
            sub = SkuDetails(OurSku.Sub.PRO_UPGRADE, subDetails),
            hasIap = false,
            hasSub = false,
            restoreInProgress = false,
        )
        check(idle.iapEnabled) { "IAP buy should be enabled when idle" }
        check(idle.subscriptionEnabled) { "Subscription buy should be enabled when idle" }
    }

    @Test
    fun `unavailable state offers a retry that fires the callback`() {
        var retryClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Unavailable(
                    error = RuntimeException("Google Play services unavailable"),
                ),
                onRetry = { retryClicks++ },
            )
        }

        // The unavailable card sits below the fold of the scrollable screen: an offscreen click
        // would silently miss the button.
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_RETRY).performScrollTo().performClick()
        composeRule.runOnIdle { check(retryClicks == 1) { "expected 1 retry click, got $retryClicks" } }
    }

    @Test
    fun `options copy promises the trial only when Play returned the trial offer`() {
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

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body_no_trial)).assertCountEquals(0)
    }

    @Test
    fun `options copy drops the trial promise when only the base offer is available`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.STANDARD,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body_no_trial)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_how_body)).assertCountEquals(0)
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
