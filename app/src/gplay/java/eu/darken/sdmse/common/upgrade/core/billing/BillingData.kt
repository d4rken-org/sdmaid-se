package eu.darken.sdmse.common.upgrade.core.billing

import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.upgrade.core.billing.client.isPurchased
import eu.darken.sdmse.common.upgrade.core.billing.client.isRelevant

/**
 * Play's purchase state, split by what it may be used for. [purchases] is the entitlement carrier
 * and PURCHASED-only by construction, so no consumer can grant Pro (or stamp the grace cache) from
 * a payment Play is still processing; [pendingPurchases] keeps that payment visible to the UI.
 */
data class BillingData(
    val purchases: Collection<Purchase>,
    val pendingPurchases: Collection<Purchase> = emptyList(),
) {

    companion object {
        // The one place raw Play data becomes a BillingData: splitting here (instead of at each
        // consumer) is what keeps "pending never grants Pro" a property of the type. Anything
        // that is neither PURCHASED nor PENDING is dropped — it is not an entitlement and not a
        // payment in progress.
        fun from(raw: Collection<Purchase>): BillingData {
            val relevant = raw.filter { it.isRelevant }
            val (purchased, pending) = relevant.partition { it.isPurchased }
            return BillingData(purchases = purchased, pendingPurchases = pending)
        }
    }
}
