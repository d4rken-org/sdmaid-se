package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve

suspend fun BaseSieve.Result.toDeletion(): SystemCleanerFilter.Match.Deletion? {
    return if (matches) SystemCleanerFilter.Match.Deletion(item) else null
}

suspend fun Collection<Exclusion.Path>.excludeNestedLookups(
    matches: Collection<SystemCleanerFilter.Match>
): Set<SystemCleanerFilter.Match> {
    var temp = matches.map { it.lookup }.toSet()
    this.forEach { temp = it.excludeNestedLookups(temp) }
    return matches
        .filter { temp.contains(it.lookup) }
        .toSet()
}