package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.storage.StorageId

data class AppCategory(
    override val storageId: StorageId,
    val setupIncomplete: Boolean = false,
    val pkgStats: Map<Installed.InstallId, PkgStat>,
) : ContentCategory {

    override val spaceUsed: Long
        get() = pkgStats.values.sumOf { it.totalSize }

    override val groups: Collection<ContentGroup>
        get() = pkgStats.values
            .map { setOfNotNull(it.appCode, it.appData, it.appMedia, it.extraData) }
            .flatten()

    data class PkgStat(
        val pkg: Installed,
        val appCode: ContentGroup?,
        val appData: ContentGroup?,
        val appMedia: ContentGroup?,
        val extraData: ContentGroup?,
    ) {

        val id: Installed.InstallId
            get() = pkg.installId

        val label: CaString
            get() = pkg.label ?: pkg.packageName.toCaString()

        val totalSize by lazy {
            var size = 0L
            appCode?.groupSize?.let { size += it }
            appData?.groupSize?.let { size += it }
            appMedia?.groupSize?.let { size += it }
            extraData?.groupSize?.let { size += it }
            size
        }
    }
}