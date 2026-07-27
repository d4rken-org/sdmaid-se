package eu.darken.sdmse.common.upgrade

/**
 * Flavor-specific entitlement diagnostics for the debug log header.
 *
 * Deliberately separate from [UpgradeRepo]: the recorder must be able to read this without
 * constructing the billing stack. Resolving [UpgradeRepo] on GPlay would build UpgradeRepoGplay ->
 * BillingManager and start its AppScope collectors and connect loop, so simply enabling a debug
 * recording would change when billing initializes. Implementations must stay inert.
 */
interface UpgradeDiagnostics {

    /** One-line summary for the log header, or null when the flavor has nothing to report. */
    suspend fun debugInfo(): String?
}
