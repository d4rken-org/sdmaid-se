package eu.darken.sdmse.common.access

/**
 * Probe-aware status of a privileged access method (root / Shizuku).
 *
 * Distinguishes the user's *decision* from the *probe result* so the UI can be honest:
 * "you opted out" is not the same as "we tried and couldn't get access". Root/Shizuku
 * availability cannot be reliably predicted (a manager app can be installed without working
 * access, and access can be hidden/integrated with no manager), so the only trustworthy
 * signal is [Active] vs [Unavailable] — the outcome of an actual probe.
 */
sealed interface AccessState {
    /** User has not made a choice yet (setting == null). A setup path is still open. */
    data object Undecided : AccessState

    /** User opted in; the access probe is currently running. Transient, treated as non-blocking. */
    data object Checking : AccessState

    /** User opted in and the probe succeeded — access works. */
    data object Active : AccessState

    /** User opted in but the probe failed — access is not available on this device. */
    data object Unavailable : AccessState

    /** User explicitly opted out (setting == false). */
    data object Declined : AccessState
}
