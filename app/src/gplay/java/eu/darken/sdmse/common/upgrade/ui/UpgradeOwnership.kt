package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper

// Ownership presentation for users who already own a Pro entitlement. Progressive disclosure:
// a renewing subscriber only sees status + management — the one-time purchase appears once the
// subscription is no longer set to renew, so buying it can't stack with an upcoming renewal.
@Composable
internal fun UpgradeOwnershipContent(
    uiState: GplayUpgradeUiState.Loaded,
    onIap: () -> Unit,
    onManageSubscription: () -> Unit,
    onRestore: () -> Unit,
) {
    val ownership = uiState.ownership
    val subscription = ownership.subscription

    if (ownership.hasIap) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_owned_iap_title),
            icon = Icons.TwoTone.Verified,
            modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_OWNED_IAP),
        ) {
            UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_owned_iap_body))
        }
    }

    if (subscription != null) {
        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_subscription_offer_title),
            icon = Icons.TwoTone.Autorenew,
            modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_OWNED_SUB),
        ) {
            UpgradeSectionBody(
                text = stringResource(
                    if (subscription.isAutoRenewing) R.string.upgrade_screen_owned_sub_renewing_body
                    else R.string.upgrade_screen_owned_sub_not_renewing_body
                ),
            )
            if (subscription.isAutoRenewing && ownership.hasIap) {
                Text(
                    text = stringResource(R.string.upgrade_screen_owned_both_renewing_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = onManageSubscription,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_MANAGE_SUB),
            ) {
                Text(stringResource(R.string.upgrade_screen_manage_subscription_action))
            }
        }
    }

    if (subscription?.isAutoRenewing == false && !ownership.hasIap) {
        UpgradeOfferCard(
            title = stringResource(R.string.upgrade_screen_iap_offer_title),
            price = uiState.iapPrice,
            supportingText = stringResource(R.string.upgrade_screen_owned_iap_purchase_note),
        ) {
            Button(
                onClick = onIap,
                // Not gated on iapEnabled: prices may have failed to load while the purchase
                // itself would work (the billing flow re-queries product details on launch).
                enabled = !uiState.verificationInProgress,
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
    }

    TextButton(
        onClick = onRestore,
        enabled = !uiState.restoreInProgress,
        modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_RESTORE),
    ) {
        Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
    }
}

private fun previewLoadedState(ownership: Ownership) = GplayUpgradeUiState.Loaded(
    subscriptionAction = SubscriptionAction.UNAVAILABLE,
    subscriptionEnabled = false,
    subscriptionPrice = "$12.99",
    iapEnabled = !ownership.hasIap,
    iapPrice = "$24.99",
    ownership = ownership,
)

@Preview2
@Composable
private fun UpgradeOwnershipRenewingSubPreview() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            UpgradeOwnershipContent(
                uiState = previewLoadedState(
                    Ownership(subscription = SubscriptionOwnership(isAutoRenewing = true)),
                ),
                onIap = {},
                onManageSubscription = {},
                onRestore = {},
            )
        }
    }
}

@Preview2
@Composable
private fun UpgradeOwnershipNonRenewingSubPreview() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            UpgradeOwnershipContent(
                uiState = previewLoadedState(
                    Ownership(subscription = SubscriptionOwnership(isAutoRenewing = false)),
                ),
                onIap = {},
                onManageSubscription = {},
                onRestore = {},
            )
        }
    }
}

@Preview2
@Composable
private fun UpgradeOwnershipIapPreview() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            UpgradeOwnershipContent(
                uiState = previewLoadedState(Ownership(hasIap = true)),
                onIap = {},
                onManageSubscription = {},
                onRestore = {},
            )
        }
    }
}

@Preview2
@Composable
private fun UpgradeOwnershipBothRenewingPreview() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            UpgradeOwnershipContent(
                uiState = previewLoadedState(
                    Ownership(hasIap = true, subscription = SubscriptionOwnership(isAutoRenewing = true)),
                ),
                onIap = {},
                onManageSubscription = {},
                onRestore = {},
            )
        }
    }
}
