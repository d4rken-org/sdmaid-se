package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.common.forensics.RiskLevel

data class Corpse(
    val ownerInfo: OwnerInfo,
    val content: Collection<APathLookup<*>>,
    val isWriteProtected: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
) {
    val path: APath
        get() = ownerInfo.item
    val areaInfo: AreaInfo
        get() = ownerInfo.areaInfo

    val size: Long
        get() = content.sumOf { it.size }
}