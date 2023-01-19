package eu.darken.sdmse.exclusions.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.isAncestorOf
import kotlinx.coroutines.flow.first

suspend fun ExclusionManager.currentExclusions() = exclusions.first()

suspend fun <T : APath> Exclusion.Path.excludeNested(paths: Collection<T>): Collection<T> {
    if (paths.isEmpty()) return emptySet()

    val excluded = mutableSetOf<T>()

    var temp = paths.filter { path ->
        match(path).also {
            if (it) excluded.add(path)
        }
    }

    temp = temp.filter { path ->
        excluded.none { path.isAncestorOf(it) }
    }

    return temp
}