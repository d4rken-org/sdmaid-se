package eu.darken.sdmse.appcleaner.core.scanner

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.os.UserHandle
import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import javax.inject.Inject

@Reusable
class StorageStatsProvider @Inject constructor(
    private val ipcFunnel: IPCFunnel,
    private val storageStatsManager: StorageStatsManager,
) {

    @Suppress("DEPRECATION")
    suspend fun getStats(pkgId: Pkg.Id): StorageStats? {
        return try {
            val ai = ipcFunnel.use { packageManager.getApplicationInfo(pkgId.name, 0) }
            ipcFunnel.use {
                storageStatsManager.queryStatsForPackage(
                    ai.storageUuid,
                    pkgId.name,
                    UserHandle.getUserHandleForUid(ai.uid)
                )
            }
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to query app size for $pkgId: ${e.asLog()}" }
            null
        }
    }

    companion object {
        val TAG = logTag("StorageStats")
    }

}