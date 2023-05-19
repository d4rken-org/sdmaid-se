package eu.darken.sdmse.analyzer.core.storage.categories

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed

data class AppCategory(
    override val storageId: DeviceStorage.Id,
    override val spaceUsed: Long,
    val pkgStats: Map<Installed.InstallId, PkgStat>,
) : ContentCategory {

    override val groups: Collection<ContentGroup>
        get() = pkgStats.values
            .map { setOfNotNull(it.appCode, it.privateData, it.publicData, it.extraData) }
            .flatten()

    data class PkgStat(
        val pkg: Installed,
        val appCode: ContentGroup?,
        val privateData: ContentGroup?,
        val publicData: ContentGroup?,
        val extraData: ContentGroup?,
    ) {

        val id: Installed.InstallId
            get() = pkg.installId

        val label: CaString
            get() = pkg.label ?: pkg.packageName.toCaString()

        val totalSize by lazy {
            var size = 0L
            appCode?.groupSize?.let { size += it }
            privateData?.groupSize?.let { size += it }
            publicData?.groupSize?.let { size += it }
            extraData?.groupSize?.let { size += it }
            size
        }
    }
}