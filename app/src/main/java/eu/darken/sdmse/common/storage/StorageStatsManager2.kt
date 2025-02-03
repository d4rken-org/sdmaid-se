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
    // We don't need the direct IPCFunnel reference, but to lighten the load in the IPC buffer we use it's locking
    private val ipcFunnel: IPCFunnel,
) {

    suspend fun queryStatsForAppUid(storageId: StorageId, pkg: Installed): StorageStats = ipcFunnel.use {
        val appUid = pkg.applicationInfo?.uid ?: throw IllegalStateException("${pkg.id} is missing an UID")
        osStatManager.queryStatsForUid(storageId.externalId, appUid)
    }

    suspend fun queryStatsForPkg(storageId: StorageId, pkg: Installed): StorageStats = ipcFunnel.use {
        osStatManager.queryStatsForPackage(storageId.externalId, pkg.packageName, pkg.userHandle.asUserHandle())
    }

    @Throws(IllegalStateException::class)
    suspend fun getTotalBytes(id: StorageId): Long {
        val value = osStatManager.getTotalBytes(id.externalId)
        if (value == ERRROR_MIN || value == ERROR_MAX) throw IllegalStateException("Total bytes is $value")
        return value
    }

    @Throws(IllegalStateException::class)
    suspend fun getFreeBytes(id: StorageId): Long {
        val value = osStatManager.getFreeBytes(id.externalId)
        if (value == ERRROR_MIN || value == ERROR_MAX) throw IllegalStateException("Total bytes is $value")
        return value
    }

    companion object {
        // using get*Bytes on a 512GB sdcard leads to invalid values being returned
        // See https://github.com/d4rken-org/sdmaid-se/issues/1575
        private const val ERROR_MAX = 1000000000000L
        private const val ERRROR_MIN = 0L
        val TAG: String = logTag("StorageStatsManager2")
    }
}