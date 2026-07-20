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
import eu.darken.sdmse.common.R as CommonR
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

    private fun appNameWithPostfixedHeroBody(bodyRes: Int): String = context.getString(
        bodyRes,
        "${context.getString(CommonR.string.app_name)} ${context.getString(R.string.app_name_upgrade_postfix)}",
    )

    @Test
    fun `loading state shows progress and hides actions`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(uiState = GplayUpgradeUiState.Loading)
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.LOADING).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.ACTIONS).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(1)
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
        // Compact rows: the price shares the title line, per-offer captions carry the terms (the
        // standalone "Options" card is gone), and there is no badge.
        composeRule.onAllNodesWithText(
            "${context.getString(R.string.upgrade_screen_subscription_offer_title)} · $12.99"
        ).assertCountEquals(1)
        composeRule.onAllNodesWithText(
            "${context.getString(R.string.upgrade_screen_iap_offer_title)} · $24.99"
        ).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offers_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offers_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offers_or)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_iap_offer_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_restore_purchase_action)).assertCountEquals(1)

        val subscriptionButtonTop = composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).getUnclippedBoundsInRoot().top
        val iapButtonTop = composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).getUnclippedBoundsInRoot().top

        check(subscriptionButtonTop < iapButtonTop) {
            "Expected subscription action to appear above IAP action, but got top=$subscriptionButtonTop and top=$iapButtonTop"
        }

        // Terms read ABOVE their buttons (description-then-action, like the restore section); the
        // header anchors the top and the parity footnote sits below both offers.
        val subscriptionCaptionTop = composeRule
            .onNodeWithText(context.getString(R.string.upgrade_screen_subscription_offer_body))
            .getUnclippedBoundsInRoot().top
        val headerTop = composeRule
            .onNodeWithText(context.getString(R.string.upgrade_screen_offers_title))
            .getUnclippedBoundsInRoot().top
        val footerTop = composeRule
            .onNodeWithText(context.getString(R.string.upgrade_screen_offers_body))
            .getUnclippedBoundsInRoot().top
        check(subscriptionCaptionTop < subscriptionButtonTop) {
            "Expected subscription terms above their button, got terms=$subscriptionCaptionTop button=$subscriptionButtonTop"
        }
        check(headerTop < subscriptionButtonTop) {
            "Expected the offers header above the offers, got header=$headerTop subButton=$subscriptionButtonTop"
        }
        check(footerTop > iapButtonTop) {
            "Expected the parity footnote below both offers, got footer=$footerTop iapButton=$iapButtonTop"
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
        // Without prices the rows fall back to bare titles (exact match proves no dangling "·"),
        // and the unavailable subscription promises no trial.
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_iap_offer_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body_no_trial)).assertCountEquals(1)
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
        // The targeted section is the ONLY restore affordance — no second generic one below.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertCountEquals(0)
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
    fun `the returning-buyer restore is disabled while a restore is running`() {
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
    }

    @Test
    fun `plain acquisition gets a described restore section below the offers`() {
        var restoreClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = GplayUpgradeUiState.Loaded(
                    subscriptionAction = SubscriptionAction.STANDARD,
                    subscriptionEnabled = true,
                    subscriptionPrice = "$12.99",
                    iapEnabled = true,
                    iapPrice = "$24.99",
                ),
                onRestore = { restoreClicks++ },
            )
        }

        // The offers card holds only offers — restore lives in its own described section. No
        // contact-support affordance here: support is only suggested after a restore came up
        // empty (the failed-restore dialog).
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_restore_body)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_contact_support_action))
            .assertCountEquals(0)
        // STANDARD subscription (no trial offer): the row must not promise a trial.
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body))
            .assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body_no_trial))
            .assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_RESTORE).performScrollTo().performClick()
        composeRule.runOnIdle { check(restoreClicks == 1) { "expected 1 restore click, got $restoreClicks" } }
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
            ownership = Ownership(),
            restoreInProgress = true,
        )

        check(!loaded.iapEnabled) { "IAP buy must be disabled during a restore" }
        check(!loaded.subscriptionEnabled) { "Subscription buy must be disabled during a restore" }

        // Same offers without a running restore: both buys are available.
        val idle = toLoadedState(
            iap = SkuDetails(OurSku.Iap.PRO_UPGRADE, iapDetails),
            sub = SkuDetails(OurSku.Sub.PRO_UPGRADE, subDetails),
            ownership = Ownership(),
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
    fun `offer copy promises the trial only when Play returned the trial offer`() {
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

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body_no_trial))
            .assertCountEquals(0)
    }

    private fun ownedState(ownership: Ownership, verificationInProgress: Boolean = false) =
        GplayUpgradeUiState.Loaded(
            subscriptionAction = SubscriptionAction.UNAVAILABLE,
            subscriptionEnabled = false,
            subscriptionPrice = null,
            iapEnabled = !ownership.hasIap,
            iapPrice = "$24.99",
            ownership = ownership,
            verificationInProgress = verificationInProgress,
        )

    @Test
    fun `renewing subscription owner sees a locked one-time offer and management`() {
        var iapClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = ownedState(Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true))),
                onIap = { iapClicks++ },
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_MANAGE_SUB).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_sub_renewing_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText("${context.getString(CommonR.string.app_name)} ${context.getString(R.string.app_name_upgrade_postfix)}").assertCountEquals(1)
        // The congrats hero names the variant.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_OWNED_HERO).assertCountEquals(1)
        composeRule.onAllNodesWithText(appNameWithPostfixedHeroBody(R.string.upgrade_screen_owned_hero_sub_body))
            .assertCountEquals(1)
        // The switch path is a visible LOCKED offer: present, disabled, with the unlock condition.
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_iap_locked_note)).assertCountEquals(1)
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).assertIsNotEnabled()
        composeRule.runOnIdle { check(iapClicks == 0) { "locked offer must not be clickable" } }
        // No acquisition upsell copy anywhere on the ownership screen.
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_offers_title)).assertCountEquals(0)
        // Restore stays available in every ownership state: it reconciles entitlements, not upsell.
        // Framed as a status re-check; support is only offered by the failed-restore dialog.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_restore_status_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_contact_support_action))
            .assertCountEquals(0)
    }

    @Test
    fun `non-renewing subscription owner can buy the one-time upgrade`() {
        var iapClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = ownedState(Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false))),
                onIap = { iapClicks++ },
            )
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_MANAGE_SUB).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_sub_not_renewing_body)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_iap_purchase_note)).assertCountEquals(1)
        // The offer is unlocked — the locked-state note must be gone.
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_iap_locked_note)).assertCountEquals(0)
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).performScrollTo().performClick()
        composeRule.runOnIdle { check(iapClicks == 1) { "expected 1 iap click, got $iapClicks" } }
    }

    @Test
    fun `one-time owner sees owned status without purchase options`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(uiState = ownedState(Ownership(hasIap = true)))
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_OWNED_IAP).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_IAP).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_MANAGE_SUB).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_iap_body)).assertCountEquals(1)
        // The hero names the permanent purchase as the unlock, never the subscription variant.
        composeRule.onAllNodesWithText(appNameWithPostfixedHeroBody(R.string.upgrade_screen_owned_hero_iap_body))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(appNameWithPostfixedHeroBody(R.string.upgrade_screen_owned_hero_sub_body))
            .assertCountEquals(0)
    }

    @Test
    fun `owning both with a renewing subscription shows the renewal warning`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = ownedState(
                    Ownership(hasIap = true, subscription = SubscriptionOwnership(isAutoRenewing = true)),
                ),
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_owned_both_renewing_warning)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_MANAGE_SUB).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_IAP).assertCountEquals(0)
    }

    private fun graceState(showDiagnostics: Boolean) = GplayUpgradeUiState.Loaded(
        subscriptionAction = SubscriptionAction.STANDARD,
        subscriptionEnabled = true,
        subscriptionPrice = "$12.99",
        iapEnabled = true,
        iapPrice = "$24.99",
        grace = GraceHint(showDiagnostics = showDiagnostics),
    )

    @Test
    fun `quiet grace stage confirms pro without diagnostics or offers`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(uiState = graceState(showDiagnostics = false))
        }

        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_GRACE).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_grace_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_grace_body_short)).assertCountEquals(1)
        // "Confirming…" is backed by motion during the quiet stage; the mascot stays cheerful.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_GRACE_SPINNER).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.MASCOT_HAPPY).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.MASCOT_GRUMPY).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_GRACE_RESTORE).assertCountEquals(0)
        // The grace card owns restore via its two-stage disclosure — the generic restore section
        // must not undercut the calm quiet stage with its own restore CTA.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_RESTORE).assertCountEquals(0)
        // Grace users are still Pro: neutral status title, not the acquisition pitch title.
        composeRule.onAllNodesWithText("${context.getString(CommonR.string.app_name)} ${context.getString(R.string.app_name_upgrade_postfix)}").assertCountEquals(1)
        // A young episode is treated as a blip: calm status only — no offers, no sales pitch.
        // The offers return with the aged (diagnostics) stage.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_IAP).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(0)
    }

    @Test
    fun `grace restore action is disabled while a restore runs`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(uiState = graceState(showDiagnostics = true).copy(restoreInProgress = true))
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_GRACE_RESTORE).assertIsNotEnabled()
    }

    @Test
    fun `ownership buy button is paused while a restore runs`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = ownedState(Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)))
                    .copy(restoreInProgress = true),
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).assertIsNotEnabled()
    }

    @Test
    fun `aged grace stage shows diagnostics with an inline restore action`() {
        var restoreClicks = 0
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = graceState(showDiagnostics = true),
                onRestore = { restoreClicks++ },
            )
        }

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_grace_body)).assertCountEquals(1)
        // The aged copy asks the user to act — no spinner contradicting the restore CTA, and the
        // mascot switches to the grumpy "needs your attention" face.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_GRACE_SPINNER).assertCountEquals(0)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.MASCOT_GRUMPY).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.MASCOT_HAPPY).assertCountEquals(0)
        // The aged episode is treated as likely-permanent: the offers come back so an expired
        // subscriber can switch without waiting out the full grace window. Still no sales pitch.
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION).assertCountEquals(1)
        composeRule.onAllNodesWithTag(UpgradeScreenTags.GPLAY_IAP).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_preamble)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_benefits_title)).assertCountEquals(0)
        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_GRACE_RESTORE).performScrollTo().performClick()
        composeRule.runOnIdle { check(restoreClicks == 1) { "expected 1 restore click, got $restoreClicks" } }
    }

    @Test
    fun `ownership buy button is disabled while verification is running`() {
        composeRule.setUpgradeContent {
            UpgradeScreen(
                uiState = ownedState(
                    Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
                    verificationInProgress = true,
                ),
            )
        }

        composeRule.onNodeWithTag(UpgradeScreenTags.GPLAY_IAP).assertIsNotEnabled()
    }

    @Test
    fun `offer copy drops the trial promise when only the base offer is available`() {
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

        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body_no_trial))
            .assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.upgrade_screen_subscription_offer_body)).assertCountEquals(0)
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
