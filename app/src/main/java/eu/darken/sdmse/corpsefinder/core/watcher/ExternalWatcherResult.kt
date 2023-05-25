package eu.darken.sdmse.corpsefinder.core.watcher

import eu.darken.sdmse.common.pkgs.Pkg

interface ExternalWatcherResult {
    val pkgId: Pkg.Id

    data class Scan(
        override val pkgId: Pkg.Id,
        val foundItems: Int,
    ) : ExternalWatcherResult

    data class Deletion(
        override val pkgId: Pkg.Id,
        val deletedItems: Int,
        val freedSpace: Long,
    ) : ExternalWatcherResult
}