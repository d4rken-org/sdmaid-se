package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserProfile2

data class AppJunk(
    val pkg: Installed,
    val userProfile: UserProfile2?,
    val expendables: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>?,
    val inaccessibleCache: InaccessibleCache?,
) {

    val identifier: Installed.InstallId
        get() = pkg.installId

    val label: CaString
        get() = pkg.label ?: pkg.packageName.toCaString()

    val itemCount by lazy {
        var count = 0
        count += expendables?.values?.sumOf { it.size } ?: 0
        inaccessibleCache?.let { count += it.itemCount }
        count
    }

    val size by lazy {
        val knownFiles = expendables?.values?.flatten()?.sumOf { it.expectedGain } ?: 0L
        val inaccessibleSize = inaccessibleCache?.run {
            val publicCacheSize = expendables
                ?.get(DefaultCachesPublicFilter::class.identifier)
                ?.sumOf { it.expectedGain }
            when {
                publicCacheSize == null -> {
                    // No extra info about public caches
                    cacheBytes
                }

                externalCacheBytes != null -> {
                    // The system knows has seperate info for pub/priv caches
                    privateCacheSize
                }

                else -> {
                    // Assume system info includes pub caches
                    cacheBytes - publicCacheSize
                }
            }
        } ?: 0L
        knownFiles + inaccessibleSize
    }

    fun isEmpty() =
        (expendables.isNullOrEmpty() || expendables.values.all { it.isEmpty() }) && inaccessibleCache == null

    override fun toString(): String =
        "AppJunk(${pkg.packageName}, categories=${expendables?.size}, inaccessible=$inaccessibleCache)"
}
