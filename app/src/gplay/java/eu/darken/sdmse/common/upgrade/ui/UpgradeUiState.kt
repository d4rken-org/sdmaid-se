package eu.darken.sdmse.common.upgrade.ui

import eu.darken.sdmse.common.upgrade.core.OurSku
import eu.darken.sdmse.common.upgrade.core.UpgradeRepoGplay
import eu.darken.sdmse.common.upgrade.core.billing.SkuDetails

// Render-state model for the gplay upgrade screen plus the pure mappers that build it. Kept apart
// from both the composables and the ViewModel: previews and tests construct these directly, and
// neither side should have to drag the other in for it.
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
        val ownership: Ownership = Ownership(),
        val grace: GraceHint? = null,
        val wasPreviouslyPro: Boolean = false,
        // A payment Google Play is still processing. SKU-agnostic on purpose: the card explains the
        // wait and both purchase actions lock, regardless of which product is pending.
        val hasPendingPurchase: Boolean = false,
        val busy: BusyOp? = null,
    ) : GplayUpgradeUiState
}

// The ONE entitlement operation currently running. Purchases and restores talk to the same Play
// account state, so they are mutually exclusive by construction: a single slot (instead of the
// former independent verifying/restoring flags) makes "which one is busy" unambiguous for both the
// arbiter in the ViewModel and the spinner placement in the UI.
internal enum class BusyOp {
    IAP,
    SUBSCRIPTION,
    RESTORE,
}

// Pro is active purely via the local grace window (no owned purchase). Stage 1 shows a quiet
// "still active" confirmation; diagnostics + restore CTA appear once the episode has aged.
internal data class GraceHint(
    val showDiagnostics: Boolean,
)

internal data class Ownership(
    val hasIap: Boolean = false,
    val subscription: SubscriptionOwnership? = null,
) {
    val ownsAnything: Boolean
        get() = hasIap || subscription != null
}

internal data class SubscriptionOwnership(
    val isAutoRenewing: Boolean,
)

internal enum class SubscriptionAction {
    TRIAL,
    STANDARD,
    UNAVAILABLE,
}

// Display-only ownership mapping from the (replayed) upgradeInfo. Conservative: if ANY record for
// the sub SKU still claims auto-renew (e.g. a retained purchase event next to fresher query data),
// treat it as renewing — that can only under-offer the one-time purchase, never enable it wrongly;
// the actual purchase gate re-verifies against a fresh SUBS query in the ViewModel.
internal fun UpgradeRepoGplay.Info.toOwnership() = Ownership(
    hasIap = upgrades.any { it.sku == OurSku.Iap.PRO_UPGRADE },
    subscription = upgrades
        .filter { it.sku == OurSku.Sub.PRO_UPGRADE }
        .takeIf { it.isNotEmpty() }
        ?.let { subs -> SubscriptionOwnership(isAutoRenewing = subs.any { it.purchase.isAutoRenewing }) },
)

// Any Pro payment Play is still processing. No per-product flag: the one-time purchase and the
// subscription are alternatives, so a pending payment for either one must lock both — completing
// both would charge the user twice for the same thing.
internal fun UpgradeRepoGplay.Info.toPendingFlag(): Boolean = pendingSkus.isNotEmpty()

internal fun toLoadedState(
    iap: SkuDetails?,
    sub: SkuDetails?,
    ownership: Ownership,
    grace: GraceHint? = null,
    wasPreviouslyPro: Boolean = false,
    hasPendingPurchase: Boolean = false,
    busy: BusyOp? = null,
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
        // Any running entitlement operation (restore, manual or the invisible already-owned
        // recovery, and purchases) pauses the buy actions too — starting a purchase while an
        // entitlement is being reconciled just races Play into ITEM_ALREADY_OWNED. A pending
        // payment locks BOTH offers for the same reason the card explains: Play refuses the
        // re-purchase, and the alternative product would double-charge for the same features.
        subscriptionEnabled = (subOffer != null || subOfferTrial != null) &&
            ownership.subscription == null && busy == null && !hasPendingPurchase,
        subscriptionPrice = subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
        iapEnabled = iapOffer != null && !ownership.hasIap && busy == null && !hasPendingPurchase,
        iapPrice = iapOffer?.formattedPrice,
        ownership = ownership,
        grace = grace,
        wasPreviouslyPro = wasPreviouslyPro,
        hasPendingPurchase = hasPendingPurchase,
        busy = busy,
    )
}
