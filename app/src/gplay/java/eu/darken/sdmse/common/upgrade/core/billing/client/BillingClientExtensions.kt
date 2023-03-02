package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult


internal val BillingResult.isSuccess: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK