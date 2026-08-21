package eu.darken.sdmse.common.pkgs.features

import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.files.local.LocalPath

interface SourceAvailable : Installed {

    val sourceDir: APath?
        get() = applicationInfo?.sourceDir?.let { LocalPath.build(it) }

    val splitSources: Set<APath>?
        get() = applicationInfo?.splitSourceDirs?.map { LocalPath.build(it) }?.toSet()

    /**
     * The splits with their ids, in the order the framework reports them.
     *
     * `splitNames` and `splitSourceDirs` are index parallel (both are ordered by the lexicographically
     * sorted split names), so pairing them by index is sound. It is not done with [Iterable.zip],
     * because that would silently drop entries if the two ever disagreed in length.
     *
     * Null when the pairing can't be trusted: no splits, no names, differing lengths or a null entry.
     * Callers that need the ids have to treat that as a failure, not as "there are no splits".
     */
    val splitSourcesNamed: List<SplitSource>?
        get() {
            val appInfo = applicationInfo ?: return null
            val dirs = appInfo.splitSourceDirs ?: return null
            val names = appInfo.splitNames ?: return null
            if (names.size != dirs.size) return null
            return dirs.indices.map { index ->
                val name = names[index] ?: return null
                val dir = dirs[index] ?: return null
                SplitSource(id = name, path = LocalPath.build(dir))
            }
        }

    data class SplitSource(
        val id: String,
        val path: APath,
    )
}
