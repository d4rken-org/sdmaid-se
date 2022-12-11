package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.RiskLevel

data class Corpse(
    val path: APath,
    val areaInfo: AreaInfo,
    val content: Collection<APath>,
    val isWriteProtected: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
)