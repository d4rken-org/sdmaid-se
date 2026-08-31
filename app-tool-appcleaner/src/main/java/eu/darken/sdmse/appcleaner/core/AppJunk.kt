package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.automation.errors.LockedAppCacheException
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPrivateFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.automation.core.errors.DisabledAppException
import eu.darken.sdmse.automation.core.errors.NoSettingsWindowException
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.pkgs.isSystemApp
import eu.darken.sdmse.common.user.UserProfile2

data class AppJunk(
    val pkg: Installed,
    val userProfile: UserProfile2?,
    val expendables: Map<ExpendablesFilterIdentifier, Collection<ExpendablesFilter.Match>>?,
    val inaccessibleCache: InaccessibleCache?,
    val acsError: Exception? = null,
    val isExclusionLimited: Boolean = false,
) {

    val identifier: InstallId
        get() = pkg.installId

    val label: CaString
        get() = pkg.label ?: pkg.packageName.toCaString()

    val isSystemApp: Boolean
        get() = pkg.isSystemApp

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
                ?.get(DefaultCachesPublicFilter::class)
                ?.sumOf { it.expectedGain }
            when {
                publicCacheSize == null -> {
                    // No extra info about public caches
                    totalSize
                }

                publicSize != null -> {
                    // The system has seperate info for pub/priv caches
                    privateSize
                }

                else -> {
                    // Assume system info includes pub caches
                    totalSize - publicCacheSize
                }
            }
        } ?: 0L
        knownFiles + inaccessibleSize
    }

    /**
     * Retrying frees nothing here with the currently available backends: all that remains is the
     * inaccessible cache, and the last clearing attempt failed for a reason that won't go away on
     * its own (no settings page, disabled app, cache locked by the system). Transient failures
     * (timeouts, interference) don't count. A rescan builds fresh junks and resets this.
     */
    val isUnclearable: Boolean
        get() = (expendables.isNullOrEmpty() || expendables.values.all { it.isEmpty() }) &&
            inaccessibleCache != null &&
            (acsError is NoSettingsWindowException || acsError is DisabledAppException || acsError is LockedAppCacheException)

    fun isEmpty() =
        (expendables.isNullOrEmpty() || expendables.values.all { it.isEmpty() }) && inaccessibleCache == null

    override fun toString(): String =
        "AppJunk(${pkg.packageName}, categories=${expendables?.size}, inaccessible=$inaccessibleCache)"
}

/** The content a device-global cache trim can reach, i.e. what an app exclusion can't protect. */
val TRIM_BLAST_RADIUS_FILTERS: Set<ExpendablesFilterIdentifier> = setOf(
    DefaultCachesPublicFilter::class,
    DefaultCachesPrivateFilter::class,
)

/**
 * Narrows this junk to [TRIM_BLAST_RADIUS_FILTERS] plus its inaccessible cache, or `null` if
 * neither is left.
 */
fun AppJunk.limitToTrimBlastRadius(): AppJunk? {
    val narrowed = expendables
        ?.filterKeys { TRIM_BLAST_RADIUS_FILTERS.contains(it) }
        ?.filterValues { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }

    if (narrowed == null && inaccessibleCache == null) return null

    return copy(
        expendables = narrowed,
        isExclusionLimited = true,
    )
}
