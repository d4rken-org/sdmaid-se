package eu.darken.sdmse.common.adb

/**
 * A step of the Shizuku connect sequence used up its whole time budget without answering.
 *
 * Distinct from a plain [AdbException] because it says something a generic failure doesn't: we waited
 * the full budget and nothing happened. Repeating that attempt right away cannot fail differently, it
 * just spends the budget again, so callers may treat it as terminal instead of retryable.
 */
class AdbConnectTimeoutException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
) : AdbException(message = message, cause = cause)
