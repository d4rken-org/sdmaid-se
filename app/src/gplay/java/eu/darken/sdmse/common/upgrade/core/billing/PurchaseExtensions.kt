package eu.darken.sdmse.common.upgrade.core.billing

import com.android.billingclient.api.Purchase

val Purchase.isPurchased: Boolean
    get() = purchaseState == Purchase.PurchaseState.PURCHASED && orderId != null