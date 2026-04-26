package eu.darken.sdmse.appcleaner.ui.details

import eu.darken.sdmse.appcleaner.core.AppJunk
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilterIdentifier
import eu.darken.sdmse.appcleaner.core.tasks.AppCleanerProcessingTask
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.pkgs.features.InstallId

/**
 * Spec describing a delete intent originating from the AppJunkDetails screen.
 *
 * The screen emits a spec via the VM's confirm dialog flow; the VM materialises the spec into an
 * [AppCleanerProcessingTask] via [buildAppCleanerTask], which intersects the spec against the
 * live snapshot to drop stale paths/categories.
 */
sealed interface DeleteSpec {
    val installId: InstallId

    data class WholeJunk(
        override val installId: InstallId,
        val appLabel: String,
    ) : DeleteSpec

    data class Inaccessible(
        override val installId: InstallId,
        val appLabel: String,
    ) : DeleteSpec

    data class Category(
        override val installId: InstallId,
        val category: ExpendablesFilterIdentifier,
        val matchCount: Int,
        val appLabel: String,
        val categoryLabel: String,
    ) : DeleteSpec

    data class SingleFile(
        override val installId: InstallId,
        val category: ExpendablesFilterIdentifier,
        val path: APath,
        val displayName: String,
    ) : DeleteSpec

    data class SelectedFiles(
        override val installId: InstallId,
        val paths: Set<APath>,
    ) : DeleteSpec
}

/**
 * Maps a [DeleteSpec] to a concrete [AppCleanerProcessingTask], intersected against the live
 * [junk] snapshot.
 *
 * Returns `null` when the spec degenerates (stale category, all paths gone, missing inaccessible
 * cache). The caller should treat `null` as a no-op.
 *
 * **Critical**: only [DeleteSpec.WholeJunk] and [DeleteSpec.Inaccessible] permit
 * `includeInaccessible = true`. Category / SingleFile / SelectedFiles always pass
 * `includeInaccessible = false` so a file-only delete does NOT also clear the app's
 * inaccessible cache. The default for that param on [AppCleanerProcessingTask] is `true`,
 * which would silently destroy data here.
 */
fun buildAppCleanerTask(spec: DeleteSpec, junk: AppJunk): AppCleanerProcessingTask? = when (spec) {
    is DeleteSpec.WholeJunk -> AppCleanerProcessingTask(
        targetPkgs = setOf(spec.installId),
    )

    is DeleteSpec.Inaccessible -> {
        if (junk.inaccessibleCache == null) {
            null
        } else {
            AppCleanerProcessingTask(
                targetPkgs = setOf(spec.installId),
                includeInaccessible = true,
                onlyInaccessible = true,
            )
        }
    }

    is DeleteSpec.Category -> {
        val livePaths = junk.expendables?.get(spec.category)?.map { it.path }?.toSet().orEmpty()
        if (livePaths.isEmpty()) {
            null
        } else {
            AppCleanerProcessingTask(
                targetPkgs = setOf(spec.installId),
                targetFilters = setOf(spec.category),
                targetContents = null,
                includeInaccessible = false,
            )
        }
    }

    is DeleteSpec.SingleFile -> {
        val match = junk.expendables?.get(spec.category)?.firstOrNull { it.path == spec.path }
        if (match == null) {
            null
        } else {
            AppCleanerProcessingTask(
                targetPkgs = setOf(spec.installId),
                targetFilters = setOf(spec.category),
                targetContents = setOf(spec.path),
                includeInaccessible = false,
            )
        }
    }

    is DeleteSpec.SelectedFiles -> {
        val byCategory = mutableMapOf<ExpendablesFilterIdentifier, MutableSet<APath>>()
        junk.expendables?.forEach { (category, matches) ->
            matches.forEach { match: ExpendablesFilter.Match ->
                if (match.path in spec.paths) {
                    byCategory.getOrPut(category) { mutableSetOf() }.add(match.path)
                }
            }
        }
        val validFilters = byCategory.keys.toSet()
        val validPaths = byCategory.values.flatten().toSet()
        if (validFilters.isEmpty() || validPaths.isEmpty()) {
            null
        } else {
            AppCleanerProcessingTask(
                targetPkgs = setOf(spec.installId),
                targetFilters = validFilters,
                targetContents = validPaths,
                includeInaccessible = false,
            )
        }
    }
}
