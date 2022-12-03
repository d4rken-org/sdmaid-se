package eu.darken.sdmse.common.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.APath

interface CSIProcessor {

    suspend fun hasJurisdiction(type: DataArea.Type): Boolean

    suspend fun matchLocation(target: APath): AreaInfo?

    suspend fun process(item: APath, areaInfo: AreaInfo): Result

    data class Result(
        val owners: Set<Owner> = emptySet(),
        val hasUnknownOwner: Boolean = false,
    )

}