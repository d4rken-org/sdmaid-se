package eu.darken.sdmse.common.upgrade.core.data

import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.upgrade.core.toPurchasedSku

data class BillingData(
    val purchases: Collection<Purchase>
) {
    val purchasedSkus: Collection<PurchasedSku>
        get() = purchases.map { it.toPurchasedSku() }.flatten()
}