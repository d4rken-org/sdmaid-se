package eu.darken.sdmse.common.upgrade.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.darken.sdmse.R
import eu.darken.sdmse.common.compose.dialog.SdmConfirmDialog
import eu.darken.sdmse.common.compose.dialog.SdmDialogAction
import eu.darken.sdmse.common.compose.preview.Preview2
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.error.ErrorEventHandler
import eu.darken.sdmse.common.navigation.NavigationEventHandler
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.R as CommonR

@Composable
fun UpgradeScreenHost(
    route: UpgradeRoute = UpgradeRoute(),
    vm: UpgradeViewModel = hiltViewModel(),
) {
    LaunchedEffect(route) { vm.bindRoute(route) }
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // No screen-level resume refresh: MainActivity already refreshes the upgrade repo on every
    // activity resume, which covers returning from Play after cancelling the subscription there.

    var showRestoreFailed by remember { mutableStateOf(false) }
    var showStillRenewing by remember { mutableStateOf(false) }
    var showCheckFailed by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreSucceeded -> Toast.makeText(
                    context,
                    context.getString(R.string.upgrade_screen_restore_success_message),
                    Toast.LENGTH_LONG,
                ).show()

                UpgradeEvents.RestoreFailed -> showRestoreFailed = true
                UpgradeEvents.SubscriptionStillRenewing -> showStillRenewing = true
                UpgradeEvents.SubscriptionCheckFailed -> showCheckFailed = true
            }
        }
    }

    if (showRestoreFailed) {
        // Leads with the just-happened live Play check (hedged: RestoreFailed also fires on
        // timeout). This dialog is the ONLY contact-support surface — escalation comes after an
        // empty restore, never before.
        val checkedMsg = stringResource(R.string.upgrade_screen_restore_checked_message)
        val multiAccountHint = stringResource(R.string.upgrade_screen_restore_multiaccount_hint)
        val syncHint = stringResource(R.string.upgrade_screen_restore_sync_patience_hint)
        val contactHint = stringResource(R.string.upgrade_screen_restore_contact_hint)
        val message = "$checkedMsg\n\n$multiAccountHint\n\n$syncHint\n\n$contactHint"
        SdmConfirmDialog(
            message = message,
            onDismissRequest = { showRestoreFailed = false },
            positive = SdmDialogAction(
                label = stringResource(R.string.upgrade_screen_contact_support_action),
                onClick = {
                    showRestoreFailed = false
                    vm.onContactSupport()
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_dismiss_action),
                onClick = { showRestoreFailed = false },
            ),
        )
    }

    if (showStillRenewing) {
        SdmConfirmDialog(
            title = stringResource(R.string.upgrade_screen_sub_still_renewing_title),
            message = stringResource(R.string.upgrade_screen_sub_still_renewing_message),
            onDismissRequest = { showStillRenewing = false },
            positive = SdmDialogAction(
                label = stringResource(R.string.upgrade_screen_manage_subscription_action),
                onClick = {
                    showStillRenewing = false
                    vm.onManageSubscription()
                },
            ),
            negative = SdmDialogAction(
                label = stringResource(CommonR.string.general_dismiss_action),
                onClick = { showStillRenewing = false },
            ),
        )
    }

    if (showCheckFailed) {
        SdmConfirmDialog(
            message = stringResource(R.string.upgrade_screen_sub_check_failed_message),
            onDismissRequest = { showCheckFailed = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_dismiss_action),
                onClick = { showCheckFailed = false },
            ),
        )
    }

    val uiState by vm.state.collectAsStateWithLifecycle()

    UpgradeScreen(
        uiState = uiState,
        onIap = { activity?.let { vm.onGoIap(it) } },
        onSubscription = { activity?.let { vm.onGoSubscription(it) } },
        onSubscriptionTrial = { activity?.let { vm.onGoSubscriptionTrial(it) } },
        onRestore = vm::restorePurchase,
        onManageSubscription = vm::onManageSubscription,
        onRetry = vm::retrySkuQuery,
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
    onManageSubscription: () -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateUp: () -> Unit = {},
) {
    // Owners get the ownership presentation: no acquisition upsell (pitch, benefits, offers box)
    // anywhere — the one-time purchase appears only as the ownership view's own switch offer,
    // locked while the subscription still renews.
    val loaded = uiState as? GplayUpgradeUiState.Loaded
    val ownedState = loaded?.takeIf { it.ownership.ownsAnything }

    UpgradeScreenScaffold(
        // Grace users are still Pro: they get the status title too — "Get SD Maid SE Pro" on the
        // status screen would contradict the rest of the app, which behaves upgraded. The postfix
        // is highlighted like the dashboard title does it.
        title = if (ownedState != null || loaded?.grace != null) {
            upgradeScreenTitle(upgraded = true)
        } else {
            AnnotatedString(stringResource(R.string.upgrade_screen_title))
        },
        onNavigateUp = onNavigateUp,
    ) { paddingValues ->
        UpgradeScreenContent(
            paddingValues = paddingValues,
            contentPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 32.dp),
        ) {
            if (ownedState == null) {
                // Owners get the mascot inside the congrats hero card instead. Once a grace
                // episode ages into the diagnostics stage, the mascot joins the mood: unimpressed
                // at Google Play, matching the setup card's "needs your attention" face. The young
                // episode keeps the happy face — its message is that nothing is wrong.
                UpgradeHeader(
                    mascotSize = 88.dp,
                    happy = loaded?.grace?.showDiagnostics != true,
                )
            }

            if (ownedState != null) {
                UpgradeOwnershipContent(
                    uiState = ownedState,
                    onIap = onIap,
                    onManageSubscription = onManageSubscription,
                    onRestore = onRestore,
                )
            } else {
                UpgradeAcquisitionContent(
                    uiState = uiState,
                    onIap = onIap,
                    onSubscription = onSubscription,
                    onSubscriptionTrial = onSubscriptionTrial,
                    onRestore = onRestore,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UpgradeAcquisitionContent(
    uiState: GplayUpgradeUiState,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
) {
    val loadedState = uiState as? GplayUpgradeUiState.Loaded
    val inGrace = loadedState?.grace != null
    loadedState?.grace?.let { grace ->
        UpgradeGraceCard(
            showDiagnostics = grace.showDiagnostics,
            onRestore = onRestore,
            restoreInProgress = loadedState.restoreInProgress,
        )
    }

    // Grace users never see the pitch (they are Pro, sales copy next to a "still active" card
    // reads as a contradiction), and the OFFERS follow the episode age — the client can't tell a
    // blip from a lapsed purchase, so time is the arbiter: a young episode (likely self-healing
    // blip) shows calm status only, an aged one (likely really gone) adds restore AND the offers,
    // so an expired subscriber can switch without waiting out the full grace window.
    if (!inGrace) {
        UpgradePreambleCard(
            text = stringResource(R.string.upgrade_screen_preamble),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        )

        if (uiState is GplayUpgradeUiState.Loaded && uiState.wasPreviouslyPro) {
            // The targeted returning-buyer nudge: prominent placement and emphasis, and the ONLY
            // restore affordance on the screen — a second one below would make the screen feel
            // uncertain about its own advice.
            UpgradeRestoreSection(
                title = stringResource(R.string.upgrade_screen_restore_banner_title),
                body = stringResource(R.string.upgrade_screen_restore_banner_body),
                onRestore = onRestore,
                modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER),
                restoreInProgress = uiState.restoreInProgress,
                emphasized = true,
                restoreTag = UpgradeScreenTags.GPLAY_RESTORE_BANNER_ACTION,
            )
        }

        UpgradeSectionCard(
            title = stringResource(R.string.upgrade_screen_benefits_title),
            icon = Icons.TwoTone.AutoAwesome,
        ) {
            UpgradeFeatureList(text = stringResource(R.string.upgrade_screen_benefits_body))
        }
    }

    // During a YOUNG grace episode the offers box is hidden: likely a blip, and offers next to
    // "Pro is still active" would contradict it. An aged episode brings them back.
    if (!inGrace || loadedState?.grace?.showDiagnostics == true) {
        UpgradeOffersBox(
            uiState = uiState,
            onIap = onIap,
            onSubscription = onSubscription,
            onSubscriptionTrial = onSubscriptionTrial,
            onRetry = onRetry,
        )
    }

    // Restore is account reconciliation, not an offer — its own described section, after the
    // offers. Only for plain acquisition: returning buyers get the emphasized section up top
    // instead, and grace users' restore is owned by the grace card's two-stage disclosure.
    val loadedForRestore = uiState as? GplayUpgradeUiState.Loaded
    if (loadedForRestore != null && !loadedForRestore.wasPreviouslyPro && loadedForRestore.grace == null) {
        UpgradeRestoreSection(
            title = stringResource(R.string.upgrade_screen_restore_banner_title),
            body = stringResource(R.string.upgrade_screen_restore_body),
            onRestore = onRestore,
            restoreInProgress = loadedForRestore.restoreInProgress,
        )
    }
}

// All purchase framing lives inside the offers box (LoadedOffers) — no separate explainer card.
// Each state brings its OWN container: the error state is a full card itself, wrapping it in the
// action card produced a card-in-card.
@Composable
private fun UpgradeOffersBox(
    uiState: GplayUpgradeUiState,
    onIap: () -> Unit,
    onSubscription: () -> Unit,
    onSubscriptionTrial: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedContent(
        targetState = uiState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "upgrade-offers",
    ) { state ->
        when (state) {
            GplayUpgradeUiState.Loading -> UpgradeActionCard { UpgradeLoadingBlock() }
            is GplayUpgradeUiState.Unavailable -> UpgradeInlineStateCard(
                title = stringResource(R.string.upgrades_gplay_unavailable_error_title),
                body = stringResource(R.string.upgrade_screen_offers_unavailable_message),
                icon = Icons.TwoTone.WarningAmber,
            ) {
                // Play can be slow rather than broken (cold store, first sign-in): let
                // the user re-run the offer queries instead of leaving a dead screen.
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.GPLAY_RETRY),
                ) {
                    Text(stringResource(CommonR.string.general_retry_action))
                }
            }
            is GplayUpgradeUiState.Loaded -> UpgradeActionCard {
                LoadedOffers(
                    uiState = state,
                    onIap = onIap,
                    onSubscription = onSubscription,
                    onSubscriptionTrial = onSubscriptionTrial,
                )
            }
        }
    }
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
private fun UpgradeScreenReturningBuyerPreview() {
    PreviewWrapper {
        UpgradeScreen(
            uiState = GplayUpgradeUiState.Loaded(
                subscriptionAction = SubscriptionAction.STANDARD,
                subscriptionEnabled = true,
                subscriptionPrice = "$12.99",
                iapEnabled = true,
                iapPrice = "$24.99",
                wasPreviouslyPro = true,
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
