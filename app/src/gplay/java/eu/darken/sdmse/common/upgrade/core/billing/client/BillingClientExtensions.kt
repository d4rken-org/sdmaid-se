package eu.darken.sdmse.common.upgrade.core.billing.client

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase


internal val BillingResult.isSuccess: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK

/**
 * Owned right now. The ONLY state that may grant an entitlement, stamp the Pro grace cache or be
 * acknowledged — a PENDING purchase is a payment Play is still processing, and acknowledging one is
 * rejected permanently.
 */
internal val Purchase.isPurchased: Boolean
    get() = purchaseState == Purchase.PurchaseState.PURCHASED

/**
 * Worth carrying in our state at all: owned, or a payment in progress the user should see. Anything
 * else (UNSPECIFIED_STATE) is dropped at ingestion — it is neither.
 */
internal val Purchase.isRelevant: Boolean
    get() = isPurchased || purchaseState == Purchase.PurchaseState.PENDING

/**
 * Log-safe rendering of a [Purchase].
 *
 * `Purchase.toString()` dumps the original response JSON, which carries the purchase token and the
 * order ID. Debug recordings are attached to support emails by users, so anything logged here ends
 * up in an inbox and wherever the user forwarded it. Everything actually useful for diagnosing an
 * entitlement problem is non-identifying, so log only that.
 *
 * Total by construction: this runs inside `log {}` lambdas, which evaluate on the billing path
 * whenever a recording is active. A formatter that can throw there would replace a real billing
 * result (or a real billing exception) with a diagnostics failure, which is strictly worse than a
 * degraded log line.
 */
internal fun Purchase.redacted(): String = runCatching {
    "Purchase(products=$products, state=$purchaseState, acknowledged=$isAcknowledged, " +
        "autoRenewing=$isAutoRenewing, purchaseTime=$purchaseTime)"
}.getOrElse { "Purchase(unreadable: ${it::class.simpleName})" }

internal fun Collection<Purchase>.redacted(): String = runCatching {
    joinToString(prefix = "[", postfix = "]") { it.redacted() }
}.getOrElse { "[unreadable: ${it::class.simpleName}]" }