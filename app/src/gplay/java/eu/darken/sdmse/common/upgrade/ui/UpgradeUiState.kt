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
        val restoreInProgress: Boolean = false,
        val verificationInProgress: Boolean = false,
    ) : GplayUpgradeUiState
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

internal fun toLoadedState(
    iap: SkuDetails?,
    sub: SkuDetails?,
    ownership: Ownership,
    grace: GraceHint? = null,
    wasPreviouslyPro: Boolean = false,
    restoreInProgress: Boolean = false,
    verificationInProgress: Boolean = false,
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
        // A running restore (manual or the invisible already-owned recovery) pauses the buy
        // actions too — starting a purchase while an entitlement is being reconciled just races
        // Play into ITEM_ALREADY_OWNED.
        subscriptionEnabled = (subOffer != null || subOfferTrial != null) &&
            ownership.subscription == null && !restoreInProgress,
        subscriptionPrice = subOffer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice,
        iapEnabled = iapOffer != null && !ownership.hasIap && !restoreInProgress,
        iapPrice = iapOffer?.formattedPrice,
        ownership = ownership,
        grace = grace,
        wasPreviouslyPro = wasPreviouslyPro,
        restoreInProgress = restoreInProgress,
        verificationInProgress = verificationInProgress,
    )
}
