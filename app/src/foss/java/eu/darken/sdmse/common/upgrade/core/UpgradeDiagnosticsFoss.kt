package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS has no store entitlement to reconcile: the upgrade state is a local sponsor record, already
 * covered by the existing header fields. Nothing to add.
 */
@Singleton
class UpgradeDiagnosticsFoss @Inject constructor() : UpgradeDiagnostics {

    override suspend fun debugInfo(): String? = null
}
