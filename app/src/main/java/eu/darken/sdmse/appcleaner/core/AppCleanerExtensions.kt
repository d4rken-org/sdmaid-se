package eu.darken.sdmse.appcleaner.core

import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.exclusion.core.types.Exclusion
import eu.darken.sdmse.exclusion.core.types.excludeNestedLookups
import kotlin.reflect.KClass

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

val KClass<out ExpendablesFilter>.identifier: ExpendablesFilterIdentifier
    get() = this