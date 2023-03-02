package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingResult

class BillingException(val result: BillingResult) : Exception() {
    override val message: String
        get() = result.debugMessage

    override fun toString(): String =
        "BillingException(code=${result.responseCode}, message=${result.debugMessage})"
}