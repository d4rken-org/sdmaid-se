package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.common.files.core.APath
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import kotlin.reflect.KClass

data class Corpse(
    val filterType: KClass<out CorpseFilter>,
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

    override fun toString(): String = "Corpse(path=$path, type=${areaInfo.type}, owners=${ownerInfo.owners})"
}