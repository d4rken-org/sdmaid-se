package eu.darken.sdmse.appcleaner.core.scanner

import eu.darken.sdmse.common.pkgs.Pkg

data class InaccessibleCache(
    val pkgId: Pkg.Id,
    val itemCount: Int,
    val cacheBytes: Long,
    val externalCacheBytes: Long?,
) {
    val privateCacheSize: Long = cacheBytes - (externalCacheBytes ?: 0L)

    val isEmpty: Boolean = privateCacheSize == 0L
}