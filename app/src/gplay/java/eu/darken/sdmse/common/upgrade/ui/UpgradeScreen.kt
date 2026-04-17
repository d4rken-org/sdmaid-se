package eu.darken.sdmse.common.upgrade.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.Payments
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.R as CommonR
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.upgrade.core.OurSku

@Composable
fun UpgradeScreenHost(
    vm: UpgradeViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var showRestoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreFailed -> showRestoreFailed = true
            }
        }
    }

    if (showRestoreFailed) {
        val purchaseMsg = stringResource(R.string.upgrade_screen_restore_purchase_message)
        val troubleshootingMsg = stringResource(R.string.upgrade_screen_restore_troubleshooting_msg)
        val syncHint = stringResource(R.string.upgrade_screen_restore_sync_patience_hint)
        val multiAccountHint = stringResource(R.string.upgrade_screen_restore_multiaccount_hint)
        val message = "$purchaseMsg\n\n$troubleshootingMsg\n\n$syncHint\n\n$multiAccountHint"
        AlertDialog(
            onDismissRequest = { showRestoreFailed = false },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showRestoreFailed = false }) {
                    Text(stringResource(CommonR.string.general_dismiss_action))
                }
            },
        )
    }

    val uiState by vm.state.collectAsStateWithLifecycle()

    UpgradeScreen(
        uiState = uiState,
        onIap = { activity?.let { vm.onGoIap(it) } },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onRestore = vm::restorePurchase,
        onNavigateUp = vm::navUp,
    )
}

@Composable
internal fun UpgradeScreen(
    uiState: GplayUpgradeUiState = GplayUpgradeUiState.Loading,
    onIap: () -> Unit = {},
    onSubscription: () -> Unit = {},
    onSubscriptionTrial: () -> Unit = {},
    onRestore: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    UpgradeScreenScaffold(
        titleRes = R.string.upgrade_screen_title,
        onNavigateUp = onNavigateUp,
    ) { paddingValues ->
        UpgradeScreenContent(
            paddingValues = paddingValues,
            contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp),
        ) {
            UpgradeHeader(
                mascotSize = 88.dp,
            )

            UpgradePreambleCard(
                text = stringResource(R.string.upgrade_screen_preamble),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )

            UpgradeSectionCard(
                title = stringResource(R.string.upgrade_screen_benefits_title),
                icon = Icons.TwoTone.AutoAwesome,
            ) {
                UpgradeFeatureList(text = stringResource(R.string.upgrade_screen_benefits_body))
            }

            UpgradeSectionCard(
                title = stringResource(R.string.upgrade_screen_how_title),
                icon = Icons.TwoTone.Payments,
            ) {
                UpgradeSectionBody(text = stringResource(R.string.upgrade_screen_how_body))
            }

            UpgradeActionCard {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "upgrade-offers",
                ) { state ->
                    when (state) {
                        GplayUpgradeUiState.Loading -> UpgradeLoadingBlock()
                        is GplayUpgradeUiState.Unavailable -> UpgradeInlineStateCard(
                            title = stringResource(R.string.upgrades_gplay_unavailable_error_title),
                            body = stringResource(R.string.upgrade_screen_offers_unavailable_message),
                            icon = Icons.TwoTone.WarningAmber,
                        )
                        is GplayUpgradeUiState.Loaded -> LoadedOffers(
                            uiState = state,
                            onIap = onIap,
                            onSubscription = onSubscription,
                            onSubscriptionTrial = onSubscriptionTrial,
                            onRestore = onRestore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadedOffers(
    uiState: GplayUpgradeUiState.Loaded,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UpgradeScreenTags.ACTIONS),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val subscriptionText = stringResource(
            when (uiState.subscriptionAction) {
                SubscriptionAction.TRIAL -> R.string.upgrade_screen_subscription_trial_action
                SubscriptionAction.STANDARD,
                SubscriptionAction.UNAVAILABLE,
                -> R.string.upgrade_screen_subscription_action
            }
        )

        UpgradeOfferCard(
            title = stringResource(R.string.upgrade_screen_subscription_offer_title),
            price = uiState.subscriptionPrice,
            supportingText = uiState.subscriptionPrice?.let {
                stringResource(R.string.upgrade_screen_subscription_action_hint, it)
            },
            emphasized = uiState.subscriptionAction != SubscriptionAction.UNAVAILABLE,
            badgeText = if (uiState.subscriptionAction != SubscriptionAction.UNAVAILABLE) {
                stringResource(R.string.upgrade_screen_offer_recommended)
            } else {
                null
            },
        ) {
            Button(
                onClick = when (uiState.subscriptionAction) {
                    SubscriptionAction.TRIAL -> onSubscriptionTrial
                    SubscriptionAction.STANDARD,
                    SubscriptionAction.UNAVAILABLE,
                    -> onSubscription
                },
                enabled = uiState.subscriptionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_SUBSCRIPTION),
            ) {
                Text(subscriptionText)
            }
        }

        UpgradeOfferCard(
            title = stringResource(R.string.upgrade_screen_iap_offer_title),
            price = uiState.iapPrice,
            supportingText = uiState.iapPrice?.let {
                stringResource(R.string.upgrade_screen_iap_action_hint, it)
            },
        ) {
            OutlinedButton(
                onClick = onIap,
                enabled = uiState.iapEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UpgradeScreenTags.GPLAY_IAP),
            ) {
                Text(stringResource(R.string.upgrade_screen_iap_action))
            }
        }

        TextButton(
            onClick = onRestore,
            modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_RESTORE),
        ) {
            Text(stringResource(R.string.upgrade_screen_restore_purchase_action))
        }
    }
}

internal sealed interface GplayUpgradeUiState {
    data object Loading : GplayUpgradeUiState

    data class Unavailable(
        val error: Throwable,
    ) : GplayUpgradeUiState

    data class Loaded(
        val subscriptionAction: SubscriptionAction,
        val subscriptionEnabled: Boolean,
        val subscriptionPrice: String?,
        val iapEnabled: Boolean,
        val iapPrice: String?,
    ) : GplayUpgradeUiState
}

internal enum class SubscriptionAction {
    TRIAL,
    STANDARD,
    UNAVAILABLE,
}

internal fun toLoadedState(
    iap: eu.darken.sdmse.common.upgrade.core.billing.SkuDetails?,
    sub: eu.darken.sdmse.common.upgrade.core.billing.SkuDetails?,
    hasIap: Boolean,
    hasSub: Boolean,
): GplayUpgradeUiState.Loaded {
    val iapOffer = iap?.details?.oneTimePurchaseOfferDetails
    val subOffer = sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
        OurSku.Sub.PRO_UPGRADE.BASE_OFFER.matches(offer)
    }
    val subOfferTrial = sub?.details?.subscriptionOfferDetails?.singleOrNull { offer ->
        OurSku.Sub.PRO_UPGRADE.TRIAL_OFFER.matches(offer)
    }

    return GplayUpgradeUiState.Loaded(
        subscriptionAction = when {
            subOfferTrial != null -> SubscriptionAction.TRIAL
            subOffer != null -> SubscriptionAction.STANDARD
            else -> SubscriptionAction.UNAVAILABLE
        },
        subscriptionEnabled = (subOffer != null || subOfferTrial != null) && !hasSub,
        subscriptionPrice = subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
        iapEnabled = iapOffer != null && !hasIap,
        iapPrice = iapOffer?.formattedPrice,
    )
}

@Preview2
@Composable
private fun UpgradeScreenLoadingPreview() {
    PreviewWrapper {
        UpgradeScreen(uiState = GplayUpgradeUiState.Loading)
    }
}

@Preview2
@Composable
private fun UpgradeScreenLoadedPreview() {
    PreviewWrapper {
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
}

@Preview2
@Composable
private fun UpgradeScreenUnavailablePreview() {
    PreviewWrapper {
        UpgradeScreen(
            uiState = GplayUpgradeUiState.Unavailable(
                error = RuntimeException("Google Play unavailable"),
            ),
        )
    }
}
