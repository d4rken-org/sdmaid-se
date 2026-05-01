package eu.darken.sdmse.appcleaner.ui.details.page

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.scanner.InaccessibleCache

internal sealed interface AppJunkElement {
    data object Header : AppJunkElement

    data class Inaccessible(
        val cache: InaccessibleCache,
    ) : AppJunkElement

    data class CategoryHeader(
        val category: ExpendablesFilterIdentifier,
        val matches: List<ExpendablesFilter.Match>,
        val totalSize: Long,
        val isCollapsed: Boolean,
    ) : AppJunkElement

    data class FileRow(
        val category: ExpendablesFilterIdentifier,
        val match: ExpendablesFilter.Match,
    ) : AppJunkElement
}

internal fun buildAppJunkElements(
    junk: AppJunk,
    collapsed: Set<ExpendablesFilterIdentifier>,
): List<AppJunkElement> {
    val out = mutableListOf<AppJunkElement>()
    out.add(AppJunkElement.Header)

    junk.inaccessibleCache?.let { out.add(AppJunkElement.Inaccessible(it)) }

    junk.expendables
        ?.filterValues { it.isNotEmpty() }
        ?.forEach { (category, matches) ->
            val isCollapsed = collapsed.contains(category)
            out.add(
                AppJunkElement.CategoryHeader(
                    category = category,
                    matches = matches.toList(),
                    totalSize = matches.sumOf { it.expectedGain },
                    isCollapsed = isCollapsed,
                )
            )
            if (!isCollapsed) {
                matches
                    .sortedByDescending { it.expectedGain }
                    .forEach { match ->
                        out.add(AppJunkElement.FileRow(category = category, match = match))
                    }
            }
        }

    return out
}
