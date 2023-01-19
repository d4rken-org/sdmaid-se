package eu.darken.sdmse.appcleaner.core.scanner

import eu.darken.sdmse.common.pkgs.Pkg

data class InaccessibleCache(
    val pkgId: Pkg.Id,
    val cacheBytes: Long,
    val externalCacheBytes: Long?,
)