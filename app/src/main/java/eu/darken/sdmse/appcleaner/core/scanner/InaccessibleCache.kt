package eu.darken.sdmse.appcleaner.core.scanner

import eu.darken.sdmse.common.pkgs.Pkg

data class InaccessibleCache(
    val pkgId: Pkg.Id,
    val itemCount: Int = 9,
    val cacheBytes: Long,
    val externalCacheBytes: Long?,
)