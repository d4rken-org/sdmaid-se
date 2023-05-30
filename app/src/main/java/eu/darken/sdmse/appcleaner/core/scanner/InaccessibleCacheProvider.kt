package eu.darken.sdmse.appcleaner.core.scanner

import dagger.Reusable
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.hasApiLevel
import eu.darken.sdmse.common.pkgs.features.Installed
import javax.inject.Inject


@Reusable
class InaccessibleCacheProvider @Inject constructor(
    private val storageStatsProvider: StorageStatsProvider,
) {

    suspend fun determineCache(pkg: Installed): InaccessibleCache? {
        val storageStats = storageStatsProvider.getStats(pkg.installId) ?: return null
        return InaccessibleCache(
            pkg.installId,
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