package eu.darken.sdmse.common.areas.modules

import eu.darken.sdmse.common.areas.DataArea

interface DataAreaModule {

    suspend fun firstPass(): Collection<DataArea>

    suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea>
}