package eu.darken.sdmse.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.upgrade.ui.GplayUpgradeUiState
import eu.darken.sdmse.common.upgrade.ui.Ownership
import eu.darken.sdmse.common.upgrade.ui.SubscriptionAction
import eu.darken.sdmse.common.upgrade.ui.SubscriptionOwnership
import eu.darken.sdmse.common.upgrade.ui.UpgradeScreen

// Renders for Crowdin's translator context, uploaded by the android-translation plugin. Unlike
// PlayStoreLocales these render en-US only: auto-tagging matches the source language, so the other
// locales would be uploads nobody reads. copy_screenshots.sh ignores these names.
@Preview(locale = "en", name = "en-US", device = DS)
private annotation class CrowdinLocale

@PreviewTest
@CrowdinLocale
@Composable
fun GplayUpgradeAcquisition() {
    PreviewWrapper {
        UpgradeScreen(
            uiState = GplayUpgradeUiState.Loaded(
                subscriptionAction = SubscriptionAction.TRIAL,
                subscriptionEnabled = true,
                subscriptionPrice = "€9.99",
                iapEnabled = true,
                iapPrice = "€12.99",
            ),
        )
    }
}

@PreviewTest
@CrowdinLocale
@Composable
fun GplayUpgradeOwnedSub() {
    PreviewWrapper {
        UpgradeScreen(
            uiState = GplayUpgradeUiState.Loaded(
                subscriptionAction = SubscriptionAction.STANDARD,
                subscriptionEnabled = false,
                subscriptionPrice = "€9.99",
                // toLoadedState computes this from the offer, not from ownership: a renewing
                // subscriber still sees an enabled IAP flag. The switch offer is greyed out by
                // isAutoRenewing further down, which is what this render is here to show.
                iapEnabled = true,
                iapPrice = "€12.99",
                ownership = Ownership(
                    subscription = SubscriptionOwnership(isAutoRenewing = true),
                ),
            ),
        )
    }
}
