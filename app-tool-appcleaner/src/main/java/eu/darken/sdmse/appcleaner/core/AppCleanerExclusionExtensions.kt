package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.PathExclusionIndex
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups

suspend fun Collection<Exclusion.Path>.excludeNestedLookups(
    matches: Collection<ExpendablesFilter.Match>
): Set<ExpendablesFilter.Match> = PathExclusionIndex(this).excludeNestedLookups(matches)

suspend fun PathExclusionIndex.excludeNestedLookups(
    matches: Collection<ExpendablesFilter.Match>
): Set<ExpendablesFilter.Match> {
    val survivors = this.excludeNestedLookups(matches.map { it.lookup })
    return matches
        .filter { survivors.contains(it.lookup) }
        .toSet()
}
