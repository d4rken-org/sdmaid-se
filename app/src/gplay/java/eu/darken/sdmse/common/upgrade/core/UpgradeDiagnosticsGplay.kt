package eu.darken.sdmse.common.upgrade.core

import eu.darken.sdmse.common.upgrade.UpgradeDiagnostics
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports the local billing cache into the debug log header.
 *
 * `lastProStateAt > 0` is the "this install once confirmed a real Pro purchase" bit. It predates
 * the CurriculumVitae pro-state counters by years and lives in a DataStore that was never migrated
 * or renamed, so it survives update chains that the newer counters can't speak to. Without it in
 * the header, a purchase complaint can't be told apart from a never-bought install.
 *
 * Depends on [BillingCache] alone -- see [UpgradeDiagnostics] for why this must not pull in
 * UpgradeRepoGplay.
 */
@Singleton
class UpgradeDiagnosticsGplay @Inject constructor(
    private val billingCache: BillingCache,
) : UpgradeDiagnostics {

    override suspend fun debugInfo(): String {
        val snapshot = billingCache.snapshot()
        val lastProAt = snapshot.lastProStateAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) } ?: "never"
        val lastProSku = snapshot.lastProStateSku.takeIf { it.isNotEmpty() } ?: "unknown/legacy"
        val unconfirmedSince = snapshot.proUnconfirmedSince.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) } ?: "none"
        return "BillingCache(lastProStateAt=$lastProAt, lastProStateSku=$lastProSku, proUnconfirmedSince=$unconfirmedSince)"
    }
}
