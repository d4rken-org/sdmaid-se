package eu.darken.sdmse.common.storage

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject

@Reusable
class StorageStatsManager2 @Inject constructor(
    private val osStatManager: StorageStatsManager,
) {

    suspend fun queryStatsForPkg(storageId: StorageId, pkg: Installed): StorageStats {
        val appUid = pkg.applicationInfo?.uid ?: throw IllegalStateException("${pkg.id} is missing an UID")
        return osStatManager.queryStatsForUid(storageId.externalId, appUid)
    }

    companion object {
        val TAG: String = logTag("StorageStatsManager2")
    }
}