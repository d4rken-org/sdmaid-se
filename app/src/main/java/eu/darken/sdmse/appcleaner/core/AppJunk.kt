package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.user.UserProfile2
import kotlin.reflect.KClass

data class AppJunk(
    val pkg: Installed,
    val userProfile: UserProfile2?,
    val expendables: Map<KClass<out ExpendablesFilter>, Collection<APathLookup<*>>>?,
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
        val knownFiles = expendables?.values?.flatten()?.sumOf { it.size } ?: 0L
        val inaccessible = inaccessibleCache?.cacheBytes ?: 0L
        // TODO If we read public storage caches, do we need to calculate the "internal inaccessible bytes"?
        knownFiles + inaccessible
    }

    fun isEmpty() =
        (expendables.isNullOrEmpty() || expendables.values.all { it.isEmpty() }) && inaccessibleCache == null

    override fun toString(): String =
        "AppJunk(${pkg.packageName}, categories=${expendables?.size}, inaccessible=$inaccessibleCache)"
}
