package eu.darken.sdmse.appcleaner.core.scanner

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.UserPkgId
import eu.darken.sdmse.common.user.UserHandle2
import javax.inject.Inject


@Reusable
class InaccessibleCacheProvider @Inject constructor(
    private val storageStatsProvider: StorageStatsProvider,
) {

    suspend fun determineCache(pkgId: Pkg.Id, userHandle: UserHandle2): InaccessibleCache? {
        val storageStats = storageStatsProvider.getStats(pkgId) ?: return null
        return InaccessibleCache(
            UserPkgId(pkgId, userHandle),
            itemCount = 1,
            cacheBytes = storageStats.cacheBytes,
            externalCacheBytes = if (hasApiLevel(31)) {
                @Suppress("NewApi")
                storageStats.externalCacheBytes
            } else null,
        )
    }

    companion object {
        val TAG = logTag("AppCleaner", "Scanner", "Inaccessible")
    }
}