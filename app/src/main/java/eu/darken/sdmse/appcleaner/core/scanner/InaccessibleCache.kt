package eu.darken.sdmse.appcleaner.core.scanner

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserHandle2

data class InaccessibleCache(
    val identifier: Installed.InstallId,
    val isSystemApp: Boolean,
    val itemCount: Int,
    val cacheBytes: Long,
    val externalCacheBytes: Long?,
) {

    val pkgId: Pkg.Id
        get() = identifier.pkgId

    val userHandle: UserHandle2
        get() = identifier.userHandle

    val privateCacheSize: Long = cacheBytes - (externalCacheBytes ?: 0L)

    val totalBytes: Long
        get() = cacheBytes + (externalCacheBytes ?: 0L)

    val isEmpty: Boolean = privateCacheSize == 0L
}