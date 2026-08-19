package eu.darken.sdmse.common.adb

import eu.darken.sdmse.common.error.causes

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

/**
 * Did this failure come from a spent connect budget?
 *
 * Walks the cause chain because the failure is wrapped on its way out (AdbServiceClient turns it
 * into an AdbUnavailableException). Depth-bounded on purpose: [causes] follows `cause` until null
 * without tracking identity, so a cyclic chain would spin forever - and both callers sit on paths
 * whose whole job is to stop things from hanging.
 */
fun Throwable.isAdbConnectTimeout(): Boolean = this is AdbConnectTimeoutException ||
        causes.take(MAX_CAUSE_DEPTH).any { it is AdbConnectTimeoutException }

/** Our own chains are 2 deep; this only has to stop a pathological one. */
private const val MAX_CAUSE_DEPTH = 16
