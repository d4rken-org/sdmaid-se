package eu.darken.sdmse.common.forensics.csi

import eu.darken.sdmse.common.forensics.AreaInfo
import eu.darken.sdmse.common.forensics.Owner

interface CSISubProcessor {

    suspend fun process(areaInfo: AreaInfo): Result


    data class Result(
        val owners: Set<Owner> = emptySet(),
        val hasKnownUnknownOwner: Boolean = false,
    )
}