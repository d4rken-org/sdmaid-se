package eu.darken.sdmse.common.upgrade.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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

    // MainActivity's per-resume refresh only covers the entitlement, never the screen-local SKU
    // query — so a transient Play outage would leave the retry card up until it's tapped by hand.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    // rememberSaveable, not remember: these are driven by one-shot events that are already consumed
    // from the flow, so a rotation while a dialog is up would drop it for good.
    var showRestoreFailed by rememberSaveable { mutableStateOf(false) }
    var showRestoreInconclusive by rememberSaveable { mutableStateOf(false) }
    var showStillRenewing by rememberSaveable { mutableStateOf(false) }
    var showCheckFailed by rememberSaveable { mutableStateOf(false) }
    var showPurchasePending by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                UpgradeEvents.RestoreSucceeded -> Toast.makeText(
                    context,
                    context.getString(R.string.upgrade_screen_restore_success_message),
                    Toast.LENGTH_LONG,
                ).show()

                UpgradeEvents.RestoreFailed -> showRestoreFailed = true
                UpgradeEvents.RestoreInconclusive -> showRestoreInconclusive = true
                UpgradeEvents.SubscriptionStillRenewing -> showStillRenewing = true
                UpgradeEvents.PurchaseCheckFailed -> showCheckFailed = true
                UpgradeEvents.PurchasePending -> showPurchasePending = true
            }
        }
    }

    if (showRestoreFailed) {
        RestoreFailedDialog(
            onContactSupport = {
                showRestoreFailed = false
                vm.onContactSupport()
            },
            onDismiss = { showRestoreFailed = false },
        )
    }

    if (showRestoreInconclusive) {
        RestoreInconclusiveDialog(
            onRetry = {
                showRestoreInconclusive = false
                vm.restorePurchase()
            },
            onDismiss = { showRestoreInconclusive = false },
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
            message = stringResource(R.string.upgrade_screen_purchase_check_failed_message),
            onDismissRequest = { showCheckFailed = false },
            positive = SdmDialogAction(
                label = stringResource(CommonR.string.general_dismiss_action),
                onClick = { showCheckFailed = false },
            ),
        )
    }

    if (showPurchasePending) {
        PurchasePendingDialog(onDismiss = { showPurchasePending = false })
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

/**
 * Shown when Play answered and no purchase was found. Leads with the just-happened live check,
 * which is literally true here: non-answers route to [RestoreInconclusiveDialog] instead. This is
 * the ONLY contact-support surface — escalation comes after an empty restore, never before.
 */
@Composable
internal fun RestoreFailedDialog(
    onContactSupport: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val checkedMsg = stringResource(R.string.upgrade_screen_restore_checked_message)
    val multiAccountHint = stringResource(R.string.upgrade_screen_restore_multiaccount_hint)
    val syncHint = stringResource(R.string.upgrade_screen_restore_sync_patience_hint)
    val contactHint = stringResource(R.string.upgrade_screen_restore_contact_hint)
    SdmConfirmDialog(
        message = "$checkedMsg\n\n$multiAccountHint\n\n$syncHint\n\n$contactHint",
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(R.string.upgrade_screen_contact_support_action),
            onClick = onContactSupport,
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_dismiss_action),
            onClick = onDismiss,
        ),
    )
}

/**
 * Shown when Play is still processing a payment. Purely informational: there is nothing to fix, no
 * purchase to restore and no support case — the entitlement arrives on its own once the payment
 * clears, so the dialog offers only a dismiss.
 */
@Composable
internal fun PurchasePendingDialog(
    onDismiss: () -> Unit = {},
) {
    SdmConfirmDialog(
        message = stringResource(R.string.upgrade_screen_pending_dialog_message),
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_dismiss_action),
            onClick = onDismiss,
        ),
    )
}

@Preview2
@Composable
private fun PurchasePendingDialogPreview() {
    PreviewWrapper {
        PurchasePendingDialog()
    }
}

/**
 * Shown when the restore never got an answer (timeout, or a Play error absorbed by grace). Carries
 * no multi-account hint and no contact-support action: nothing was established, so both would be
 * premature. Retry is the useful move, and `restorePurchase()` is single-flight.
 */
@Composable
internal fun RestoreInconclusiveDialog(
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val inconclusiveMsg = stringResource(R.string.upgrade_screen_restore_inconclusive_message)
    val syncHint = stringResource(R.string.upgrade_screen_restore_sync_patience_hint)
    SdmConfirmDialog(
        message = "$inconclusiveMsg\n\n$syncHint",
        onDismissRequest = onDismiss,
        positive = SdmDialogAction(
            label = stringResource(CommonR.string.general_retry_action),
            onClick = onRetry,
        ),
        negative = SdmDialogAction(
            label = stringResource(CommonR.string.general_dismiss_action),
            onClick = onDismiss,
        ),
    )
}

// The acquisition pitch inserts the SAME composed brand the status title uses, postfix colored —
// one brand rendering for both. Word-order-proof: the brand is spliced into the TRANSLATED pattern,
// so Android's formatter owns placeholder semantics (numbering, reordering, escaping).
@Composable
private fun upgradeAcquisitionTitle(): AnnotatedString = spliceBrandTitle(
    formatted = stringResource(R.string.upgrade_screen_title_template, BRAND_TITLE_MARKER),
    brand = upgradeScreenTitle(upgraded = true),
)

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
        // Grace users are still Pro: they get the bare status title — "Get SD Maid SE Pro" on the
        // status screen would contradict the rest of the app, which behaves upgraded. Acquisition
        // wraps that same brand in the pitch sentence. Either way the postfix is highlighted like
        // the dashboard title does it.
        title = if (ownedState != null || loaded?.grace != null) {
            upgradeScreenTitle(upgraded = true)
        } else {
            upgradeAcquisitionTitle()
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
                if (loaded?.grace != null) {
                    // Grace users never see the preamble (sales copy contradicts "still active"),
                    // so there is nothing to pair the mascot with — it stays a standalone header
                    // above the grace card.
                    UpgradeHeader(
                        mascotSize = 88.dp,
                        happy = loaded.grace.showDiagnostics != true,
                    )
                } else {
                    UpgradeHeroCard(
                        text = stringResource(R.string.upgrade_screen_preamble),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }

            // Above the ownership/acquisition split, because a pending payment cuts across it: the
            // buyer waiting for their first Pro purchase, the owner switching products and the
            // grace user (whose offers box is hidden entirely) all need it, and it is the reason
            // their purchase buttons are locked.
            if (loaded?.hasPendingPurchase == true) PendingPurchaseCard()

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
            busy = loadedState.busy,
        )
    }

    // Grace users never see the pitch (they are Pro, sales copy next to a "still active" card
    // reads as a contradiction), and the OFFERS follow the episode age — the client can't tell a
    // blip from a lapsed purchase, so time is the arbiter: a young episode (likely self-healing
    // blip) shows calm status only, an aged one (likely really gone) adds restore AND the offers,
    // so an expired subscriber can switch without waiting out the full grace window.
    if (!inGrace) {
        // The preamble itself now lives in the hero card at the top of the screen, next to the
        // mascot — only the sections below it are conditional here.
        if (uiState is GplayUpgradeUiState.Loaded && uiState.wasPreviouslyPro) {
            // The targeted returning-buyer nudge: prominent placement and emphasis, and the ONLY
            // restore affordance on the screen — a second one below would make the screen feel
            // uncertain about its own advice.
            UpgradeRestoreSection(
                title = stringResource(R.string.upgrade_screen_restore_banner_title),
                body = stringResource(R.string.upgrade_screen_restore_banner_body),
                onRestore = onRestore,
                modifier = Modifier.testTag(UpgradeScreenTags.GPLAY_RESTORE_BANNER),
                busy = uiState.busy,
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
            busy = loadedForRestore.busy,
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
                title = stringResource(R.string.upgrade_screen_offers_unavailable_title),
                body = stringResource(R.string.upgrade_screen_offers_unavailable_message),
                icon = Icons.TwoTone.WarningAmber,
            ) {
                // Play can be slow rather than broken (cold store, first sign-in): let
                // the user re-run the offer queries instead of leaving a dead screen.
                // No reset needed: this composable unmounts the moment the state leaves Unavailable.
                var retryTapped by remember { mutableStateOf(false) }
                val retryEnabled = !retryTapped
                OutlinedButton(
                    // Guard inside the callback, not just via `enabled`: `enabled` only takes effect
                    // after recomposition, so two taps in the same frame would both fire.
                    onClick = {
                        if (!retryTapped) {
                            retryTapped = true
                            onRetry()
                        }
                    },
                    enabled = retryEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UpgradeScreenTags.GPLAY_RETRY),
                    // The button sits on the errorContainer card, so the default primary-on-surface
                    // outlined colors read as a foreign element with poor contrast.
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f),
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = if (retryEnabled) 1f else 0.1f),
                    ),
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

// The acquisition variant of the pending state: card above the offers, both buy buttons locked.
@Preview2
@Composable
private fun UpgradeScreenPendingPreview() {
    PreviewWrapper {
        UpgradeScreen(
            uiState = GplayUpgradeUiState.Loaded(
                subscriptionAction = SubscriptionAction.STANDARD,
                subscriptionEnabled = false,
                subscriptionPrice = "$12.99",
                iapEnabled = false,
                iapPrice = "$24.99",
                hasPendingPurchase = true,
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
