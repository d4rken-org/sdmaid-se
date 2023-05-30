package eu.darken.sdmse.appcleaner.core.scanner

import android.app.usage.StorageStats
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.storage.StorageStatsManager2
import javax.inject.Inject

@Reusable
class StorageStatsProvider @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val storageStatsManager: StorageStatsManager2,
) {

    @Suppress("DEPRECATION")
    suspend fun getStats(id: Installed.InstallId): StorageStats? {
        return try {
            val ai = ipcFunnel.use { packageManager.getApplicationInfo(id.pkgId.name, 0) }
            ipcFunnel.use {
                storageStatsManager.queryStatsForPkg(
                    StorageId(internalId = null, externalId = ai.storageUuid),
                    id,
                )
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to query app size for $id: ${e.asLog()}" }
            null
        }
    }

    companion object {
        val TAG = logTag("StorageStats")
    }

}