package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Autorenew
import androidx.compose.material.icons.twotone.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.R as CommonR

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

    UpgradeOwnedHero(ownership = ownership)

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
            if (subscription.isAutoRenewing && !ownership.hasIap) {
                // The switch path must be discoverable: without this, a renewing subscriber has no
                // way to learn that the one-time purchase appears after cancelling.
                UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_owned_sub_switch_hint))
            }
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
        UpgradeActionCard {
            UpgradeOfferRow(
                title = stringResource(R.string.upgrade_screen_iap_offer_title),
                price = uiState.iapPrice,
                hint = stringResource(R.string.upgrade_screen_owned_iap_purchase_note),
            ) {
                Button(
                    onClick = onIap,
                    // Not gated on iapEnabled: prices may have failed to load while the purchase
                    // itself would work (the billing flow re-queries details on launch).
                    enabled = !uiState.verificationInProgress && !uiState.restoreInProgress,
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
    }

    // Framed as a status re-check; support is offered by the failed-restore dialog only.
    UpgradeRestoreSection(
        title = stringResource(R.string.upgrade_screen_restore_status_title),
        body = stringResource(R.string.upgrade_screen_restore_status_body),
        onRestore = onRestore,
        restoreInProgress = uiState.restoreInProgress,
    )
}

// The "you have it" moment: mascot and congrats in one hero card at the top of the status
// screen, with the variant (subscription vs one-time) spelled out. The per-purchase cards below
// carry details and actions.
@Composable
private fun UpgradeOwnedHero(
    ownership: Ownership,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.GPLAY_OWNED_HERO),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpgradeMascot(size = 72.dp)
            Text(
                text = stringResource(
                    R.string.upgrade_screen_owned_hero_title,
                    "${stringResource(CommonR.string.app_name)} ${stringResource(R.string.app_name_upgrade_postfix)}",
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                // The permanent purchase is the meaningful one when both are owned.
                text = stringResource(
                    if (ownership.hasIap) R.string.upgrade_screen_owned_hero_iap_body
                    else R.string.upgrade_screen_owned_hero_sub_body
                ),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Shown on the acquisition view while Pro is active purely via the local grace window. Calm
// reassurance styling, not a warning: the user has lost nothing (yet). Stage 1 confirms Pro is
// intact; stage 2 (after the episode aged past the threshold) explains and offers restore.
@Composable
internal fun UpgradeGraceCard(
    showDiagnostics: Boolean,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    restoreInProgress: Boolean = false,
) {
    UpgradeSectionCard(
        title = stringResource(R.string.upgrade_screen_grace_title),
        icon = Icons.TwoTone.Verified,
        modifier = modifier.testTag(UpgradeScreenTags.GPLAY_GRACE),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = stringResource(
                if (showDiagnostics) R.string.upgrade_screen_grace_body
                else R.string.upgrade_screen_grace_body_short
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (showDiagnostics) {
            Button(
                onClick = onRestore,
                enabled = !restoreInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_GRACE_RESTORE),
            ) {
                if (restoreInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
            }
        }
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
private fun UpgradeGraceCardQuietPreview() {
    PreviewWrapper {
        UpgradeGraceCard(
            showDiagnostics = false,
            onRestore = {},
        )
    }
}

@Preview2
@Composable
private fun UpgradeGraceCardDiagnosticsPreview() {
    PreviewWrapper {
        UpgradeGraceCard(
            showDiagnostics = true,
            onRestore = {},
        )
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
