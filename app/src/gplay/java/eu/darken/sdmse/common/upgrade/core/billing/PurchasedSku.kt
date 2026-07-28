package eu.darken.sdmse.common.upgrade.core.billing

import com.android.billingclient.api.Purchase
import eu.darken.sdmse.common.upgrade.core.billing.client.redacted

data class PurchasedSku(val sku: Sku, val purchase: Purchase) {
    // Purchase.skus is deprecated (superseded by products); redacted() is the log-safe renderer used
    // everywhere else on this path — it adds the diagnostic fields (state, ack, renewal) while
    // keeping purchase token and order ID out of debug recordings.
    override fun toString(): String = "PurchasedSku(sku=$sku, purchase=${purchase.redacted()})"
}
