package eu.darken.sdmse.corpsefinder.core

import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.OwnerInfo
import eu.darken.sdmse.corpsefinder.core.filter.CorpseFilter
import kotlin.reflect.KClass

data class Corpse(
    val filterType: KClass<out CorpseFilter>,
    val ownerInfo: OwnerInfo,
    val lookup: APathLookup<*>,
    val content: Collection<APathLookup<*>>,
    val isWriteProtected: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.NORMAL,
) {
    val identifier: CorpseIdentifier
        get() = lookup.lookedUp

    val areaInfo: AreaInfo
        get() = ownerInfo.areaInfo

    val size: Long
        get() = lookup.size + content.sumOf { it.size }

    override fun toString(): String =
        "Corpse(identifier=$identifier, type=${areaInfo.type}, owners=${ownerInfo.owners}, size=$size)"
}