package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.pkgs.features.Installed
import kotlin.reflect.KClass

data class AppJunk(
    val pkg: Installed,
    val expendables: Map<KClass<out ExpendablesFilter>, Collection<APathLookup<*>>>?,
    val inaccessibleCache: InaccessibleCache?,
) {
    val size by lazy {
        val knownFiles = expendables?.values?.flatten()?.sumOf { it.size } ?: 0L
        val inaccessible = inaccessibleCache?.cacheBytes ?: 0L
        // TODO If we read public storage caches, do we need to calculate the "internal inaccessible bytes"?
        knownFiles + inaccessible
    }

    fun isEmpty() = expendables.isNullOrEmpty() && inaccessibleCache == null
}
