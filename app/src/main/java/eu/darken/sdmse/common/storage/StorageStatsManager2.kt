package eu.darken.sdmse.common.storage

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.getPkg
import eu.darken.sdmse.common.user.UserHandle2
import javax.inject.Inject

@Reusable
class StorageStatsManager2 @Inject constructor(
    private val osStatManager: StorageStatsManager,
    private val pkgRepo: PkgRepo,
) {

    suspend fun queryStatsForPkg(storageId: StorageId, installId: Installed.InstallId): StorageStats {
        val pkg = pkgRepo.getPkg(installId) ?: throw IllegalArgumentException("Pkg is not installed $installId")
        return queryStatsForPkg(storageId, pkg)
    }

    suspend fun queryStatsForPkg(storageId: StorageId, pkg: Installed): StorageStats {
        return osStatManager.queryStatsForUid(storageId.externalId, pkg.packageInfo.applicationInfo.uid)
    }

    fun queryStatsForUser(storageId: StorageId, user: UserHandle2): Any {
        return osStatManager.queryStatsForUser(storageId.externalId, user.asUserHandle())
    }

    companion object {
        val TAG: String = logTag("StorageStatsManager2")
    }
}