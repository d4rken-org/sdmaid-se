package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

// The acquisition offers box: header, offer rows, "or" divider, parity footnote. Own file so the
// box can be iterated via the previews below.
@Composable
internal fun LoadedOffers(
    uiState: GplayUpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.ACTIONS),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UpgradeSectionHeader(
            title = stringResource(R.string.upgrade_screen_offers_title),
            icon = Icons.TwoTone.Stars,
        )

        val subscriptionText = stringResource(
            when (uiState.subscriptionAction) {
                SubscriptionAction.TRIAL -> R.string.upgrade_screen_subscription_trial_action
                SubscriptionAction.STANDARD,
                SubscriptionAction.UNAVAILABLE,
                    -> R.string.upgrade_screen_subscription_action
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        UpgradeOfferRow(
            title = stringResource(R.string.upgrade_screen_subscription_offer_title),
            price = uiState.subscriptionPrice,
            // Only promise the trial when Play actually returned the trial offer.
            hint = stringResource(
                if (uiState.subscriptionAction == SubscriptionAction.TRIAL) {
                    R.string.upgrade_screen_subscription_offer_body
                } else {
                    R.string.upgrade_screen_subscription_offer_body_no_trial
                }
            ),
        ) {
            Button(
                onClick = when (uiState.subscriptionAction) {
                    SubscriptionAction.TRIAL -> onSubscriptionTrial
                    SubscriptionAction.STANDARD,
                    SubscriptionAction.UNAVAILABLE,
                        -> onSubscription
                },
                // Also locked during IAP verification: two concurrent billing launches must not race.
                enabled = uiState.subscriptionEnabled && !uiState.verificationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION),
            ) {
                Text(subscriptionText)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.upgrade_screen_offers_or),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))

        UpgradeOfferRow(
            title = stringResource(R.string.upgrade_screen_iap_offer_title),
            price = uiState.iapPrice,
            hint = stringResource(R.string.upgrade_screen_iap_offer_body),
        ) {
            OutlinedButton(
                onClick = onIap,
                enabled = uiState.iapEnabled && !uiState.verificationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_IAP),
            ) {
                if (uiState.verificationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.upgrade_screen_iap_action))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        UpgradeHintText(text = stringResource(R.string.upgrade_screen_offers_body))
    }
}

// Title and price share one line ("·"-joined in code: direction-neutral punctuation, not
// translatable copy), terms follow as body text, then the action — the terms must not repeat
// the button label.
@Composable
internal fun UpgradeOfferRow(
    title: String,
    price: String?,
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = listOfNotNull(title, price).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        hint?.let { UpgradeSectionBody(text = it) }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

private fun previewLoadedOffersState(
    subscriptionAction: SubscriptionAction = SubscriptionAction.TRIAL,
) = GplayUpgradeUiState.Loaded(
    subscriptionAction = subscriptionAction,
    subscriptionEnabled = true,
    subscriptionPrice = "$12.99",
    iapEnabled = true,
    iapPrice = "$24.99",
)

@Preview2
@Composable
private fun LoadedOffersPreview() {
    PreviewWrapper {
        // Inside the real container so spacing and colors match the device.
        UpgradeActionCard {
            LoadedOffers(
                uiState = previewLoadedOffersState(),
                onIap = {},
                onSubscription = {},
                onSubscriptionTrial = {},
            )
        }
    }
}

@Preview2
@Composable
private fun LoadedOffersNoTrialPreview() {
    PreviewWrapper {
        UpgradeActionCard {
            LoadedOffers(
                uiState = previewLoadedOffersState(subscriptionAction = SubscriptionAction.STANDARD),
                onIap = {},
                onSubscription = {},
                onSubscriptionTrial = {},
            )
        }
    }
}
