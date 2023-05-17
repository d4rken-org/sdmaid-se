package eu.darken.sdmse.analyzer.core.content.types

import eu.darken.sdmse.analyzer.core.content.ContentItem
import eu.darken.sdmse.common.pkgs.features.Installed

data class AppContent(
    override val id: String,
    override val spaceUsed: Long,
    val pkgStats: Collection<PkgStat>,
) : StorageContent {

    data class PkgStat(
        val pkg: Installed,
        val appCode: Set<ContentItem>,
        val appData: Set<ContentItem>,
        val appCache: Set<ContentItem>,
        val extra: Set<ContentItem>,
    ) {
        val totalSize by lazy {
            var size = 0L
            size += appCode.sumOf { it.size }
            size += appData.sumOf { it.size }
            size += appCache.sumOf { it.size }
            size += extra.sumOf { it.size }
            size
        }
    }
}