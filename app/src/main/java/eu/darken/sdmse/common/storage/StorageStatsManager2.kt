package eu.darken.sdmse.common.storage

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

@Reusable
class StorageStatsManager2 @Inject constructor(
    private val osStatManager: StorageStatsManager,
    private val ipcFunnel: IPCFunnel,
) {

    suspend fun queryStatsForPkg(storageId: StorageId, pkg: Installed): StorageStats = ipcFunnel.use {
        // We don't need the direct IPCFunnel reference, but to lighten the load in the IPC buffer we use it's locking
        val appUid = pkg.applicationInfo?.uid ?: throw IllegalStateException("${pkg.id} is missing an UID")
        osStatManager.queryStatsForUid(storageId.externalId, appUid)
    }

    companion object {
        val TAG: String = logTag("StorageStatsManager2")
    }
}