package eu.darken.sdmse.common.upgrade.core.billing

/**
 * Exception thrown when user cancels the billing flow.
 * Does NOT implement HasLocalizedError - should be dismissed silently.
 */
class UserCanceledBillingException(cause: Throwable) :
    BillingException("User canceled billing flow.", cause)
