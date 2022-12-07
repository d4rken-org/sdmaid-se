package eu.darken.sdmse.common.forensics.csi.source

import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner

interface AppSourceCheck {

    suspend fun process(areaInfo: AreaInfo): Result


    data class Result(
        val owners: Set<Owner> = emptySet(),
        val hasKnownUnknownOwner: Boolean = false,
    )
}