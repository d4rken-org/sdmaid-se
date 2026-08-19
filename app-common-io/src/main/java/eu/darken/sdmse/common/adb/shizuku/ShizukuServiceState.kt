package eu.darken.sdmse.common.adb.shizuku

/**
 * Why our privileged Shizuku service is (not) usable.
 *
 * Exists because a bare Boolean cannot tell "still nothing yet" apart from "we tried and it failed",
 * which is what left the setup card showing the same waiting message forever on devices where
 * Shizuku's user service never comes up.
 */
sealed interface ShizukuServiceState {

    /** Nothing has probed yet. */
    data object NotChecked : ShizukuServiceState

    /** Our service is up and answering. */
    data object Available : ShizukuServiceState

    /** Shizuku says we do not have permission. */
    data object PermissionDenied : ShizukuServiceState

    /**
     * The grant state could not be read. NOT the same as [PermissionDenied]: it means "cannot know".
     *
     * Deliberately NOT a terminal failure even though ShizukuWrapper.isGranted() also returns null
     * when its own watchdog expires, so a wedged Shizuku server lands here too. The overwhelmingly
     * common cause is simply that Shizuku has not been started yet, and telling that user their
     * setup failed would be wrong. The helper-process defect this all exists for does not come
     * through here: it surfaces as [TimedOut] or [Failed] from the connect attempt itself.
     */
    data object Unknown : ShizukuServiceState

    /**
     * A step of the connect sequence used its whole time budget. Terminal for this attempt: the
     * upstream defect where Shizuku's user service never calls back lands here.
     */
    data object TimedOut : ShizukuServiceState

    /**
     * Any other terminal failure, e.g. the user-service handshake threw. The same upstream defect
     * can surface here rather than as [TimedOut] when the service dies mid-handshake, so this is a
     * real failure to report, not an "unknown yet".
     *
     * Deliberately carries no Throwable: this state is cached in long-lived UI state, the manager
     * already logs the exception, and nothing downstream reads it.
     */
    data object Failed : ShizukuServiceState

    /** Did we probe and get a definitive "no"? Drives whether the UI offers a retry. */
    val isTerminalFailure: Boolean
        get() = this is TimedOut || this is Failed
}
