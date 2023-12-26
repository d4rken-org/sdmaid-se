package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups

val AppCleaner.Data?.hasData: Boolean
    get() = this?.junks?.isNotEmpty() ?: false


suspend fun Collection<Exclusion.Path>.excludeNestedLookups(
    matches: Collection<ExpendablesFilter.Match>
): Set<ExpendablesFilter.Match> {
    var temp = matches.map { it.lookup }.toSet()
    this.forEach { temp = it.excludeNestedLookups(temp) }
    return matches
        .filter { temp.contains(it.lookup) }
        .toSet()
}