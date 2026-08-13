package eu.darken.sdmse.common.upgrade.core.billing

/**
 * A purchase can't proceed because the account already has a payment Play is still processing.
 *
 * Typed so the UI can answer with the informational pending dialog instead of the already-owned
 * error and its restore tips: restoring cannot help — Play refuses to re-sell a product with a
 * pending payment, and the entitlement arrives on its own once the payment clears.
 */
class PendingPurchaseBillingException(cause: Throwable? = null) :
    BillingException("A purchase with a pending payment already exists.", cause)
