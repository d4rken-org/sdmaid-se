package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingResult
import eu.darken.sdmse.common.upgrade.core.billing.BillingException

class BillingClientException(val result: BillingResult) : BillingException(result.debugMessage) {

    override fun toString(): String =
        "BillingClientException(code=${result.responseCode}, message=${result.debugMessage})"
}