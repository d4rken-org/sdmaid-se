package eu.darken.sdmse.systemcleaner.core.filter

import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusionIndex
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups
import eu.darken.sdmse.systemcleaner.core.sieve.SystemCrawlerSieve

suspend fun SystemCrawlerSieve.Result.toDeletion(): SystemCleanerFilter.Match.Deletion? {
    return if (matches) SystemCleanerFilter.Match.Deletion(item) else null
}

suspend fun Collection<Exclusion.Path>.excludeNestedLookups(
    matches: Collection<SystemCleanerFilter.Match>
): Set<SystemCleanerFilter.Match> = PathExclusionIndex(this).excludeNestedLookups(matches)

suspend fun PathExclusionIndex.excludeNestedLookups(
    matches: Collection<SystemCleanerFilter.Match>
): Set<SystemCleanerFilter.Match> {
    val survivors = this.excludeNestedLookups(matches.map { it.lookup })
    return matches
        .filter { survivors.contains(it.lookup) }
        .toSet()
}
