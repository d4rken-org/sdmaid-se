package eu.darken.sdmse.analyzer.core.storage.types

import eu.darken.sdmse.analyzer.core.content.ContentGroup
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.CaString
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.pkgs.features.Installed

data class AppContent(
    override val storageId: DeviceStorage.Id,
    override val spaceUsed: Long,
    val pkgStats: Collection<PkgStat>,
) : StorageContent {

    data class PkgStat(
        val pkg: Installed,
        val appCode: ContentGroup,
        val privateData: ContentGroup,
        val publicData: ContentGroup,
        val extraData: ContentGroup,
    ) {

        val id: Installed.InstallId
            get() = pkg.installId

        val label: CaString
            get() = pkg.label ?: pkg.packageName.toCaString()

        val totalSize by lazy {
            var size = 0L
            size += appCode.groupSize
            size += privateData.groupSize
            size += publicData.groupSize
            size += extraData.groupSize
            size
        }
    }
}